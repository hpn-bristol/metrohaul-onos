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

import org.onlab.util.Frequency;
import org.onosproject.net.*;

import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.FlowId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class OpenRoadmConnection {

    private static final Logger log = LoggerFactory.getLogger(OpenRoadmConnection.class);

    protected DeviceService deviceService;

    //Parameters of the FlowRule traslated into the OpenRoadm connection
    protected DeviceId deviceId;
    protected FlowId id;
    protected int priotrity;

    protected OpenRoadmFlowRule.Type type; //enum (EXPRESS_LINK, ADD_LINK, DROP_LINK)
    protected OchSignal ochSignal;
    protected PortNumber inPortNumber;
    protected PortNumber outPortNumber;

    protected Port srcPort; //used to retrieve info in the annotations
    protected Port dstPort; //used to retrieve info in the annotations

    //Parameters of <roadm-connections>
    protected String connectionName;
    protected String opticalControlMode;
    protected double targetOutputPower  = 0.0;
    protected String srcConnInterface; //this is an NMC interface
    protected String dstConnInterface; //this is an NMC interface

    //Parameters of associated NMC interfaces: <type>openROADM-if:networkMediaChannelConnectionTerminationPoint</type>
    protected String srcNmcName;
    protected String srcNmcDescription;
    protected String srcNmcType;
    protected String srcNmcAdministrativeState;
    protected String srcNmcSupportingCircuitPack;
    protected String srcNmcSupportingInterface; //this is an MC interface (express-link) or a physical-port (add-drop)
    protected String srcNmcSupportingPort;
    protected Frequency srcNmcFrequency; //expressed in Thz
    protected Frequency srcNmcWidth; //expressed in Ghz

    protected String dstNmcName;
    protected String dstNmcDescription;
    protected String dstNmcType;
    protected String dstNmcAdministrativeState;
    protected String dstNmcSupportingCircuitPack;
    protected String dstNmcSupportingInterface; //this is an MC interface (express-link) or a physical-port (add-drop)
    protected String dstNmcSupportingPort;
    protected Frequency dstNmcFrequency; //expressed in Thz
    protected Frequency dstNmcWidth; //expressed in Ghz

    //Parameters of associated MC interfaces: <type>openROADM-if:mediaChannelTrailTerminationPoint</type>
    protected String srcMcName;
    protected String srcMcDescription;
    protected String srcMcType;
    protected String srcMcAdministrativeState;
    protected String srcMcSupportingCircuitPack;
    protected String srcMcSupportingInterface; //this is a physical-port
    protected String srcMcSupportingPort;
    protected Frequency srcMcMinFrequency; //expressed in Thz
    protected Frequency srcMcMaxFrequency; //expressed in Thz

    protected String dstMcName;
    protected String dstMcDescription;
    protected String dstMcType;
    protected String dstMcAdministrativeState;
    protected String dstMcSupportingCircuitPack;
    protected String dstMcSupportingInterface; //this is a physical-port
    protected String dstMcSupportingPort;
    protected Frequency dstMcMinFrequency; //expressed in Thz
    protected Frequency dstMcMaxFrequency; //expressed in Thz

    /**
     * Builds an OpenRoadmConnection.
     *
     * @param xc the associated OpenRoadmFlowRule
     */
    public OpenRoadmConnection(String openRoadmName, OpenRoadmFlowRule xc, DeviceService deviceService) {

        connectionName = openRoadmName;

        deviceId = xc.deviceId();
        id = xc.id();
        priotrity = xc.priority();

        inPortNumber = xc.inPort();
        outPortNumber = xc.outPort();
        ochSignal = xc.ochSignal();
        type = xc.type;

        srcPort = deviceService.getPort(deviceId,xc.inPort());
        dstPort = deviceService.getPort(deviceId,xc.outPort());

        log.debug("OpenRoadm NETCONF connection created type {} inPort {} outPort {} ochSignal {}",
                type, inPortNumber, outPortNumber, xc.ochSignal());

        switch (type) {
            case EXPRESS_LINK:
                buildExpressLinkData(xc);
                break;
            case ADD_LINK:
                buildAddLinkData(xc);
                break;
            case DROP_LINK:
                buildDropLinkData(xc);
                break;
        }
    }

    private void buildExpressLinkData(OpenRoadmFlowRule xc) {

        //Conversion from ochSignal (center frequency + diameter) to OpenRoadm Media Channel (start - end)
        Frequency freqRadius = Frequency.ofHz(xc.ochSignal().channelSpacing().frequency().asHz() / 2);
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        srcMcMinFrequency = centerFreq.subtract(freqRadius);
        srcMcMaxFrequency = centerFreq.add(freqRadius);
        srcMcSupportingCircuitPack = srcPort.annotations().value("circuit-pack");
        srcNmcFrequency = centerFreq;
        srcNmcWidth = xc.ochSignal().channelSpacing().frequency();

        srcNmcSupportingCircuitPack = srcPort.annotations().value("circuit-pack");
        dstMcMinFrequency = centerFreq.subtract(freqRadius);
        dstMcMaxFrequency = centerFreq.add(freqRadius);
        dstMcSupportingCircuitPack = dstPort.annotations().value("circuit-pack");
        dstNmcFrequency = centerFreq;
        dstNmcWidth = xc.ochSignal().channelSpacing().frequency();
        dstNmcSupportingCircuitPack = dstPort.annotations().value("circuit-pack");
        srcMcName = "MC-TTP-"
                + srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        dstMcName  = "MC-TTP-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        srcNmcName = "NMC-CTP-"
                + srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        dstNmcName = "NMC-CTP-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        //********************************//
        //TODO temporary solution for supporting current TIM datastore
        srcMcSupportingInterface = "OMS-"
                + srcPort.annotations().value("logical-connection-point");

        //Remove following line to be consistent with OpenRoadm standard
//RM        srcMcSupportingInterface = srcMcSupportingInterface.substring(0,9) + "TX";
//        srcMcSupportingInterface = srcMcSupportingInterface.substring(0,9) + "RX";

        dstMcSupportingInterface = "OMS-"
                + dstPort.annotations().value("logical-connection-point");

        //Remove following line to be consistent with OpenRoadm standard
//RM        dstMcSupportingInterface = dstMcSupportingInterface.substring(0,9) + "RX";
//        dstMcSupportingInterface = dstMcSupportingInterface.substring(0,9) + "TX";
        //********************************//

        srcNmcSupportingInterface = srcMcName;
        dstNmcSupportingInterface = dstMcName;

        srcConnInterface = srcNmcName;
        dstConnInterface =  dstNmcName;

        srcMcSupportingPort = srcPort.annotations().value(AnnotationKeys.PORT_NAME);
        dstMcSupportingPort = dstPort.annotations().value(AnnotationKeys.PORT_NAME);

        srcNmcSupportingPort = srcPort.annotations().value(AnnotationKeys.PORT_NAME);
        dstNmcSupportingPort = dstPort.annotations().value(AnnotationKeys.PORT_NAME);

    }

    private void buildAddLinkData(OpenRoadmFlowRule xc) {
        //Conversion from ochSignal (center frequency + diameter) to OpenRoadm Media Channel (start - end)
        Frequency freqRadius = Frequency.ofHz(xc.ochSignal().channelSpacing().frequency().asHz() / 2);
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        //srcMc is not defined because not utilized by an AddLink connection

        srcNmcFrequency = centerFreq;
        srcNmcWidth = xc.ochSignal().channelSpacing().frequency();
        srcNmcSupportingCircuitPack = srcPort.annotations().value("circuit-pack");

        dstMcMinFrequency = centerFreq.subtract(freqRadius);
        dstMcMaxFrequency = centerFreq.add(freqRadius);
        dstMcSupportingCircuitPack = dstPort.annotations().value("circuit-pack");
        dstNmcFrequency = centerFreq;
        dstNmcWidth = xc.ochSignal().channelSpacing().frequency();
        dstNmcSupportingCircuitPack = dstPort.annotations().value("circuit-pack");
        dstMcName  = "MC-TTP-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        srcNmcName = //"NMC-CTP-" +
                srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        dstNmcName = "NMC-CTP-"
                + dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        //********************************//
        //TODO temporary solution for supporting current TIM datastore
        dstMcSupportingInterface = "OMS-"
                + dstPort.annotations().value("logical-connection-point");

        //Remove following line to be consistent with OpenRoadm standard
//RM        dstMcSupportingInterface = dstMcSupportingInterface.substring(0,9) + "RX";
//        dstMcSupportingInterface = dstMcSupportingInterface.substring(0,9) + "TX";
        //********************************//

        srcNmcSupportingInterface = ""; //AddLink does not use this parameter
        dstNmcSupportingInterface = dstMcName;

        srcConnInterface = srcNmcName;
        dstConnInterface =  dstNmcName;

        dstMcSupportingPort = dstPort.annotations().value(AnnotationKeys.PORT_NAME);

        srcNmcSupportingPort = srcPort.annotations().value(AnnotationKeys.PORT_NAME);
        dstNmcSupportingPort = dstPort.annotations().value(AnnotationKeys.PORT_NAME);
    }

    private void buildDropLinkData(OpenRoadmFlowRule xc) {
        //Conversion from ochSignal (center frequency + diameter) to OpenRoadm Media Channel (start - end)
        Frequency freqRadius = Frequency.ofHz(xc.ochSignal().channelSpacing().frequency().asHz() / 2);
        Frequency centerFreq = xc.ochSignal().centralFrequency();

        //dstMc is not defined because not utilized by an AddLink connection

        srcMcMinFrequency = centerFreq.subtract(freqRadius);
        srcMcMaxFrequency = centerFreq.add(freqRadius);
        srcMcSupportingCircuitPack = srcPort.annotations().value("circuit-pack");
        srcNmcFrequency = centerFreq;
        srcNmcWidth = xc.ochSignal().channelSpacing().frequency();
        srcNmcSupportingCircuitPack = srcPort.annotations().value("circuit-pack");
        dstNmcFrequency = centerFreq;
        dstNmcWidth = xc.ochSignal().channelSpacing().frequency();
        dstNmcSupportingCircuitPack = dstPort.annotations().value("circuit-pack");

        srcMcName = "MC-TTP-"
                + srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        srcNmcName = "NMC-CTP-"
                + srcPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        dstNmcName = //"NMC-CTP-" +
                dstPort.annotations().value("logical-connection-point") + "-"
                + centerFreq.asTHz();

        //********************************//
        //TODO temporary solution for supporting current TIM datastore
        srcMcSupportingInterface = "OMS-"
                + srcPort.annotations().value("logical-connection-point");

        //Remove following line to be consistent with OpenRoadm standard
//RM        srcMcSupportingInterface = srcMcSupportingInterface.substring(0,9) + "TX";
//        srcMcSupportingInterface = srcMcSupportingInterface.substring(0,9) + "RX";
        //********************************//

        srcNmcSupportingInterface = srcMcName;
        dstNmcSupportingInterface = dstMcName;

        srcConnInterface = srcNmcName;
        dstConnInterface =  dstNmcName;

        srcMcSupportingPort = srcPort.annotations().value(AnnotationKeys.PORT_NAME);

        srcNmcSupportingPort = srcPort.annotations().value(AnnotationKeys.PORT_NAME);
        dstNmcSupportingPort = dstPort.annotations().value(AnnotationKeys.PORT_NAME);
    }


    protected String getConnectionName() {
        return connectionName;
    }
}
