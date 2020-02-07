#ifndef _VOYAGER_DRIVER_H_INCLUDED_
#define _VOYAGER_DRIVER_H_INCLUDED_

#include <stdint.h>

double convertFrequencyStrToDouble(char const* const str_val);
int getIndexFromName(char const* const name, char sep);
int getPortFromId(int lch_id, int side);
int getSideFromId(int lch_id);
int getVoyagerFromId(int lch_id);
char* mapIndexToInterface(int lch_id);
char* mapIndexToTransponder(int lch_id);

void sendCommandVoyager(char* cmd);

char* voyagerSetChannelState(uint32_t lch_id, char const* const val);
char* voyagerSetFrequency(char const* const channel, char const* const val);
char* voyagerSetVlan(uint32_t lch_id, uint32_t lch_assignment_id, uint32_t vlan_id, char const* const operation);

// working in progress
// void voyagerAddVlan(uint32_t lch_id, uint32_t lch_assignment_id, uint32_t vlan_id);
// char* voyagerDelVlan(uint32_t lch_id, uint32_t lch_assignment_id, uint32_t vlan_id);

// // renamed
// void initVoyagerHardwareCommunication();
// void stopVoyagerHardwareCommunication();



// todo:
// void lch_set_admin_state(uint32_t lch, char const* const val);
// void component_set_target_power(char const* const name, char const* const val);
// void component_set_frequency(char const* const name, char const* const val);


// void component_set_oprational_mode(char const* const name, char const* const val);



#endif // _VOYAGER_DRIVER_H_INCLUDED_