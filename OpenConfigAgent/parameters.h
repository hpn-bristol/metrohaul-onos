//Port of monitoring socket
#define CONF_MONITORING 0
#define SERVER_PORT  12346
//parameters of the socket used for the configuration of a transponder
#define TRANSPONDER_ADDR "10.10.10.34"//"10.30.2.24"
#define TRANSPONDER_PORT 16001
//enable the configuration via driver to the transponder [0:disabled, 1:enabled]
#define CONF_TRANSPONDER 0
//confd lib used for the setup of the agents
#define LOCALPATH  ((const unsigned char *)"/home/ubuntu/confd/bin/")
//enable the configuration via driver to the voyagers [0:disabled, 1:enabled]
#define CONF_VOYAGER 1
#define VOYAGER_ADDR "137.222.204.212"
#define VOYAGER_PORT 8080
