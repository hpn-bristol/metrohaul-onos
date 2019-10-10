/*
 * Copyright 2018-present Metro-Haul
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map;
import java.util.Optional;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractBehaviour;
import org.onosproject.net.driver.DriverData;
import org.onosproject.net.optical.OpticalDevice;
import org.onosproject.net.optical.DefaultOpticalDevice;
import org.onosproject.net.optical.device.port.OchPortMapper;
import org.onosproject.net.optical.device.port.OduCltPortMapper;
import org.onosproject.net.optical.OmsPort;
import org.onosproject.net.optical.OchPort;
import org.onosproject.net.optical.device.port.OmsPortMapper;
import org.onosproject.net.optical.device.port.OtuPortMapper;
import org.onosproject.net.optical.device.port.PortMapper;
import org.onosproject.net.utils.ForwardingDevice;
import org.slf4j.Logger;

import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;

/**
 * Implementation of {@link OpticalDevice}.
 * <p>
 * Currently supports
 * <ul>
 *  <li> {@link OmsPort}
 * </ul>
 */
@Beta
public class OpenRoadmOpticalDevice extends DefaultOpticalDevice implements OpticalDevice {

    private static final Logger log = getLogger(OpenRoadmOpticalDevice.class);

    // Default constructor required as a Behaviour.
    public OpenRoadmOpticalDevice() {}

    public <T extends Port> boolean portIs(Port port, Class<T> portClass) {

        // OMS port
        if ((port.type() == Port.Type.OMS) && (portClass == OmsPort.class)) {
            return true;
        }

        // OCh port
        if ((port.type() == Port.Type.OCH) && (portClass == OchPort.class)) {
            return true;
        }
        // Check when to return true. E.g. OMS
        // How to deal with OCh Ports with OpenROADM 2.2 or 3.0
        return false;
    }


    public <T extends Port> Optional<T> portAs(Port port, Class<T> portClass) {
        return Optional.empty();
    }

    public Port port(Port port) {
        return port;
    }
}
