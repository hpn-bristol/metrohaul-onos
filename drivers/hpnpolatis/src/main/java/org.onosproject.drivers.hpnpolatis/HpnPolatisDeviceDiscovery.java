/*
 * Copyright 2018-present Open Networking Foundation
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

 * This work was partially supported by EC H2020 project METRO-HAUL (761727).
 */

package org.onosproject.drivers.hpnpolatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.onlab.packet.ChassisId;
import org.onlab.util.Spectrum;
import org.onosproject.net.*;
import org.onosproject.net.device.DefaultDeviceDescription;
import org.onosproject.net.device.DeviceDescriptionDiscovery;
import org.onosproject.net.device.DeviceDescription;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.protocol.rest.RestSBController;
import org.slf4j.Logger;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import static org.onosproject.net.optical.device.OmsPortHelper.omsPortDescription;
import static org.slf4j.LoggerFactory.getLogger;


public class HpnPolatisDeviceDiscovery extends AbstractHandlerBehaviour
        implements DeviceDescriptionDiscovery {

    public static final MediaType JSON = MediaType.valueOf(MediaType.APPLICATION_JSON);
    private static final Logger log = getLogger(HpnPolatisDeviceDiscovery.class);

    @Override
    public DeviceDescription discoverDeviceDetails() {

        Device.Type type = Device.Type.FIBER_SWITCH;

        String vendor       = "HPN";
        String serialNumber = "1111";
        String hwVersion    = "0.1";
        String swVersion    = "0.1";
        String chassisId    = "1111";

        SparseAnnotations annotations = DefaultAnnotations.builder().build();

        return new DefaultDeviceDescription(did().uri(),
                type, vendor, hwVersion, swVersion, serialNumber,
                new ChassisId(chassisId), true, annotations);

    }

    @Override
    public List<PortDescription> discoverPortDetails() {
        DeviceId deviceId = did();
        List<PortDescription> portDescriptions = null;
        try {
            RestSBController restSBController = handler().get(RestSBController.class);
            InputStream inputStream = restSBController.get(deviceId, "/ports/", MediaType.APPLICATION_JSON_TYPE);
            portDescriptions = parsePorts(new ObjectMapper().readTree(inputStream));
        }
        catch(IOException e){
            log.error("Exception discoverPortDetails() {}", did(), e);
            return ImmutableList.of();
        }

        return portDescriptions;
    }

    private List<PortDescription> parsePorts(JsonNode jsonNode){
        List<PortDescription> portDescriptions = Lists.newArrayList();

        Iterator<JsonNode> inputPortsIter = jsonNode.get("input_ports").iterator();
        Iterator<JsonNode> outputPortsIter = jsonNode.get("output_ports").iterator();

        while(inputPortsIter.hasNext()){
            portDescriptions.add(parsePort(inputPortsIter.next().asInt()));
        }

        while(outputPortsIter.hasNext()){
            portDescriptions.add(parsePort(outputPortsIter.next().asInt()));
        }

        return portDescriptions;
    }

    private PortDescription parsePort(Integer currentPort){
        PortNumber portNumber = PortNumber.portNumber((long)currentPort);
        DefaultAnnotations annotations = DefaultAnnotations.builder()
                .set(AnnotationKeys.PORT_NAME, "port-" + currentPort)
                .build();

                return omsPortDescription(
                        portNumber,
                        true,
                        Spectrum.C_BAND_MIN,
                        Spectrum.C_BAND_MAX,
                        ChannelSpacing.CHL_50GHZ.frequency(),
                        annotations
                );
        //return null;
    }

    /**
     * Get the deviceId for which the methods apply.
     *
     * @return The deviceId as contained in the handler data
     */
    private DeviceId did() {
        return handler().data().deviceId();
    }


}