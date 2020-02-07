#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "voyager_driver.h"

#ifndef TRUE
#define TRUE 1
#endif

#ifndef FALSE
#define FALSE 1
#endif

void test_convertFrequencyStrToDouble(char const* const str_val, double res_expected) {
    double result = convertFrequencyStrToDouble(str_val);
    if ((result == -1.0) && (res_expected == -1.0)) {
        printf("Test: PASS\t %s: error \n", str_val);
    }
    else if (result == res_expected) {
        printf("Test: PASS\t %s: %f\n", str_val, result);
    }
    else {
        printf("Test: FAIL\t %s: %f != %f\n", str_val, result, res_expected);
    }
}

void test_mapIndexToInterface(int id, char* res_expected) {
    char* result = mapIndexToInterface(id);
    if (strcmp(result, "-1") == 0 && strcmp(res_expected, "-1") == 0) {
        printf("Test: PASS\t %d: error \n",id);
    }
    else if (strcmp(result, res_expected) == 0) {
        printf("Test: PASS\t %d: %s\n", id, result);
    } else {
        printf("Test: FAIL\t %d: %s != %s\n", id, result, res_expected);
    }
    free(result);
}

int test_mapIndexToTransponder(int id, char* res_expected) {
    char* result = mapIndexToTransponder(id);
    if (strcmp(result, "-1") == 0 && strcmp(res_expected, "-1") == 0) {
        printf("Test: PASS\t %d: error \n",id);
    }
    else if (strcmp(result, res_expected) == 0) {
        printf("Test: PASS\t %d: %s\n", id, result);
    } else {
        printf("Test: FAIL\t %d: %s != %s\n", id, result, res_expected);
    }
    free(result);
}

void test_voyagerSetChannelState(uint32_t id, char const* const state, char* res_expected) {
    char* result = voyagerSetChannelState(id, state);
    if (strcmp(result, "-1") == 0 && strcmp(res_expected, "-1") == 0) {
        printf("Test: PASS\t %d: error \n",id);
    }
    else if (strcmp(result, res_expected) == 0) {
        printf("Test: PASS\t %d: %s\n", id, result);
    } else {
        printf("Test: FAIL\t %d: %s != %s\n", id, result, res_expected);
    }
    free(result);
}

int main()
{
    printf("** Interface tests **\n");
    test_mapIndexToInterface(1101, "swp1");
    test_mapIndexToInterface(1112, "swp12");
    test_mapIndexToInterface(1201, "swpL1");
    test_mapIndexToInterface(1204, "swpL4");
    test_mapIndexToInterface(1100, "-1");
    test_mapIndexToInterface(1113, "-1");
    test_mapIndexToInterface(1001, "-1");
    test_mapIndexToInterface(1301, "-1");
    test_mapIndexToInterface(1200, "-1");
    test_mapIndexToInterface(1205, "-1");

    printf("\n** Transponder tests **\n");
    test_mapIndexToTransponder(1201, "L1");
    test_mapIndexToTransponder(1204, "L4");
    test_mapIndexToTransponder(1101, "-1");
    test_mapIndexToTransponder(1112, "-1");
    test_mapIndexToTransponder(1100, "-1");
    test_mapIndexToTransponder(1113, "-1");
    test_mapIndexToTransponder(1001, "-1");
    test_mapIndexToTransponder(1301, "-1");
    test_mapIndexToTransponder(1200, "-1");
    test_mapIndexToTransponder(1205, "-1");

    printf("\n** Frequency conversion tests **\n");
    static char const * const freq2 = "193500000";
    test_convertFrequencyStrToDouble(freq2, 193.5);
    static char const * const freq3 = "195450000";
    test_convertFrequencyStrToDouble(freq3, 195.45);
    static char const * const freq4 = "197450000";
    test_convertFrequencyStrToDouble(freq4, -1.0);
    static char const * const freq1 = "190000000";
    test_convertFrequencyStrToDouble(freq1, -1.0);
    static char const * const freq5 = "197000";
    test_convertFrequencyStrToDouble(freq5, -1.0);

    printf("\n** Admin-State tests **\n");
    static char const * const enable = "ENABLE";
    static char const * const disable = "DISABLE";
    test_voyagerSetChannelState(1101, enable, "del interface swp1 link down");
    test_voyagerSetChannelState(1112, disable, "add interface swp12 link down");
    test_voyagerSetChannelState(1201, enable, "add interface L1 state ready");
    test_voyagerSetChannelState(1204, disable, "add interface L4 state tx-off");
    test_voyagerSetChannelState(1100, disable, "-1");
    test_voyagerSetChannelState(1100, enable, "-1");
    test_voyagerSetChannelState(1115, disable, "-1");
    test_voyagerSetChannelState(1115, enable, "-1");
    test_voyagerSetChannelState(1001, disable, "-1");
    test_voyagerSetChannelState(1001, enable, "-1");
    test_voyagerSetChannelState(1301, disable, "-1");
    test_voyagerSetChannelState(1301, enable, "-1");
    test_voyagerSetChannelState(1200, disable, "-1");
    test_voyagerSetChannelState(1200, enable, "-1");
    test_voyagerSetChannelState(1205, disable, "-1");
    test_voyagerSetChannelState(1205, enable, "-1");

    printf("\n** Admin-State tests **\n");
}