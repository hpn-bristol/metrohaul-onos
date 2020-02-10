#ifndef _TSON_DRIVER_H_
#define _TSON_DRIVER_H_

void intTsonHardwareCommunication();

void stopTsonHardwareCommunication();

void terminal_device_tson_set_vlan_in(uint32_t lch, char const* const val);
void terminal_device_tson_set_vlan_out(uint32_t lch, char const* const val);


void lch_set_admin_state(uint32_t lch, char const* const val);
void component_set_target_power(char const* const name, char const* const val);
void component_set_frequency(char const* const name, char const* const val);
void component_set_operational_mode(char const* const name, char const* const val);

#endif