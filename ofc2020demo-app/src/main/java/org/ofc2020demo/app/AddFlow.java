/*
 * Copyright 2019-present Open Networking Foundation
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
package org.ofc2020demo.app;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.VlanId;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.cli.net.ConnectPointCompleter;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Sample Apache Karaf CLI command
 */
@Service
@Command(scope = "onos", name = "demoapp-addflow",
        description = "Adding custom VLAN rule to an OpenConfig device, e.g.: demoapp-addflow --vlan 100 netconf:192.168.46.4:2022/10103 netconf:192.168.46.4:2022/10101 delete")
public class AddFlow extends AbstractShellCommand {

    static final String ADD = "add";
    static final String DELETE = "delete";

    @Argument(index = 0, name = "ingress",
            description = "Ingress Device/Port Description",
            required = true, multiValued = false)
    @Completion(ConnectPointCompleter.class)
    String ingressString = "";

    @Argument(index = 1, name = "egress",
            description = "Egress Device/Port Description",
            required = true, multiValued = false)
    @Completion(ConnectPointCompleter.class)
    String egressString = "";

    @Argument(index = 2, name = "Add or delete action",
            description = "add: add a cross connection, Delete: Delete a cross connection",
            required = true, multiValued = false)
    @Completion(AddFlowCommandCompleter.class)
    String addOrDelete = "";

    @Option(name = "-v", aliases = "--vlan", description = "VLAN ID",
            required = true, multiValued = false)
    private String vlanString = null;


    @Override
    protected void doExecute() {

        ConnectPoint ingress = createConnectPoint(ingressString);
        ConnectPoint egress = createConnectPoint(egressString);

        FlowRuleService flowRuleService = get(FlowRuleService.class);

        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();

        selectorBuilder.matchInPort(ingress.port());
        treatmentBuilder.setOutput(egress.port());

        if (!isNullOrEmpty(vlanString)) {
            selectorBuilder.matchVlanId(VlanId.vlanId(Short.parseShort(vlanString)));
        }

        FlowRule flowRule = DefaultFlowRule.builder()
                .forDevice(ingress.deviceId())
                .withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .withPriority(100)
                .fromApp(appId())
                .makePermanent()
                .build();

        if(addOrDelete.equals(ADD)){
            flowRuleService.applyFlowRules(flowRule);
        }
        else if(addOrDelete.equals(DELETE)){
            flowRuleService.removeFlowRules(flowRule);
        }

    }

    private ConnectPoint createConnectPoint(String devicePortString) {
        String[] splitted = devicePortString.split("/");

        checkArgument(splitted.length == 2,
                "Connect point must be in \"deviceUri/portNumber\" format");

        DeviceId deviceId = DeviceId.deviceId(splitted[0]);
        DeviceService deviceService = get(DeviceService.class);

        List<Port> ports = deviceService.getPorts(deviceId);

        for (Port port : ports) {
            if (splitted[1].equals(port.number().name())) {
                return new ConnectPoint(deviceId, port.number());
            }
        }

        return null;
    }

}



        /*
        DriverService driverService = get(DriverService.class);
        Driver driver = driverService.getDriver(deviceId);
        DriverHandler handler = new DefaultDriverHandler(new DefaultDriverData(driver, deviceId));
        ExtensionSelectorResolver selectorResolver = handler.behaviour(ExtensionSelectorResolver.class);

        ExtensionSelector extensionSelector = selectorResolver.getExtensionSelector(new ExtensionSelectorType(500));
        HashMap<PortNumber, Pair<VlanId, VlanId>> vlanAssignment = new HashMap<>();
        vlanAssignment.put(clientConnectPoint.port(), new Pair<>(VlanId.vlanId(vlanAin), VlanId.vlanId(vlanAout)));

        try {
            extensionSelector.setPropertyValue("vlanPortAssignment", vlanAssignment);
        }
        catch (ExtensionPropertyException e) {
            log.error("Error setting extension property", e);
        }

        selectorBuilder.extension(extensionSelector, deviceId);
        */
