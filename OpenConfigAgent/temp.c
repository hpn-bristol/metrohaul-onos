


inside circuit_pack_make_connection(void *session, ...)
    Rest_session *sessp, fd;
	fd.address = address;
	fd.port = port;
	fd.wss_name = name;

	sessp = (Rest_session*) malloc(sizeof(Rest_session));
	*sessp = fd;
	*session = (void*)sessp;

inside circuit_pack_make_connection (void *session, ...)
    Rest_session* restSession = (Rest_session*)session;
	RestResponse* restResponse = (RestResponse*)malloc(sizeof(RestResponse));
    wss_change_config(restSession, restResponse, ...);
    
    printf("%s, %ld\n", restResponse->response_text, restResponse->response_code);
	if(restResponse->response_code != 200){
		return EXIT_FAILURE;
	}
	free(restResponse);

	return EXIT_SUCCESS;


inside wss_change_config(Rest_session* restSession, RestResponse* restResponse, ...)
    char* strtosend;
	int result = asprintf(&strtosend, 
			"{\"config\": [{"
					"\"in_port\": \"%s\","
					"\"out_port\": \"%s\","
					"\"freq\": %f,"
			"\"bandwidth\": %f,"
					" \"block\": \"%s\""
					"}],"
				"\"wss_id\": \"%s\"}", 
			wss_in_port, wss_out_port, 
			freq, bandwidth, block,  
			restSession->wss_name); 
	printf("Sending %s\n", strtosend);	
	postRest(strtosend, restSession->address, restSession->port, "wssconfigure", restResponse);


inside main()
    void *temp = NULL;
	void **session = &temp;
    circuit_pack_init(session, ...);
    Rest_session* recovered = (Rest_session*)*session;
	printf("%s, %s, %s\n", recovered->address, recovered->port, recovered->wss_name);

    circuit_pack_make_connection(*session, ...);

    --------------

	


#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <libxml/tree.h>
#include <curl/curl.h>


typedef struct{
	char* address;
	char* port;
	char* session_name;
} RestSession;


struct MemoryStruct {
	char *memory;
	size_t size;
};


typedef struct{
	char* response_text;
	long response_code;	
} RestResponse;


static size_t WriteMemoryCallback(void *contents, size_t size, size_t nmemb, void *userp){
	size_t realsize = size * nmemb;
	struct MemoryStruct *mem = (struct MemoryStruct *)userp;

	char *ptr = realloc(mem->memory, mem->size + realsize + 1);
	if(!ptr) {
	  printf("not enough memory (realloc returned NULL)\n");
	  return 0;
	}

	mem->memory = ptr;
	memcpy(&(mem->memory[mem->size]), contents, realsize);
	mem->size += realsize;
	mem->memory[mem->size] = 0;

	return realsize;
}


void postRest(char* str_to_post, char* address, char* port, char* url, RestResponse* restResponse){
	char* complete_url;
	int result = asprintf(&complete_url, "http://%s:%s/%s", address, port, url);

	CURLcode res;
	struct MemoryStruct chunk;
	chunk.memory = malloc(1);
	chunk.size = 0;	
	
	curl_global_init(CURL_GLOBAL_ALL);
 
	/* get a curl handle */ 
	CURL *curl = curl_easy_init();
	if(curl) {
		/* First set the URL that is about to receive our POST. This URL can
		   just as well be a https:// URL if that is what should receive the
		   data. */ 
		curl_easy_setopt(curl, CURLOPT_URL, complete_url);
		  
		/* send all data to this function  */ 
		curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteMemoryCallback);
	 
		/* we pass our 'chunk' struct to the callback function */ 
		curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *)&chunk);
	 
		/* some servers don't like requests that are made without a user-agent field, so we provide one */ 
		curl_easy_setopt(curl, CURLOPT_USERAGENT, "libcurl-agent/1.0");

		/* Now specify the POST data */
		curl_easy_setopt(curl, CURLOPT_POSTFIELDS, str_to_post);
		curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(str_to_post));

		/* Perform the request, res will get the return code */ 
		res = curl_easy_perform(curl);
		
		/* Check for errors */ 
		if(res != CURLE_OK)
			fprintf(stderr, "curl_easy_perform() failed: %s\n", curl_easy_strerror(res));
		else{
			curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &restResponse->response_code);
			restResponse->response_text = chunk.memory;
		}
		/* always cleanup */ 
		curl_easy_cleanup(curl);
	}
	curl_global_cleanup();
}


void wss_change_config(char* wss_in_port, char* wss_out_port, double freq, 
		double bandwidth, char* block,  Rest_session* restSession,
		RestResponse* restResponse){

	char* strtosend;

	int result = asprintf(&strtosend, 
			"{\"config\": [{"
					"\"in_port\": \"%s\","
					"\"out_port\": \"%s\","
					"\"freq\": %f,"
			"\"bandwidth\": %f,"
					" \"block\": \"%s\""
					"}],"
				"\"wss_id\": \"%s\"}", 
			wss_in_port, wss_out_port, 
			freq, bandwidth, block,  
			restSession->wss_name); 

	printf("Sending %s\n", strtosend);	

	postRest(strtosend, restSession->address, restSession->port, "wssconfigure", restResponse);
	free(strtosend);
}


