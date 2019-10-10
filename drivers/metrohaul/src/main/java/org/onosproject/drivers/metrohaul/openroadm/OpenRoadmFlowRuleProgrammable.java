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

import com.google.common.collect.ImmutableList;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.onlab.util.Frequency;
import org.onlab.util.Spectrum;
import org.onosproject.drivers.utilities.XmlConfigParser;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.netconf.DatastoreId;
import org.onosproject.netconf.NetconfController;
import org.onosproject.netconf.NetconfException;
import org.onosproject.netconf.NetconfSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.onosproject.net.PortNumber.portNumber;



/**
 * Implementation of FlowRuleProgrammable interface for OpenROADM devices
 */
public class OpenRoadmFlowRuleProgrammable extends AbstractHandlerBehaviour implements FlowRuleProgrammable {

    private static final Logger log =
            LoggerFactory.getLogger(OpenRoadmFlowRuleProgrammable.class);

    private DeviceId did() {
        return data().deviceId();
    }

    /**
     * Helper method to log from this class adding DeviceId.
     * <p>
     */
    private void openRoadmLog (String format, Object... arguments) {
        log.info("OPENROADM {}: " + format, did(), arguments);
    }


    /**
     * Get the flow entries that are present on the device, called by FlowRuleDriverProvider.
     * <p>
     * The flow entries must match exactly the FlowRule entries in the ONOS store. If they are not an
     * exact match the device will be requested to remove those flows.
     *
     * @return A collection of Flow Entries
     */
    @Override
    public Collection<FlowEntry> getFlowEntries() {
        return ImmutableList.copyOf(
                fetchConnectionsFromDevice().stream()
                        .map(conn -> buildFlowrule(conn))
                        .filter(Objects::nonNull)
                        .map(fr -> new DefaultFlowEntry(
                                fr, FlowEntry.FlowEntryState.ADDED, 0, 0, 0))
                        .collect(Collectors.toList()));
    }


    /**
     * Apply the flow entries specified in the collection rules.
     *
     * @param rules A collection of Flow Rules to be applied
     * @return The collection of added Flow Entries
     */
    @Override
    public Collection<FlowRule> applyFlowRules(Collection<FlowRule> rules) {
        // Apply the  rules on the device
        Collection<FlowRule> added = rules.stream()
                .map(r -> new OpenRoadmFlowRule(r, getLinePorts()))
                .peek(xc -> openRoadmLog("RULE {}", xc))
                .filter(xc -> editConfigAddConnection(xc))
                .collect(Collectors.toList());

        added.forEach(xc -> log.debug("OpenRoadm build OpenRoamFlowRule selector {} treatment {}",
                xc.selector(), xc.treatment()));

        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(did());
        connectionCache.print();

        return added;
    }


    /**
     * Remove the specified flow rules.
     *
     * @param rules A collection of Flow Rules to be removed
     * @return The collection of removed Flow Entries
     */
    @Override
    public Collection<FlowRule> removeFlowRules(Collection<FlowRule> rules) {

        // Remove the valid rules from the device
        Collection<FlowRule> removed = rules.stream()
                .map(r -> new OpenRoadmFlowRule(r, getLinePorts()))
                .filter(xc -> editConfigDeleteConnection(xc))
                .peek(xc -> log.debug("OpenRoadm removed OpenRoamFlowRule selector {} treatment {}", xc.selector(), xc.treatment()))
                .collect(Collectors.toList());

        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(did());
        connectionCache.print();
        return removed;
    }


    private List<PortNumber> getLinePorts() {
        List<PortNumber> linePorts;

        DeviceService deviceService = this.handler().get(DeviceService.class);
        linePorts = deviceService.getPorts(did()).stream()
                .filter(p -> p.annotations().value("logical-connection-point").contains("DEG"))
                .map(p -> p.number())
                .collect(Collectors.toList());

        linePorts.stream()
                .forEach(p -> log.debug("OpenRoadm NETCONF: detected degree port {}", p));

        return linePorts;
    }


    /**
     * Fetches list of connections from device.
     *
     * @return list of connections as XML hierarchy
     */
    private List<HierarchicalConfiguration> fetchConnectionsFromDevice() {
        NetconfSession session = getNetconfSession();
        if (session == null) {
            log.error("OpenRoadm NETCONF - session not found for {}", did());
            return ImmutableList.of();
        }

        StringBuilder requestBuilder = new StringBuilder();
        requestBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        requestBuilder.append("<roadm-connections>");
        requestBuilder.append("</roadm-connections>");
        requestBuilder.append("</org-openroadm-device>");

        String reply;
        try {
            reply = session.getConfig(DatastoreId.RUNNING, requestBuilder.toString());
            openRoadmLog("fetchConnectionsFromDevice reply {}", reply);
        } catch (NetconfException e) {
            log.error("Failed to retrieve configuration details for device {}",did(), e);
            return ImmutableList.of();
        }

        HierarchicalConfiguration cfg =
                XmlConfigParser.loadXml(new ByteArrayInputStream(reply.getBytes()));

        return cfg.configurationsAt("data.org-openroadm-device.roadm-connections");
    }


