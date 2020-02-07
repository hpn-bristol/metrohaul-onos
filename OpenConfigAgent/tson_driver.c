#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/msg.h>
#include <netdb.h>


#include <pthread.h>
#include <signal.h>

#include "tson_driver.h"
#include "parameters.h"


static pthread_t config_thread_id;

static int msg_queue_id;

struct message_s {
  long int mtype;
  char mtext[256];
};

static const size_t msg_size = sizeof(struct message_s) - sizeof(long int);

#define MESSAGE_TYPE 1
#define IPC_WAIT 0
#define MESSAGE_QUEUE_KEY 1235


static void *config_tson_communication_job(void *vargp) {
    struct hostent *hostnm;    /* server host name information        */
    struct sockaddr_in server; /* server address                      */
    int sock_id;                     /* client socket                       */
    int rc;
    struct message_s mymsg;

    //
    // Get the server address.
    //
    hostnm = gethostbyname(TSON_ADDR);
    if (hostnm == (struct hostent *) 0) {
        fprintf(stderr, "Gethostbyname failed\n");
        pthread_exit(NULL);
    }

    //
    // Put the server information into the server structure.
    // The port must be put into network byte order.
    //
    server.sin_family      = AF_INET;
    server.sin_port        = htons(TSON_PORT);
    server.sin_addr.s_addr = *((unsigned long *)hostnm->h_addr);

    //
    // Get a stream socket.
    //
    if ((sock_id = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("Socket()");
    }

    //
    // Connect to the server.
    //
    while (connect(sock_id, (struct sockaddr *)&server, sizeof(server)) < 0) {
        sleep(5);//sleep 5 seconds and retry
        printf("TSON Driver - Trying to connect...\n");
    }

    printf("Transponder Driver - Connected\n");

    while ( (rc = msgrcv(msg_queue_id, &mymsg, msg_size, MESSAGE_TYPE, IPC_WAIT)) >= 0) {

        // send the message to the device
        if (send(sock_id, mymsg.mtext, strlen(mymsg.mtext), 0) < 0) {
            perror("Send()");
        }
        printf("Transponder Driver - Sent command: %s\n", mymsg.mtext);
    }


    /*
    if (recv(sock_id, buf, sizeof(buf), 0) < 0) {
        tcperror("Recv()");
        exit(6);
    }*/

    // Close the socket.
    close(sock_id);

    printf("Transponder Driver - Disconnected\n");

    return NULL;
}


void intTsonHardwareCommunication() {
  // Set up the message queue
  msg_queue_id = msgget((key_t)MESSAGE_QUEUE_KEY, 0666 | IPC_CREAT);

  if (msg_queue_id == -1) {
    perror("msgget failed with error");
    exit(1);
  }

  // launch thread for configuring the device
  pthread_create(&config_thread_id, NULL, config_tson_communication_job, NULL);

  printf("TSON driver started\n");
}

void stopTsonHardwareCommunication() {
  int status;
  status = pthread_kill( config_thread_id, SIGUSR1);
  if ( status <  0)                                                              
    perror("pthread_kill failed");
}

void terminal_device_tson_set_vlan_in(uint32_t lch, char const* const val) {
  printf("TSON Driver - Index: %d - VLAN in: %s\n", lch,val);

  char system_cmd[256];
  sprintf(system_cmd,"curl -X POST -k -H \"Content-Type: application/json\" -d '{\"ch_index\":%d,\"vlan_in\":%s}' http://127.0.0.1:8080/configuration/fpga/in/vlan",lch,val);
  system(system_cmd);
  printf("Running this:\n\t%s\n",system_cmd);
}

void terminal_device_tson_set_vlan_out(uint32_t lch, char const* const val) {
  printf("TSON Driver - Index: %d - VLAN out: %s\n", lch,val);
  
  char system_cmd[256];
  sprintf(system_cmd,"curl -X POST -k -H \"Content-Type: application/json\" -d '{\"ch_index\":%d,\"vlan_out\":%s}' http://127.0.0.1:8080/configuration/fpga/out/vlan",lch,val);
  system(system_cmd);
  printf("Running this:\n\t%s\n",system_cmd);
}
