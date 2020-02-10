#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
// #include <unistd.h>
// #include <sys/types.h>
// #include <sys/stat.h>
// #include <sys/socket.h>
// #include <netinet/in.h>
// #include <arpa/inet.h>
// #include <sys/msg.h>
// #include <netdb.h>

// #include <pthread.h>
// #include <signal.h>

#include "voyager_driver.h"
#include "parameters.h"

#define CLIENT_SIDE 1
#define LINE_SIDE 2
#define POWER_UP 0.0
#define POWER_DOWN -35.0

double convertFrequencyStrToDouble(char const* const str_val) {
    long long_val = 0;
    char temp_str[15];
    double double_val = 0.0;

    strcpy(temp_str, str_val);
    long_val = atol(temp_str);

    // Convert from Hz to THz
    double_val = (double)((double)(long_val)/1000000.0);

    if ((double_val > 191.0) && (double_val < 197.0)) {
        return double_val;
    }
    else {
        return -1.0;
    }
}

int getIndexFromName(char const* const name, char sep) {
    char *position_ptr = strchr(name, sep);
    int position = (position_ptr == NULL ? -1 : position_ptr - name);
    
    if (position == -1) {
        fprintf(stderr, "Voyager Driver - getIndexFromName: received name: %s", name);
        perror("Couldn't find channel index.");
    }

    char index[10];
    int chars = strlen(name)-position;
    strncpy(index, &name[position+1], chars);
    return (int)atoi(index);
}

/****  Mapping rules ****
  Voyager: 1-digit (1- voyager 1, 2- voyager 2)
  Side id: 1-digit (1- Client side, 2- Line side)
  Port id: 2-digits (01 to 12 for Client side, 01 to 04 for Line side)
  Example: 1112 -- Voyager 1, Client side, port 12
  Example: 1203 -- Voyager 1, Line side, port 3
*/
int getPortFromId(int lch_id, int side) {
    int port = (int)((lch_id % 1000) % 100);
    if (side == -1) {
        return -1;
    }
    switch (side) {
        case CLIENT_SIDE: {
            if ((port < 1) || (port > 12)) {
                // fprintf(stderr, "Logical Channel ID (%d) - Voyager does not have a port (%d).\n", lch_id, port);
                return -1;
            }
        }
        break;
        case LINE_SIDE: {
            if ((port < 1) || (port > 4)) {
                // fprintf(stderr, "Logical Channel ID (%d) - Voyager does not have a port (%d).\n", lch_id, port);
                return -1;
            }
        }
        break;
    }
    return port;
}

int getSideFromId(int lch_id) {
    int side = (int)((lch_id % 1000) / 100);

    if ((side < 1) || (side > 2)) {
        // fprintf(stderr, "Logical Channel ID (%d) - Voyager does not have a side (%d).\n", lch_id, side);
        return -1;
    }

    return side;
}

int getVoyagerFromId(int lch_id) {
    return (int)(lch_id / 1000);
}

/* Voyagers interfaces are named swpX (client side) or swpLX (line side) */
char* mapIndexToInterface(int lch_id) {
    int side = getSideFromId(lch_id);
    int port = getPortFromId(lch_id, side);
    char* interface = malloc(6);
    
    if (port == -1) {
        strcpy(interface, "-1");
        return interface;
    }
 
    char aux[6];
    switch(side) {
        case(CLIENT_SIDE): {
            sprintf(aux, "swp%d", port);
            strcpy(interface, aux);
        }
        break;
        case(LINE_SIDE): {
            sprintf(aux, "swpL%d", port);
            strcpy(interface, aux);
        }
        break;
        default: {
            // fprintf(stderr, "The lch_id (%d) does not follows the defined naming rules.\n", lch_id);
            // perror("Bad channel index");
            strcpy(interface, "-1");
        }
        break;
    }
    return interface;
}