    /**
     * Builds a flow rule from a connection hierarchy.
     *
     * @param connection the connection hierarchy
     * @return the flow rule
     */
    private FlowRule buildFlowrule(HierarchicalConfiguration connection) {

        String name = connection.getString("connection-name");
        if (name == null) {
            log.error("OpenRoadm NETCONF connection name not correctly retrieved");
            return null;
        }

        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(did());

        // If the flow entry is not in the cache: return null otherwise publish the flow rule
        OpenRoadmConnection conn = connectionCache.get(name);
        if (conn == null) {
            log.error("OpenRoadm NETCONF name not in connectionSet {} delete editConfig message sent", name);
            editConfigDeleteConnection(name);
            return null;
        } else {
            openRoadmLog("connection retrieved {}", conn.getConnectionName());
        }

        //Build the rule selector and treatment
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(conn.inPortNumber)
                .add(Criteria.matchOchSignalType(OchSignalType.FIXED_GRID))
                .add(Criteria.matchLambda(toOchSignalCenterWidth(conn.srcNmcFrequency, conn.srcNmcWidth)))
                .build();
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .add(Instructions.modL0Lambda(toOchSignalCenterWidth(conn.srcNmcFrequency, conn.srcNmcWidth)))
                .setOutput(conn.outPortNumber)
                .build();

        return DefaultFlowRule.builder()
                .forDevice(data().deviceId())
                .makePermanent()
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(conn.priotrity)
                .withCookie(conn.id.value())
                .build();
    }





    /**
     * Request the device to setup the Connection for the rule

     * @param xc - OpenRoadmFlowRule crossconnect
     *
     * @return true if operation was completed, false otherwise.
     */
    private boolean editConfigAddConnection(OpenRoadmFlowRule xc) {
        if (xc == null) {
            log.error("OpenRoadm driver - logic error, null OpenRoadmFlowRule");
            return false;
        }

        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(did());
        if (connectionCache == null) {
            log.error("OpenRoadm driver - logic error, missing connectionCache");
            return false;
        }

        if (connectionCache.size() > 100) {
            log.error("OpenRoadm driver - 100 connections are already configured on the device");
            return false;
        }

        openRoadmLog("editConfigAddConnection {}", xc);
        switch (xc.type) {
            case EXPRESS_LINK:
                return editConfigExpressLink(xc);
            case ADD_LINK:
                return editConfigAddLink(xc);
            case DROP_LINK:
                return editConfigDropLink(xc);
            default:
                log.error("OpenRoadm driver FlowRule type not found");
                return false;
        }
    }



