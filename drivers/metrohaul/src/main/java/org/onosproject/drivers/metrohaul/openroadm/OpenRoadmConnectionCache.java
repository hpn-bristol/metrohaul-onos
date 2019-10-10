/*
 * Copyright 2017-present Metro-Haul consortium
 * This work was partially supported by EC H2020 project METRO-HAUL (761727).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.driver.metrohaul.openroadm;

import org.onosproject.net.DeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



public class OpenRoadmConnectionCache {

    private static final Logger log = LoggerFactory.getLogger(OpenRoadmConnectionCache.class);

    private static Map<DeviceId, OpenRoadmConnectionCache> instances = new ConcurrentHashMap<>(100);

    private Set<OpenRoadmConnection> connectionSet = new HashSet<>();

    private DeviceId deviceId;

    // Private constructor to force access through getInstance
    private OpenRoadmConnectionCache(DeviceId did) {
        deviceId = did;
    }

    // only one thread accesses the instances *ºper device*
    public static OpenRoadmConnectionCache getInstance(DeviceId deviceId) {
        OpenRoadmConnectionCache cache = instances.get(deviceId);
        if (cache == null) {
            cache = new OpenRoadmConnectionCache(deviceId);
            instances.put(deviceId, cache);
            log.warn("New OpenRoadmConnectionCache created for device {}", deviceId);
        }
        return cache;
    }


    public int size() {
        return connectionSet.size();
    }

    public OpenRoadmConnection get(String name) {
        OpenRoadmConnection conn = connectionSet.stream()
                .filter(c -> c.connectionName.equals(name))
                .findFirst()
                .orElse(null);
        return conn;
    }

    public void add(OpenRoadmConnection conn) {
        connectionSet.add(conn);
    }

    public void remove(OpenRoadmConnection conn) {
        connectionSet.removeIf(c -> conn.connectionName.equals(c.connectionName));
    }

    public void print() {
        log.info("Device {} OpenRoadmConnectionCache size {}", deviceId, connectionSet.size());
        connectionSet.forEach(c -> log.info("Connection name {}", c.connectionName));
    }
}