// /* Voyagers have 4 transponders, L1 to L4 */
char* mapIndexToTransponder(int lch_id) {
    int side = getSideFromId(lch_id);
    int port = getPortFromId(lch_id, side);
    char* interface = malloc(3);

    if (port == -1) {
        strcpy(interface, "-1");
    }
    else {    
        if (side != LINE_SIDE) {
            // fprintf(stderr, "Logical Channel ID (%d) ", lch_id);
            // perror("does not have an associated transponder.\n");
            strcpy(interface, "-1");
        }
        else {
            char aux[3];
            sprintf(aux, "L%d", port);
            strcpy(interface, aux);
        }
    }
    return interface;
}

//  Provisory to test. TODO: Use curl library.
void sendCommandVoyager(char* cmd) {
    if (strcmp(cmd, "-1") == 0) {
        perror("sendCommandVoyager. ERROR.\n");
    }
    char system_cmd[256];
    sprintf(system_cmd, "curl -X POST -k -u cumulus:CumulusLinux! -H \"Content-Type: application/json\" -d '{\"cmd\": \"%s\"}' https://%s:%d/nclu/v1/rpc &", cmd, VOYAGER_ADDR, VOYAGER_PORT);
    printf("Sending cmd: %s\n", system_cmd);
    system(system_cmd);
}

char* voyagerSetChannelState(uint32_t lch_id, char const* const val) {
    // check if it is line port or client port
    int side = getSideFromId(lch_id);
    char* interface_1 = NULL;
    char* interface_2 = NULL;
    char* cmd = malloc(100*sizeof(char));
    char* cmd_1 = malloc(50*sizeof(char));
    char* cmd_2 = malloc(50*sizeof(char));

    if (side == -1) {
        sprintf(cmd, "-1");
        return cmd;
        // perror("voyagerSetChannelState: Bad index.");
    }
    switch(side) {
        /* Handling Voyager's electric interfaces:
             DISABLE: net add interface IF link down
             ENABLE : net del interface IF link down 
           Yes, 'del' and 'add' are correct, you add a link down status to an interface.
        */
        case CLIENT_SIDE: {
            interface_1 = mapIndexToInterface(lch_id);
            if (strcmp(interface_1,"-1") == 0) {
                sprintf(cmd, "-1");
                break;
            }
            if (strcmp(val,"DISABLE") == 0) {
                sprintf(cmd, "add interface %s link down", interface_1);
            }
            else if (strcmp(val,"ENABLE") == 0) {
                sprintf(cmd, "del interface %s link down", interface_1);
            } else {
                sprintf(cmd, "-1");
                // fprintf(stderr, "Bad Admin-State received (%s).", val);
                // perror("voyagerSetChannelState: Bad value.");
            }

    	    sendCommandVoyager(cmd);
    	    sleep(1);
    	    sendCommandVoyager("commit");
        }
        break;
        /* Handling Voyager's optical interfaces:
             DISABLE: net add interface IF state tx-off
             ENABLE : net del interface IF state ready
           Yes, 'del' and 'add' are correct, you add a link down status to an interface.
        */
        case LINE_SIDE: {
            interface_1 = mapIndexToTransponder(lch_id);
            interface_2 = mapIndexToInterface(lch_id);
            if ( (strcmp(interface_1,"-1") == 0) || (strcmp(interface_2,"-1") == 0) ) {
                sprintf(cmd, "-1");
                break;
            }
            if (strcmp(val,"DISABLE") == 0) {
                sprintf(cmd_2, "add interface %s link down", interface_2);
                sprintf(cmd_1, "add interface %s power %f", interface_1, POWER_DOWN);
            }
            else if (strcmp(val,"ENABLE") == 0) {
                sprintf(cmd_2, "del interface %s link down", interface_2);
                sprintf(cmd_1, "add interface %s power %f", interface_1, POWER_UP);
                // sprintf(cmd, "add interface %s state ready", interface);
            } else {
                fprintf(stderr, "Bad Admin-State received (%s).", val);
                perror("voyagerSetChannelState: Bad value.");
            }

            sendCommandVoyager(cmd_1);
    	    sleep(1);
            sendCommandVoyager(cmd_2);
    	    sleep(1);
    	    sendCommandVoyager("commit");
    	    sprintf(cmd, "%s && %s", cmd_1, cmd_2);
        }
        break;
    }
    
    free(interface_1);
    free(interface_2);
    free(cmd_1);
    free(cmd_2);
    return cmd;
}