    private boolean editConfigExpressLink(OpenRoadmFlowRule xc) {
        //Generate the openRoadmConnection name starting from values in the Flow Rule
        DeviceService deviceService = this.handler().get(DeviceService.class);
        Port srcPort = deviceService.getPort(did(), xc.inPort());
        Port dstPort = deviceService.getPort(did(), xc.outPort());
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        String openRoadmConnectionName = "NMC-CTP-"
                + srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz()
                + "-to-NMC-CTP-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        OpenRoadmConnection connection = new OpenRoadmConnection(openRoadmConnectionName, xc, deviceService);

        //--- Creation of the Media Channel interface
        StringBuilder mcStringBuilder = new StringBuilder();
        mcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");

        //Source Interface - RX
        mcStringBuilder.append("<interface>");
        mcStringBuilder.append("<name>" + connection.srcMcName + "</name>");
        mcStringBuilder.append("<description>Media-Channel</description>");
        mcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:mediaChannelTrailTerminationPoint</type>");
        mcStringBuilder.append("<administrative-state>inService</administrative-state>");
        mcStringBuilder.append("<supporting-circuit-pack-name>" + connection.srcMcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        mcStringBuilder.append("<supporting-port>" + connection.srcMcSupportingPort + "</supporting-port>");
        mcStringBuilder.append("<supporting-interface>" + connection.srcMcSupportingInterface + "</supporting-interface>");
        mcStringBuilder.append("<mc-ttp xmlns=\"http://org/openroadm/media-channel-interfaces\">");
        mcStringBuilder.append("<min-freq>" + connection.srcMcMinFrequency.asTHz() + "</min-freq>");
        mcStringBuilder.append("<max-freq>" + connection.srcMcMaxFrequency.asTHz() + "</max-freq>");
        mcStringBuilder.append("</mc-ttp>");
        mcStringBuilder.append("</interface>");

        //Destination Interface - TX
        mcStringBuilder.append("<interface>");
        mcStringBuilder.append("<name>" + connection.dstMcName + "</name>");
        mcStringBuilder.append("<description>Media-Channel</description>");
        mcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:mediaChannelTrailTerminationPoint</type>");
        mcStringBuilder.append("<administrative-state>inService</administrative-state>");
        mcStringBuilder.append("<supporting-circuit-pack-name>" + connection.dstMcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        mcStringBuilder.append("<supporting-port>" + connection.dstMcSupportingPort + "</supporting-port>");
        mcStringBuilder.append("<supporting-interface>" + connection.dstMcSupportingInterface + "</supporting-interface>");
        mcStringBuilder.append("<mc-ttp xmlns=\"http://org/openroadm/media-channel-interfaces\">");
        mcStringBuilder.append("<min-freq>" + connection.dstMcMinFrequency.asTHz() + "</min-freq>");
        mcStringBuilder.append("<max-freq>" + connection.dstMcMaxFrequency.asTHz() + "</max-freq>");
        mcStringBuilder.append("</mc-ttp>");
        mcStringBuilder.append("</interface>");

        mcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(mcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Media Channel Interface");
            log.error("OpenRoadm driver - editConfig generating error {}", mcStringBuilder.toString());
            return false;
        }

        //--- Creation of the Network Media Channel interface
        StringBuilder nmcStringBuilder = new StringBuilder();
        nmcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");

        //Source Interface - RX
        nmcStringBuilder.append("<interface>");
        nmcStringBuilder.append("<name>" + connection.srcNmcName + "</name>");
        nmcStringBuilder.append("<description>Network-Media-Channel</description>");
        nmcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:networkMediaChannelConnectionTerminationPoint</type>");
        nmcStringBuilder.append("<administrative-state>inService</administrative-state>");
        nmcStringBuilder.append("<supporting-circuit-pack-name>" + connection.srcNmcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        nmcStringBuilder.append("<supporting-port>" + connection.srcNmcSupportingPort + "</supporting-port>");
        nmcStringBuilder.append("<supporting-interface>" + connection.srcNmcSupportingInterface + "</supporting-interface>");
        nmcStringBuilder.append("<nmc-ctp xmlns=\"http://org/openroadm/network-media-channel-interfaces\">");
        nmcStringBuilder.append("<frequency>" + connection.srcNmcFrequency.asTHz() + "</frequency>");
        nmcStringBuilder.append("<width>" + connection.srcNmcWidth.asGHz() + "</width>");
        nmcStringBuilder.append("</nmc-ctp>");
        nmcStringBuilder.append("</interface>");

        //Destination Interface - TX
        nmcStringBuilder.append("<interface>");
        nmcStringBuilder.append("<name>" + connection.dstNmcName + "</name>");
        nmcStringBuilder.append("<description>Network-Media-Channel</description>");
        nmcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:networkMediaChannelConnectionTerminationPoint</type>");
        nmcStringBuilder.append("<administrative-state>inService</administrative-state>");
        nmcStringBuilder.append("<supporting-circuit-pack-name>" + connection.dstNmcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        nmcStringBuilder.append("<supporting-port>" + connection.dstNmcSupportingPort + "</supporting-port>");
        nmcStringBuilder.append("<supporting-interface>" + connection.dstNmcSupportingInterface + "</supporting-interface>");
        nmcStringBuilder.append("<nmc-ctp xmlns=\"http://org/openroadm/network-media-channel-interfaces\">");
        nmcStringBuilder.append("<frequency>" + connection.dstNmcFrequency.asTHz() + "</frequency>");
        nmcStringBuilder.append("<width>" + connection.dstNmcWidth.asGHz() + "</width>");
        nmcStringBuilder.append("</nmc-ctp>");
        nmcStringBuilder.append("</interface>");

        nmcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(nmcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Network Media Channel Interface");
            log.error("OpenRoadm driver - editConfig generating error {}", nmcStringBuilder.toString());
            return false;
        }

        //--- Creation of the Connection
        StringBuilder connStringBuilder = new StringBuilder();
        connStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        connStringBuilder.append("<roadm-connections>");
        connStringBuilder.append("<connection-name>" + connection.connectionName + "</connection-name>");
        connStringBuilder.append("<opticalControlMode>off</opticalControlMode>");
        connStringBuilder.append("<target-output-power>0</target-output-power>");
        connStringBuilder.append("<source>");
        connStringBuilder.append("<src-if>" + connection.srcConnInterface + "</src-if>");
        connStringBuilder.append("</source>");
        connStringBuilder.append("<destination>");
        connStringBuilder.append("<dst-if>" + connection.dstConnInterface + "</dst-if>");
        connStringBuilder.append("</destination>");
        connStringBuilder.append("</roadm-connections>");
        connStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(connStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Connection");
            log.error("OpenRoadm driver - editConfig generating error {}", connStringBuilder.toString());
            return false;
        }

        //Add connection to local cache
        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(data().deviceId());
        connectionCache.add(connection);
        openRoadmLog("Connection {} created", connection.connectionName);
        return true;
    }




    private boolean editConfigAddLink(OpenRoadmFlowRule xc) {
        //Generate the openRoadmConnection name starting from values in the Flow Rule
        DeviceService deviceService = this.handler().get(DeviceService.class);
        Port srcPort = deviceService.getPort(did(), xc.inPort());
        Port dstPort = deviceService.getPort(did(), xc.outPort());
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        String openRoadmConnectionName = //"NMC-CTP-" +
                srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz()
                + "-to-NMC-CTP-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        OpenRoadmConnection connection = new OpenRoadmConnection(openRoadmConnectionName, xc, deviceService);

        //--- Creation of the Media Channel interface
        StringBuilder mcStringBuilder = new StringBuilder();
        mcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");

        //Source Interface - RX --- In the AddLink connection the source MC is not required

        //Destination Interface - TX
        mcStringBuilder.append("<interface>");
        mcStringBuilder.append("<name>" + connection.dstMcName + "</name>");
        mcStringBuilder.append("<description>Media-Channel</description>");
        mcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:mediaChannelTrailTerminationPoint</type>");
        mcStringBuilder.append("<administrative-state>inService</administrative-state>");
        mcStringBuilder.append("<supporting-circuit-pack-name>" + connection.dstMcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        mcStringBuilder.append("<supporting-port>" + connection.dstMcSupportingPort + "</supporting-port>");
        mcStringBuilder.append("<supporting-interface>" + connection.dstMcSupportingInterface + "</supporting-interface>");
        mcStringBuilder.append("<mc-ttp xmlns=\"http://org/openroadm/media-channel-interfaces\">");
        mcStringBuilder.append("<min-freq>" + connection.dstMcMinFrequency.asTHz() + "</min-freq>");
        mcStringBuilder.append("<max-freq>" + connection.dstMcMaxFrequency.asTHz() + "</max-freq>");
        mcStringBuilder.append("</mc-ttp>");
        mcStringBuilder.append("</interface>");

        mcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(mcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Media Channel Interface {}", mcStringBuilder);
            return false;
        }

        //--- Creation of the Network Media Channel interface
        StringBuilder nmcStringBuilder = new StringBuilder();
        nmcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");

        //Source Interface - RX
        nmcStringBuilder.append("<interface>");
        nmcStringBuilder.append("<name>" + connection.srcNmcName + "</name>");
        nmcStringBuilder.append("<description>Network-Media-Channel</description>");
        nmcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:networkMediaChannelConnectionTerminationPoint</type>");
        nmcStringBuilder.append("<administrative-state>inService</administrative-state>");
        nmcStringBuilder.append("<supporting-circuit-pack-name>" + connection.srcNmcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        nmcStringBuilder.append("<supporting-port>" + connection.srcNmcSupportingPort + "</supporting-port>");
        //nmcStringBuilder.append("<supporting-interface>" + connection.srcNmcSupportingInterface + "</supporting-interface>");
        nmcStringBuilder.append("<nmc-ctp xmlns=\"http://org/openroadm/network-media-channel-interfaces\">");
        nmcStringBuilder.append("<frequency>" + connection.srcNmcFrequency.asTHz() + "</frequency>");
        nmcStringBuilder.append("<width>" + connection.srcNmcWidth.asGHz() + "</width>");
        nmcStringBuilder.append("</nmc-ctp>");
        nmcStringBuilder.append("</interface>");

        //Destination Interface - TX
        nmcStringBuilder.append("<interface>");
        nmcStringBuilder.append("<name>" + connection.dstNmcName + "</name>");
        nmcStringBuilder.append("<description>Network-Media-Channel</description>");
        nmcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:networkMediaChannelConnectionTerminationPoint</type>");
        nmcStringBuilder.append("<administrative-state>inService</administrative-state>");
        nmcStringBuilder.append("<supporting-circuit-pack-name>" + connection.dstNmcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        nmcStringBuilder.append("<supporting-port>" + connection.dstNmcSupportingPort + "</supporting-port>");
        nmcStringBuilder.append("<supporting-interface>" + connection.dstNmcSupportingInterface + "</supporting-interface>");
        nmcStringBuilder.append("<nmc-ctp xmlns=\"http://org/openroadm/network-media-channel-interfaces\">");
        nmcStringBuilder.append("<frequency>" + connection.dstNmcFrequency.asTHz() + "</frequency>");
        nmcStringBuilder.append("<width>" + connection.dstNmcWidth.asGHz() + "</width>");
        nmcStringBuilder.append("</nmc-ctp>");
        nmcStringBuilder.append("</interface>");

        nmcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(nmcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Network Media Channel Interface {}", nmcStringBuilder);
            return false;
        }

        //--- Creation of the Connection
        StringBuilder connStringBuilder = new StringBuilder();
        connStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        connStringBuilder.append("<roadm-connections>");
        connStringBuilder.append("<connection-name>" + connection.connectionName + "</connection-name>");
        connStringBuilder.append("<opticalControlMode>off</opticalControlMode>");
        connStringBuilder.append("<target-output-power>0</target-output-power>");
        connStringBuilder.append("<source>");
        connStringBuilder.append("<src-if>" + connection.srcConnInterface + "</src-if>");
        connStringBuilder.append("</source>");
        connStringBuilder.append("<destination>");
        connStringBuilder.append("<dst-if>" + connection.dstConnInterface + "</dst-if>");
        connStringBuilder.append("</destination>");
        connStringBuilder.append("</roadm-connections>");
        connStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(connStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Connection {}", connStringBuilder);
            return false;
        }

        //Add connection to local cache
        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(data().deviceId());
        connectionCache.add(connection);

        openRoadmLog("Connection {} created", connection.connectionName);
        return true;

    }

    private boolean editConfigDropLink(OpenRoadmFlowRule xc) {
        //Generate the openRoadmConnection name starting from values in the Flow Rule
        DeviceService deviceService = this.handler().get(DeviceService.class);
        Port srcPort = deviceService.getPort(data().deviceId(),xc.inPort());
        Port dstPort = deviceService.getPort(data().deviceId(),xc.outPort());
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        String openRoadmConnectionName = "NMC-CTP-"
                + srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz()
                //+ "-to-NMC-CTP-"
                + "-to-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        OpenRoadmConnection connection = new OpenRoadmConnection(openRoadmConnectionName, xc, deviceService);

        //--- Creation of the Media Channel interface
        StringBuilder mcStringBuilder = new StringBuilder();
        mcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");

        //Source Interface - RX
        mcStringBuilder.append("<interface>");
        mcStringBuilder.append("<name>" + connection.srcMcName + "</name>");
        mcStringBuilder.append("<description>Media-Channel</description>");
        mcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:mediaChannelTrailTerminationPoint</type>");
        mcStringBuilder.append("<administrative-state>inService</administrative-state>");
        mcStringBuilder.append("<supporting-circuit-pack-name>" + connection.srcMcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        mcStringBuilder.append("<supporting-port>" + connection.srcMcSupportingPort + "</supporting-port>");
        mcStringBuilder.append("<supporting-interface>" + connection.srcMcSupportingInterface + "</supporting-interface>");
        mcStringBuilder.append("<mc-ttp xmlns=\"http://org/openroadm/media-channel-interfaces\">");
        mcStringBuilder.append("<min-freq>" + connection.srcMcMinFrequency.asTHz() + "</min-freq>");
        mcStringBuilder.append("<max-freq>" + connection.srcMcMaxFrequency.asTHz() + "</max-freq>");
        mcStringBuilder.append("</mc-ttp>");
        mcStringBuilder.append("</interface>");

        //Destination Interface - TX -- In the DropLink connection the destination MC is not required

        mcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(mcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Media Channel Interface {}", mcStringBuilder);
            return false;
        }

        //--- Creation of the Network Media Channel interface
        StringBuilder nmcStringBuilder = new StringBuilder();
        nmcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");

        //Source Interface - RX
        nmcStringBuilder.append("<interface>");
        nmcStringBuilder.append("<name>" + connection.srcNmcName + "</name>");
        nmcStringBuilder.append("<description>Network-Media-Channel</description>");
        nmcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:networkMediaChannelConnectionTerminationPoint</type>");
        nmcStringBuilder.append("<administrative-state>inService</administrative-state>");
        nmcStringBuilder.append("<supporting-circuit-pack-name>" + connection.srcNmcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        nmcStringBuilder.append("<supporting-port>" + connection.srcNmcSupportingPort + "</supporting-port>");
        nmcStringBuilder.append("<supporting-interface>" + connection.srcNmcSupportingInterface + "</supporting-interface>");
        nmcStringBuilder.append("<nmc-ctp xmlns=\"http://org/openroadm/network-media-channel-interfaces\">");
        nmcStringBuilder.append("<frequency>" + connection.srcNmcFrequency.asTHz() + "</frequency>");
        nmcStringBuilder.append("<width>" + connection.srcNmcWidth.asGHz() + "</width>");
        nmcStringBuilder.append("</nmc-ctp>");
        nmcStringBuilder.append("</interface>");

        //Destination Interface - TX
        nmcStringBuilder.append("<interface>");
        nmcStringBuilder.append("<name>" + connection.dstNmcName + "</name>");
        nmcStringBuilder.append("<description>Network-Media-Channel</description>");
        nmcStringBuilder.append("<type xmlns:openROADM-if=\"http://org/openroadm/interfaces\">openROADM-if:networkMediaChannelConnectionTerminationPoint</type>");
        nmcStringBuilder.append("<administrative-state>inService</administrative-state>");
        nmcStringBuilder.append("<supporting-circuit-pack-name>" + connection.dstNmcSupportingCircuitPack + "</supporting-circuit-pack-name>");
        nmcStringBuilder.append("<supporting-port>" + connection.dstNmcSupportingPort + "</supporting-port>");
        //nmcStringBuilder.append("<supporting-interface>" + connection.dstNmcSupportingInterface + "</supporting-interface>");
        nmcStringBuilder.append("<nmc-ctp xmlns=\"http://org/openroadm/network-media-channel-interfaces\">");
        nmcStringBuilder.append("<frequency>" + connection.dstNmcFrequency.asTHz() + "</frequency>");
        nmcStringBuilder.append("<width>" + connection.dstNmcWidth.asGHz() + "</width>");
        nmcStringBuilder.append("</nmc-ctp>");
        nmcStringBuilder.append("</interface>");

        nmcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(nmcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Network Media Channel Interface {}", nmcStringBuilder);
            return false;
        }

        //--- Creation of the Connection
        StringBuilder connStringBuilder = new StringBuilder();
        connStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        connStringBuilder.append("<roadm-connections>");
        connStringBuilder.append("<connection-name>" + connection.connectionName + "</connection-name>");
        connStringBuilder.append("<opticalControlMode>off</opticalControlMode>");
        connStringBuilder.append("<target-output-power>0</target-output-power>");
        connStringBuilder.append("<source>");
        connStringBuilder.append("<src-if>" + connection.srcConnInterface + "</src-if>");
        connStringBuilder.append("</source>");
        connStringBuilder.append("<destination>");
        connStringBuilder.append("<dst-if>" + connection.dstConnInterface + "</dst-if>");
        connStringBuilder.append("</destination>");
        connStringBuilder.append("</roadm-connections>");
        connStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(connStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to create Connection {}", connStringBuilder);
            return false;
        }

        //Add connection to local cache
        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(data().deviceId());
        connectionCache.add(connection);
        openRoadmLog("Connection {} created", connection.connectionName);
        return true;
    }





    /**
     * Entry point to remove a Crossconnect.
     * 
     * @param xc - OpenROADM flow rule (cross-connect data)
     */
    private boolean editConfigDeleteConnection(OpenRoadmFlowRule xc) {

        openRoadmLog("editConfigDeleteConnection {}", xc);
        switch (xc.type) {
            case EXPRESS_LINK:
                return editConfigDeleteExpressLink(xc);
            case ADD_LINK:
                return editConfigDeleteAddLink(xc);
            case DROP_LINK:
                return editConfigDeleteDropLink(xc);
            default:
                log.error("OpenRoadm driver FlowRule type not found");
                return false;
        }
    }



    private boolean editConfigDeleteExpressLink(OpenRoadmFlowRule xc) {
        //Generate the openRoadmConnection name starting from values in the Flow Rule
        DeviceService deviceService = this.handler().get(DeviceService.class);
        Port srcPort = deviceService.getPort(data().deviceId(),xc.inPort());
        Port dstPort = deviceService.getPort(data().deviceId(),xc.outPort());
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        String openRoadmConnectionName = "NMC-CTP-"
                + srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz()
                + "-to-NMC-CTP-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        //Retrieve the connection in the local cache
        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(data().deviceId());
        OpenRoadmConnection retrievedConn = connectionCache.get(openRoadmConnectionName);

        if (retrievedConn == null) {
            log.error("OpenRoadm editConfigDeleteConnection, connection not found on the local cache");
        }

        openRoadmLog("DELETE EXPRESS {}", openRoadmConnectionName);

        //--- Delete the Connection
        StringBuilder connStringBuilder = new StringBuilder();
        connStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        connStringBuilder.append("<roadm-connections nc:operation=\"delete\">");
        connStringBuilder.append("<connection-name>" + retrievedConn.connectionName + "</connection-name>");
        connStringBuilder.append("</roadm-connections>");
        connStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(connStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete Connection");
            return false;
        }

        //--- Delete Network Media Channel interface
        openRoadmLog("DELETE NMC interfaces '{}' '{}'", retrievedConn.srcNmcName, retrievedConn.dstNmcName);
        StringBuilder nmcStringBuilder = new StringBuilder();
        nmcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        nmcStringBuilder.append("<interface nc:operation=\"delete\">");
        nmcStringBuilder.append("<name>" + retrievedConn.srcNmcName + "</name>");
        nmcStringBuilder.append("</interface>");
        nmcStringBuilder.append("<interface nc:operation=\"delete\">");
        nmcStringBuilder.append("<name>" + retrievedConn.dstNmcName + "</name>");
        nmcStringBuilder.append("</interface>");
        nmcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(nmcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete NMC interface");
            return false;
        }

        //--- Delete Media Channel interface
        openRoadmLog("DELETE MC interfaces '{}' '{}'", retrievedConn.srcMcName, retrievedConn.dstMcName);
        StringBuilder mcStringBuilder = new StringBuilder();
        mcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        mcStringBuilder.append("<interface nc:operation=\"delete\">");
        mcStringBuilder.append("<name>" + retrievedConn.srcMcName + "</name>");
        mcStringBuilder.append("</interface>");
        mcStringBuilder.append("<interface nc:operation=\"delete\">");
        mcStringBuilder.append("<name>" + retrievedConn.dstMcName + "</name>");
        mcStringBuilder.append("</interface>");
        mcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(mcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete NMC interface");
            return false;
        }

        connectionCache.remove(retrievedConn);
        return true;
    }



    private boolean editConfigDeleteAddLink(OpenRoadmFlowRule xc) {
        //Generate the openRoadmConnection name starting from values in the Flow Rule
        DeviceService deviceService = this.handler().get(DeviceService.class);
        Port srcPort = deviceService.getPort(data().deviceId(),xc.inPort());
        Port dstPort = deviceService.getPort(data().deviceId(),xc.outPort());
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        String openRoadmConnectionName = //"NMC-CTP-" +
                srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz()
                + "-to-NMC-CTP-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        //Retrieve the connection in the local cache
        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(did());
        openRoadmLog("DELETE ADD; ConnectionCache: {}", connectionCache.size());
        OpenRoadmConnection retrievedConn = connectionCache.get(openRoadmConnectionName);
        if (retrievedConn == null) {
            log.error("OpenRoadm editConfigDeleteConnection, connection not found on the local cache");
            return false;
        }

        //--- Delete the Connection
        openRoadmLog("DELETE ADD {}", openRoadmConnectionName);
        StringBuilder connStringBuilder = new StringBuilder();
        connStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        connStringBuilder.append("<roadm-connections nc:operation=\"delete\">");
        connStringBuilder.append("<connection-name>" + retrievedConn.connectionName + "</connection-name>");
        connStringBuilder.append("</roadm-connections>");
        connStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(connStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete Connection {}", retrievedConn.connectionName);
            return false;
        }

        // RCAS: At this point, we have to remove the connection from the cache. Otherwise, if we fail 
        // to remove the interfaces, the cache will be left in an inconsistent state
        connectionCache.remove(retrievedConn);

        //--- Delete Network Media Channel interface
        openRoadmLog("DELETE NMC interfaces '{}' '{}'", retrievedConn.srcNmcName, retrievedConn.dstNmcName);
        StringBuilder nmcStringBuilder = new StringBuilder();
        nmcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        nmcStringBuilder.append("<interface nc:operation=\"delete\">");
        nmcStringBuilder.append("<name>" + retrievedConn.srcNmcName + "</name>");
        nmcStringBuilder.append("</interface>");
        nmcStringBuilder.append("<interface nc:operation=\"delete\">");
        nmcStringBuilder.append("<name>" + retrievedConn.dstNmcName + "</name>");
        nmcStringBuilder.append("</interface>");
        nmcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(nmcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete NMC interface");
            return false;
        }

        //--- Delete Media Channel interface -- In AddLink there is not a src Media Channel
        openRoadmLog("DELETE MC interfaces '{}' '{}'", retrievedConn.srcMcName, retrievedConn.dstMcName);
        StringBuilder mcStringBuilder = new StringBuilder();
        mcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        //mcStringBuilder.append("<interface nc:operation=\"delete\">");
        //mcStringBuilder.append("<name>" + retrievedConn.srcMcName + "</name>");
        //mcStringBuilder.append("</interface>");
        mcStringBuilder.append("<interface nc:operation=\"delete\">");
        mcStringBuilder.append("<name>" + retrievedConn.dstMcName + "</name>");
        mcStringBuilder.append("</interface>");
        mcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(mcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete NMC interface");
            return false;
        }

        return true;
    }





    /**
     * @brief MEthid to delete a Drop Cross-connection.
     *
     * @param xc The OpenRoadm flow rule (cross-connect data)
     *
     * @return true if it was deleted correctly, false otherwise.
     */
    private boolean editConfigDeleteDropLink(OpenRoadmFlowRule xc) {

        DeviceId did = data().deviceId();

        //Generate the openRoadmConnection name starting from values in the Flow Rule
        DeviceService deviceService = this.handler().get(DeviceService.class);
        Port srcPort = deviceService.getPort(did,xc.inPort());
        Port dstPort = deviceService.getPort(did,xc.outPort());
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        String openRoadmConnectionName = "NMC-CTP-"
                + srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz()
                //+ "-to-NMC-CTP-"
                + "-to-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        //Retrieve the connection in the local cache
        OpenRoadmConnectionCache connectionCache = OpenRoadmConnectionCache.getInstance(data().deviceId());
        if (connectionCache == null) {
            log.error("OpenRoadm editConfigDeleteConnection, cache is null {}",
                openRoadmConnectionName);
            return false;
        }

        openRoadmLog("DELETE DROP; ConnectionCache: {}", connectionCache.size());
        OpenRoadmConnection retrievedConn = connectionCache.get(openRoadmConnectionName);
        if (retrievedConn == null) {
            log.error("OpenRoadm editConfigDeleteConnection, connection not found on the local cache: {}",
                openRoadmConnectionName);
            return false;
        }

        //--- Delete the Connection
        openRoadmLog("DELETE DROP {}", openRoadmConnectionName);
        StringBuilder connStringBuilder = new StringBuilder();
        connStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        connStringBuilder.append("<roadm-connections nc:operation=\"delete\">");
        connStringBuilder.append("<connection-name>" + retrievedConn.connectionName + "</connection-name>");
        connStringBuilder.append("</roadm-connections>");
        connStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(connStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete Connection");
            return false;
        }

        // RCAS: At this point, we have to remove the connection from the cache. Otherwise, if we fail 
        // to remove the interfaces, the cache will be left in an inconsistent state
        connectionCache.remove(retrievedConn);

        //--- Delete Network Media Channel interface
        openRoadmLog("DELETE NMC interfaces '{}' '{}'", retrievedConn.srcNmcName, retrievedConn.dstNmcName);
        StringBuilder nmcStringBuilder = new StringBuilder();
        nmcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        nmcStringBuilder.append("<interface nc:operation=\"delete\">");
        nmcStringBuilder.append("<name>" + retrievedConn.srcNmcName + "</name>");
        nmcStringBuilder.append("</interface>");
        nmcStringBuilder.append("<interface nc:operation=\"delete\">");
        nmcStringBuilder.append("<name>" + retrievedConn.dstNmcName + "</name>");
        nmcStringBuilder.append("</interface>");
        nmcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(nmcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete NMC interface(s) {} and {}",
                retrievedConn.srcMcName, retrievedConn.dstMcName);
            return false;
        }

        //--- Delete Media Channel interface
        openRoadmLog("DELETE MC interfaces '{}' '{}'", retrievedConn.srcMcName, retrievedConn.dstMcName);
        StringBuilder mcStringBuilder = new StringBuilder();
        mcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        mcStringBuilder.append("<interface nc:operation=\"delete\">");
        mcStringBuilder.append("<name>" + retrievedConn.srcMcName + "</name>");
        mcStringBuilder.append("</interface>");
        //mcStringBuilder.append("<interface nc:operation=\"delete\">");
        //mcStringBuilder.append("<name>" + retrievedConn.dstMcName + "</name>");
        //mcStringBuilder.append("</interface>");
        mcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(mcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete NMC interface");
            return false;
        }
        return true;
    }



    private boolean editConfigDeleteConnection(String connectionName) {

        //-- From the connection name build up MC and NMC names
        String nmcRxName;
        String nmcTxName;
        String mcRxName;
        String mcTxName;

        //NMC-CTP-DEG1-TTP-RX-192.7-to-NMC-CTP-DEG3-TTP-TX-192.7
        String nmcNames[] = connectionName.split("-to-");
        nmcRxName = nmcNames[0];
        nmcTxName = nmcNames[1];

        mcRxName = "MC-TTP" + nmcRxName.substring(7);
        mcTxName = "MC-TTP" + nmcTxName.substring(7);

        log.info("connection {}, NMC RX {}, NMC TX {}, MC RX {}, MC TX {}", connectionName, nmcRxName, nmcTxName, mcRxName, mcTxName);

        //--- Delete the Connection
        StringBuilder connStringBuilder = new StringBuilder();
        connStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        connStringBuilder.append("<roadm-connections nc:operation=\"delete\">");
        connStringBuilder.append("<connection-name>" + connectionName + "</connection-name>");
        connStringBuilder.append("</roadm-connections>");
        connStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(connStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete Connection");
            return false;
        }

        //--- Delete Network Media Channel interface
        StringBuilder nmcStringBuilder = new StringBuilder();
        nmcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        nmcStringBuilder.append("<interface nc:operation=\"delete\">");
        nmcStringBuilder.append("<name>" + nmcRxName + "</name>");
        nmcStringBuilder.append("</interface>");
        nmcStringBuilder.append("<interface nc:operation=\"delete\">");
        nmcStringBuilder.append("<name>" + nmcTxName + "</name>");
        nmcStringBuilder.append("</interface>");
        nmcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(nmcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete NMC interface");
            return false;
        }

        //--- Delete Media Channel interface
        StringBuilder mcStringBuilder = new StringBuilder();
        mcStringBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        if (!nmcRxName.contains("SRG")) { //This is not a AddLink so MC TX is present
            mcStringBuilder.append("<interface nc:operation=\"delete\">");
            mcStringBuilder.append("<name>" + mcRxName + "</name>");
            mcStringBuilder.append("</interface>");
        }
        if (!nmcTxName.contains("SRG")) { //This is not a DropLink so MC TX is present
            mcStringBuilder.append("<interface nc:operation=\"delete\">");
            mcStringBuilder.append("<name>" + mcTxName + "</name>");
            mcStringBuilder.append("</interface>");
        }
        mcStringBuilder.append("</org-openroadm-device>");

        if (!editCrossConnect(mcStringBuilder.toString())) {
            log.error("OpenRoadm driver - failed to delete NMC interface");
            return false;
        }

        return true;
    }



    private boolean editCrossConnect(String xcString) {
        NetconfSession session = getNetconfSession();
        if (session == null) {
            log.error("NETCONF - session not found for device {}", handler().data().deviceId());
            return false;
        }

        try {
            return session.editConfig(DatastoreId.RUNNING, null, xcString);
        } catch (NetconfException e) {
            log.error("Failed to edit the CrossConnect edid-cfg for device {}",
                    handler().data().deviceId(), e);
            log.debug("Failed configuration {}", xcString);
            return false;
        }
    }

    private NetconfSession getNetconfSession() {
        NetconfController controller = checkNotNull(handler().get(NetconfController.class));
        try {
            NetconfSession session = checkNotNull(
                    controller.getNetconfDevice(handler().data().deviceId()).getSession());
            return session;
        } catch (NullPointerException e) {
            log.error("OPENROADM - session not found for {}", handler().data().deviceId());
            return null;
        }
    }

    /**
     * Convert start and end frequencies to OCh signal.
     *
     * FIXME: assumes slots of 12.5 GHz while devices allows granularity 6.25 GHz
     * FIXME: supports channel spacing 50 and 100
     *
     * @param min starting frequency as double in THz
     * @param max end frequency as double in THz
     * @return OCh signal
     */
    public static OchSignal toOchSignalMinMax(Frequency min, Frequency max) {
        double start = min.asGHz();
        double end = max.asGHz();

        int slots = (int) ((end - start) / ChannelSpacing.CHL_12P5GHZ.frequency().asGHz());
        int multiplier = 0;

        //Conversion for 50 GHz slots
        if (end - start == 50) {
            multiplier = (int) (((end - start) / 2 + start - Spectrum.CENTER_FREQUENCY.asGHz())
                    / ChannelSpacing.CHL_50GHZ.frequency().asGHz());

            return new OchSignal(GridType.DWDM, ChannelSpacing.CHL_50GHZ, multiplier, slots);
        }

        //Conversion for 100 GHz slots
        if (end - start == 100) {
            multiplier = (int) (((end - start) / 2 + start - Spectrum.CENTER_FREQUENCY.asGHz())
                    / ChannelSpacing.CHL_100GHZ.frequency().asGHz());

            return new OchSignal(GridType.DWDM, ChannelSpacing.CHL_100GHZ, multiplier, slots);
        }

        return null;
    }

    public static OchSignal toOchSignalCenterWidth(Frequency center, Frequency width) {

        Frequency radius = width.floorDivision(2);

        double start = center.subtract(radius).asGHz();
        double end = center.add(radius).asGHz();

        int slots = (int) ((end - start) / ChannelSpacing.CHL_12P5GHZ.frequency().asGHz());
        int multiplier = 0;

        //Conversion for 50 GHz slots
        if (end - start == 50) {
            multiplier = (int) (((end - start) / 2 + start - Spectrum.CENTER_FREQUENCY.asGHz())
                    / ChannelSpacing.CHL_50GHZ.frequency().asGHz());

            return new OchSignal(GridType.DWDM, ChannelSpacing.CHL_50GHZ, multiplier, slots);
        }

        //Conversion for 100 GHz slots
        if (end - start == 100) {
            multiplier = (int) (((end - start) / 2 + start - Spectrum.CENTER_FREQUENCY.asGHz())
                    / ChannelSpacing.CHL_100GHZ.frequency().asGHz());

            return new OchSignal(GridType.DWDM, ChannelSpacing.CHL_100GHZ, multiplier, slots);
        }

        return null;
    }
}

