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

import com.google.common.collect.Range;
import org.onosproject.net.OchSignal;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.PowerConfig;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.slf4j.LoggerFactory.getLogger;

public class OpenRoadmPowerConfig<T> extends AbstractHandlerBehaviour implements PowerConfig<T> {

    // log
    private final Logger log = getLogger(getClass());

    @Override
    public Optional<Double> getTargetPower(PortNumber port, T component) {
        return Optional.ofNullable(acquireTargetPower(port, component));
    }

    @Override
    public void setTargetPower(PortNumber port, T component, double power) {
        if (component instanceof OchSignal) {
            setConnectionTargetPower(port, (OchSignal) component, power);
        } else {
            setPortTargetPower(port, power);
        }
    }

    @Override
    public Optional<Double> currentPower(PortNumber port, T component) {
        return Optional.ofNullable(acquireCurrentPower(port, component));
    }

    @Override
    public Optional<Range<Double>> getTargetPowerRange(PortNumber portNumber, T component) {

        log.debug("OpenRoadm PowerConfig getTargetPowerRange {}", portNumber);

        List<PortNumber> outputPorts;

        DeviceService deviceService = this.handler().get(DeviceService.class);
        outputPorts = deviceService.getPorts(data().deviceId()).stream()
                .filter(p -> p.annotations().value("logical-connection-point").contains("TX"))
                .map(p -> p.number())
                .collect(Collectors.toList());

        //Output port on the degrees
        //outputPorts.add(PortNumber.portNumber(1));
        //outputPorts.add(PortNumber.portNumber(2));
        //outputPorts.add(PortNumber.portNumber(3));

        if (outputPorts.contains(portNumber)) {
            return Optional.ofNullable(getTxPowerRange(portNumber, component));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Range<Double>> getInputPowerRange(PortNumber portNumber, T component) {

        log.debug("OpenRoadm PowerConfig getInputPowerRange {}", portNumber);

        List<PortNumber> inputPorts;

        DeviceService deviceService = this.handler().get(DeviceService.class);
        inputPorts = deviceService.getPorts(data().deviceId()).stream()
                .filter(p -> p.annotations().value("logical-connection-point").contains("RX"))
                .map(p -> p.number())
                .collect(Collectors.toList());

        //Input port on the degrees
        //inputPorts.add(PortNumber.portNumber(17));
        //inputPorts.add(PortNumber.portNumber(18));
        //inputPorts.add(PortNumber.portNumber(19));

        if (inputPorts.contains(portNumber)) {
            return Optional.ofNullable(getRxPowerRange(portNumber, component));
        }
        return Optional.empty();
    }

    //TODO implement actual get configuration from the device
    //This is used by ROADM application to retrieve attenuation parameter, with T instanceof OchSignal
    private Double acquireTargetPower(PortNumber port, T component) {
        log.info("OpenRoadm PowerConfig get port {} target power...", port);

        return 0.0;
    }

    //TODO implement actual get configuration from the device
    //This is used by ROADM application to retrieve attenuation parameter, with T instanceof OchSignal
    private Double acquireCurrentPower(PortNumber port, T component) {
        log.info("OpenRoadm PowerConfig get port {} current power...", port);

        return 0.0;
    }

    //TODO implement actual get configuration from the device
    //Return PowerRange -60 dBm to 60 dBm
    private Range<Double> getTxPowerRange(PortNumber port, T component) {
        log.debug("Get port {} tx power range...", port);
        return Range.closed(-60.0, 60.0);
    }

    //TODO implement actual get configuration from the device
    //Return PowerRange -60dBm to 60 dBm
    private Range<Double> getRxPowerRange(PortNumber port, T component) {
        log.debug("Get port {} rx power range...", port);
        return Range.closed(-60.0, 60.0);
    }

    //TODO implement configuration on the device
    //Nothing to do
    private void setPortTargetPower(PortNumber port, double power) {
        log.debug("Set port {} target power {}", port, power);
    }

    //TODO implement configuration on the device
    //Nothing to do
    private void setConnectionTargetPower(PortNumber port, OchSignal signal, double power) {
        log.debug("Set connection target power {} ochsignal {} port {}", power, signal, port);
    }
}