char* voyagerSetFrequency(char const* const channel, char const* const val) {
    // channel should contain the channel index on it, e.g., Channel-1101.
    // getIndexFromName finds the index and converts it to int.
    int lch_id = getIndexFromName(channel, '-');
    
    char* interface = NULL;    
    char* cmd = malloc(50*sizeof(char));
    // check if it is line port or client port
    int side = getSideFromId(lch_id);
    if (side != LINE_SIDE) {
        sprintf(cmd, "-1");
        return cmd;
        // perror("voyagerSetFrequency: This Side does not have a frequency.");
    }

    /* Changing frequency of a Voyager's optical interface:
            net add interface _IF_ frequency _FREQ_
    */
    interface = mapIndexToTransponder(lch_id);
    if (strcmp(interface,"-1") == 0) {
        sprintf(cmd, "-1");
        return cmd;
        // perror("voyagerSetFrequency: Bad index.");
    }

    double new_frequency = convertFrequencyStrToDouble(val);
    if (new_frequency == -1.0) {
        sprintf(cmd, "-1");
        return cmd;
        // perror("voyagerSetFrequency: The received frequency is not valid.");
    }

    sprintf(cmd, "add interface %s frequency %f", interface, new_frequency);
    
    sendCommandVoyager(cmd);
    sleep(1);
    sendCommandVoyager("commit");

    free(interface);
    return cmd;
}

char* voyagerSetVlan(uint32_t lch_id, uint32_t lch_assignment_id, uint32_t vlan_id, char const* const operation) {
    char* interface_1 = NULL;
    char* interface_2 = NULL;
    char* cmd = malloc(100*sizeof(char));
    char* cmd_1 = malloc(50*sizeof(char));
    char* cmd_2 = malloc(50*sizeof(char));

    /* Working with Vlans in a Voyager bridge:
        ADD   : net add interface _IF_ bridge vids _VLAN_
        DELETE: net del interface _IF_ bridge vids _VLAN_
    */
    interface_1 = mapIndexToInterface(lch_id);
    if (strcmp(interface_1,"-1") == 0) {
        sprintf(cmd, "-1");
        return cmd;
        // perror("voyagerSetFrequency: Bad index.");
    }

    interface_2 = mapIndexToInterface(lch_assignment_id);
    if (strcmp(interface_2,"-1") == 0) {
        sprintf(cmd, "-1");
        return cmd;
        // perror("voyagerSetFrequency: Bad index.");
    }

    if (strcmp(operation,"ADD") == 0) {
        sprintf(cmd_1, "add interface %s bridge vids %d", interface_1, vlan_id);
        sprintf(cmd_2, "add interface %s bridge vids %d", interface_2, vlan_id);
    }
    else if (strcmp(operation,"DELETE") == 0) {
        sprintf(cmd_1, "del interface %s bridge vids %d", interface_1, vlan_id);
        sprintf(cmd_2, "del interface %s bridge vids %d", interface_2, vlan_id);
    } else {
        // fprintf(stderr, "Bad Admin-State received (%s).", val);
        // perror("voyagerSetChannelState: Bad value.");
        sprintf(cmd, "-1");
        return cmd;
    }

    sendCommandVoyager(cmd_1);
    sleep(1);
    sendCommandVoyager(cmd_2);
    sleep(1);
    sendCommandVoyager("commit");

    free(interface_1);
    free(interface_2);
    
    // cmd is returned for the unit tests purpose.
    sprintf(cmd, "%s && %s", cmd_1, cmd_2);
    free(cmd_1);
    free(cmd_2);
    
    return cmd;
}