int circuit_pack_make_connection (void *session, char *circuit_pack,
                                  char *wss_in, char *wss_out,
                                  uint32_t center_freq, uint32_t slot_width,
                                  double output_power, uint16_t *id){
		
	static uint16_t wss_channel = 1;
	
	Rest_session* restSession = (Rest_session*)session;
	RestResponse* restResponse = (RestResponse*)malloc(sizeof(RestResponse));	
	
	double freq = (double)center_freq/1.0e6;
	double bandwidth = (double)slot_width/1.0e6;

	wss_change_config(wss_in, wss_out, freq, bandwidth, "False", 
			restSession, restResponse);   

	printf("%s, %ld\n", restResponse->response_text, restResponse->response_code);

	if(restResponse->response_code != 200){
		return EXIT_FAILURE;
	}

	free(restResponse);

	*id = wss_channel++;

	return EXIT_SUCCESS;
}


void wss_change_config(char* wss_in_port, char* wss_out_port, double freq, 
		double bandwidth, char* block,  Rest_session* restSession,
		RestResponse* restResponse){

	char* strtosend;

	int result = asprintf(&strtosend, 
			"{\"config\": [{"
					"\"in_port\": \"%s\","
					"\"out_port\": \"%s\","
					"\"freq\": %f,"
			"\"bandwidth\": %f,"
					" \"block\": \"%s\""
					"}],"
				"\"wss_id\": \"%s\"}", 
			wss_in_port, wss_out_port, 
			freq, bandwidth, block,  
			restSession->wss_name); 

	printf("Sending %s\n", strtosend);	

	postRest(strtosend, restSession->address, restSession->port, "wssconfigure", restResponse);
	free(strtosend);
}


int circuit_pack_init(char *name, char *type, xmlNodePtr node, void **session){
	xmlNodePtr node2;
	xmlChar *conn_type, *port = NULL, *address = NULL;

	Rest_session *sessp, fd;

	for (node = node->children; node; node = node->next) {
		if (xmlStrEqual (node->name, BAD_CAST "connection")){
			conn_type = xmlGetProp (node, BAD_CAST "type");
			if (!xmlStrEqual (conn_type, BAD_CAST "rest")){
			        xmlFree (conn_type);
		        	return EXIT_FAILURE;
			}
			xmlFree (conn_type);
			for (node2 = node->children; node2; node2 = node2->next){
				if (xmlStrEqual (node2->name, BAD_CAST "port"))
					port = xmlNodeGetContent (node2);
        			else if (xmlStrEqual (node2->name, BAD_CAST "address")){
					address = xmlNodeGetContent (node2);
				}
			}
		}
        }

	fd.address = address;
	fd.port = port;
	fd.wss_name = name;

	sessp = (Rest_session*) malloc(sizeof(Rest_session));
	
	*sessp = fd;

	*session = (void*)sessp;

	return EXIT_SUCCESS;
}

int main (void){
	char *name = "WSS-4";
	char *type = "rest";
	void *temp = NULL;
	void **session = &temp;
	char* filename = "circuit-packs.xml";

	xmlNodePtr node, node2;

	xmlDoc *document = xmlReadFile(filename, NULL, 0);
	node = xmlDocGetRootElement(document);

	/*
	Testing circuit_pack_init 
	*/
	int status = circuit_pack_init(name, type, node, session);
	Rest_session* recovered = (Rest_session*)*session;
	printf("%s, %s, %s\n", recovered->address, recovered->port, recovered->wss_name);

	/*
	testing postrest


	RestResponse* restResponse = (RestResponse*)malloc(sizeof(RestResponse));	
	char* strtotest;
	//asprintf(&strtotest, "{\"%s\" : \"%s\"}", "hi", "there");   
	asprintf(&strtotest, "{\"config\": [{\"out_port\": 2,\"bandwidth\": 0.05,\"freq\": 194.5,\"in_port\": 2}],\"wss_id\": \"%s\"}", recovered->wss_name);   
	
	//postRest(strtotest, recovered->address, recovered->port, "testpost", restResponse);
	postRest(strtotest, recovered->address, recovered->port, "wssconfig", restResponse);

	printf("%s, %ld\n", restResponse->response_text, restResponse->response_code);
	free(restResponse);
	free(strtotest);
	*/

	/*
	testing circuit_pack_make_connection 
	*/

	uint16_t *id = (uint16_t*)malloc(sizeof(uint16_t));
	status = circuit_pack_make_connection(*session, recovered->wss_name, "4", "A", 194100000, 50000, 0.0, id);


	/*
	testing circuit_pack_delete_connection 
	*/

	status = circuit_pack_delete_connection(*session, recovered->wss_name, "4", "A", 194100000, 50000, 0.0, *id);

	/*
	Testing circuit_pack_close
	*/
	free(id);
	status = circuit_pack_close(name, type, "test", *session);

	return 0;
}

--------------