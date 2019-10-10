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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

import org.onosproject.net.*;
import org.onosproject.netconf.*;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

import org.onlab.packet.ChassisId;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;

import org.onosproject.drivers.utilities.XmlConfigParser;

import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.DeviceDescription;
import org.onosproject.net.device.DeviceDescriptionDiscovery;
import org.onosproject.net.device.DefaultDeviceDescription;
import org.onosproject.net.device.DefaultPortDescription;
import org.onosproject.net.device.DefaultPortDescription.Builder;
import org.onosproject.net.device.PortDescription;

import org.onosproject.net.OchSignal;
import org.onosproject.net.OchSignalType;
import org.onosproject.net.OduSignalType;
import org.onosproject.net.optical.device.OmsPortHelper;
import org.onosproject.net.optical.device.OchPortHelper;
import org.onlab.util.Frequency;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.GridType;
import org.onosproject.net.Lambda;


import org.onosproject.net.driver.AbstractHandlerBehaviour;

import org.onosproject.net.Port.Type;

import com.google.common.collect.ImmutableList;
import org.slf4j.LoggerFactory;


/**
 * Driver Implementation of the DeviceDescrption discovery for OpenROADM.
 */
public class OpenRoadmDeviceDescription extends AbstractHandlerBehaviour
    implements DeviceDescriptionDiscovery {

    private static final String RPC_TAG_NETCONF_BASE =
        "<rpc xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">";

    private static final String RPC_CLOSE_TAG =
        "</rpc>";

    public static final ChannelSpacing CHANNEL_SPACING = ChannelSpacing.CHL_50GHZ;
    // 191.35 1566.72 C1
    public static final Frequency START_CENTER_FREQ = Frequency.ofGHz(191_350);
    // 196.10 1528.77 C96  (96 channels at 50 GHz)
    public static final Frequency STOP_CENTER_FREQ = Frequency.ofGHz(196_100);

    private static final Logger log = LoggerFactory.getLogger(OpenRoadmDeviceDescription.class);

    /**
     * Returns the NetconfSession with the device for which the method was called.
     *
     * @param deviceId device indetifier
     *
     * @return The netconf session or null
     */
    private NetconfSession getNetconfSession(DeviceId deviceId) {
        NetconfController controller = handler().get(NetconfController.class);
        NetconfDevice ncdev = controller.getDevicesMap().get(deviceId);
        if (ncdev == null) {
            log.trace("No netconf device, returning null session");
            return null;
        }
        return ncdev.getSession();
    }

    /**
     * Get the deviceId for which the methods apply.
     *
     * @return The deviceId as contained in the handler data
     */
    private DeviceId did() {
        return handler().data().deviceId();
    }


    /**
     * Get the device instance for which the methods apply.
     *
     * @return The device instance
     */
    private Device getDevice() {
        DeviceService deviceService = checkNotNull(handler().get(DeviceService.class));
        Device device = deviceService.getDevice(did());
        return device;
    }

    /**
     * Get the device instance for which the methods apply.
     *
     * @return The device instance
     */
    private NetconfDevice getNetconfDevice() {
        NetconfController controller = checkNotNull(handler().get(NetconfController.class));
        NetconfDevice ncDevice = controller.getDevicesMap().get(handler().data().deviceId());
        return ncDevice;
    }


    /**
     * Construct a String with a Netconf filtered get RPC Message.
     *
     * @param filter A valid XML tree with the filter to apply in the get
     * @return a String containing the RPC XML Document
     */
    private String filteredGetBuilder(String filter) {
        StringBuilder rpc = new StringBuilder(RPC_TAG_NETCONF_BASE);
        rpc.append("<get>");
        rpc.append("<filter type='subtree'>");
        rpc.append(filter);
        rpc.append("</filter>");
        rpc.append("</get>");
        rpc.append(RPC_CLOSE_TAG);
        return rpc.toString();
    }

    /**
     * Construct a String with a Netconf filtered get RPC Message.
     *
     * @param filter A valid XPath Expression with the filter to apply in the get
     * @return a String containing the RPC XML Document
     *
     * Note: server must support xpath capability.
     */
    private String xpathFilteredGetBuilder(String filter) {
        StringBuilder rpc = new StringBuilder(RPC_TAG_NETCONF_BASE);
        rpc.append("<get>");
        rpc.append("<filter type='xpath' select=\"");
        rpc.append(filter);
        rpc.append("\"/>");
        rpc.append("</get>");
        rpc.append(RPC_CLOSE_TAG);
        return rpc.toString();
    }


    /**
     * Builds a request to get Device details, operational data.
     *
     * @return A string with the Netconf RPC for a get with subtree info
     */
    private String getDeviceDetailsBuilder() {
        StringBuilder filter = new StringBuilder();
        filter.append(" <org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        filter.append("   <info/>");
        filter.append(" </org-openroadm-device>");
        return filteredGetBuilder(filter.toString());
    }

    /**
     * Builds a request to get Ports data
     *
     * @return A string with the Netconf RPC
     */
    private String getDevicePortsBuilder() {
        StringBuilder filter = new StringBuilder();
        filter.append("/org-openroadm-device/circuit-packs/ports[port-qual='roadm-external']");
        return xpathFilteredGetBuilder(filter.toString());
    }

    /**
     * Builds a request to get Device Degrees, config and operational data.
     *
     * @return A string with the Netconf RPC for a get with subtree rpcing based on
     *    /components/component/state/type being oc-platform-types:PORT
     */
    private String getDeviceDegreesBuilder() {
        StringBuilder filter = new StringBuilder();
        filter.append(" <org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        filter.append("   <degree/>");
        filter.append(" </org-openroadm-device>");
        return filteredGetBuilder(filter.toString());
    }

    /**
     * Builds a request to get Device SharedRiskGroups, config and operational data.
     *
     * @return A string with the Netconf RPC for a get with subtree
     */
    private String getDeviceSharedRiskGroupsBuilder() {
        StringBuilder filter = new StringBuilder();
        filter.append(" <org-openroadm-device xmlns=\"http://org/openroadm/device\">");
        filter.append("   <shared-risk-group/>");
        filter.append(" </org-openroadm-device>");
        return filteredGetBuilder(filter.toString());
    }

    /**
     * Returns a DeviceDescription with Device info.
     *
     * @return DeviceDescription or null
     *
     * //CHECKSTYLE:OFF
     * <pre>{@code
     * <rpc-reply message-id="1" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
     *  <data xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
     *    <org-openroadm-device xmlns="http://org/openroadm/device">
     *       <info>
     *         ...
     * </data>
     *}</pre>
     * //CHECKSTYLE:ON
     */
    @Override
    public DeviceDescription discoverDeviceDetails() {
        log.info("OpenRoadmDeviceDescription::discoverDeviceDetails device {}", did());
        boolean defaultAvailable = true;

        // Get the Netconf device
        NetconfDevice ncDevice = getNetconfDevice();
        if (ncDevice == null) {
            log.error("Internal ONOS Error: Device reachable, deviceID {} is not in Devices Map", did());
            return null;
        }

        DefaultAnnotations.Builder annotationsBuilder = DefaultAnnotations.builder();
        Device.Type type = Device.Type.ROADM;

        // Some defaults values
        String vendor       = "not loaded";
        String hwVersion    = "not loaded";
        String swVersion    = "not loaded";
        String serialNumber = "not loaded";
        String chassisId    = "not loaded";

        // Get the session,
        NetconfSession session = getNetconfSession(did());
        if (session != null) {
            try {

                /*
                 * ECOC-Hack. At each run we copy the startup config to the running config
                 * We should check the capabilities, to see if the agent supports it. Also, 
                 * we can do this sending the raw message or we can use a methof of 
                 * NetconfSession. We lock the running datastore.
                <?xml version='1.0' encoding='UTF-8'?>
                <rpc message-id="0" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
                <copy-config>
                    <target>
                        <running/>
                    </target>
                    <source>
                        <candidate/>
                    </source>
                </copy-config>
                </rpc>

                In some cases we get (e.g. confd, depending on configuration

                <rpc-reply xmlns="urn:ietf:params:xml:ns:netconf:base:1.0" message-id="2">
                    <rpc-error>
                        <error-type>protocol</error-type>
                        <error-tag>invalid-value</error-tag>
                        <error-severity>error</error-severity>
                        <error-message xml:lang="en">Unsupported capability :startup</error-message>
                        <error-info>
                            <bad-element>startup</bad-element>
                        </error-info>
                    </rpc-error>
                </rpc-reply>
                */
                log.info("OpenRoadmDeviceDescription::discoverDeviceDetails - Resetting RUNNING {}", did());
                //session.lock();
                boolean ret = session.copyConfig(DatastoreId.RUNNING ,DatastoreId.STARTUP);
                // session.unlock();
                if (ret) {
                    log.info("Operation {}", ret? "ok" : "failed");
                }
                


                //String reply = session.get(getDeviceDetailsBuilder());
                //String reply = session.getConfig(DatastoreId.RUNNING, null);
                StringBuilder filterBuilder = new StringBuilder();
                filterBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
                filterBuilder.append("<info>");
                filterBuilder.append("</info>");
                filterBuilder.append("</org-openroadm-device>");
                String reply = session.get(filterBuilder.toString(), null);

                XMLConfiguration xconf = (XMLConfiguration) XmlConfigParser.loadXmlString(reply);
                String nodeType = xconf.getString("data.org-openroadm-device.info.node-type", "");
                if (!nodeType.equals("rdm")) {
                    log.error("OpenRoadmDeviceDescription::discoverDeviceDetails device {} wrong node-type", did());
                }
                vendor       = xconf.getString("data.org-openroadm-device.info.vendor", vendor);
                hwVersion    = xconf.getString("data.org-openroadm-device.info.model", hwVersion);
                swVersion    = xconf.getString("data.org-openroadm-device.info.softwareVersion", swVersion);
                serialNumber = xconf.getString("data.org-openroadm-device.info.serial-id", serialNumber);
                chassisId    = xconf.getString("data.org-openroadm-device.info.node-number", chassisId);

                // GEOLOCATION
                String longitudeStr = xconf.getString("data.org-openroadm-device.info.geoLocation.longitude");
                String latitudeStr  = xconf.getString("data.org-openroadm-device.info.geoLocation.latitude");
                if (longitudeStr != null && latitudeStr != null) {
                    annotationsBuilder
                        .set(AnnotationKeys.LONGITUDE, longitudeStr)
                        .set(AnnotationKeys.LATITUDE, latitudeStr);
                }
            } catch (Exception e) {
                throw new RuntimeException(new NetconfException("Failed to retrieve version info.", e));
            }
        } else {
            log.info("OpenRoadmDeviceDescription::discoverDeviceDetails - No netconf session for {}", did());
        }

        log.info("TYPE      {}", type);
        log.info("VENDOR    {}", vendor);
        log.info("HWVERSION {}", hwVersion);
        log.info("SWVERSION {}", swVersion);
        log.info("SERIAL    {}", serialNumber);
        log.info("CHASSISID {}", chassisId);

        ChassisId cid = new ChassisId(Long.valueOf(chassisId, 10));
        return new DefaultDeviceDescription(did().uri(), type, vendor, hwVersion, swVersion, serialNumber, cid,
                annotationsBuilder.build());
    }

    /**
     * Returns a list of PortDescriptions for the device.
     *
     * @return a list of descriptions.
     *
     * The RPC reply follows the following pattern:
     * //CHECKSTYLE:OFF
     * <pre>{@code
     * <?xml version="1.0" encoding="UTF-8"?>
     * <rpc-reply message-id="3" xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
       <data>
         <org-openroadm-device xmlns="http://org/openroadm/device">
           <circuit-packs>
             <circuit-pack-name>mux-demux</circuit-pack-name>
             <ports>
               <port-name>ext-r22</port-name>
               <port-type>fixed</port-type>
               <port-qual>roadm-external</port-qual>
               <circuit-id>mux-demux</circuit-id>
               <administrative-state>outOfService</administrative-state>
               <logical-connection-point>SRG1-PP22-RX</logical-connection-point>
            </ports>
     * }</pre>
     * //CHECKSTYLE:ON
     */
    @Override
    public List<PortDescription> discoverPortDetails() {
        log.info("OpenRoadmDeviceDescription::discoverPortDetails device {}", did());

        try {
            NetconfSession session = getNetconfSession(did());
            if (session == null) {
                log.error("discoverPortDetails called with null session for {}", did());
                return ImmutableList.of();
            }

            //String reply = session.getConfig(DatastoreId.RUNNING, null);
            StringBuilder filterBuilder = new StringBuilder();
            filterBuilder.append("<org-openroadm-device xmlns=\"http://org/openroadm/device\">");
            filterBuilder.append("<circuit-packs>");
            filterBuilder.append("</circuit-packs>");
            filterBuilder.append("</org-openroadm-device>");
            String reply = session.get(filterBuilder.toString(), null);

            XMLConfiguration xconf = (XMLConfiguration) XmlConfigParser.loadXmlString(reply);
            List<HierarchicalConfiguration> cpacks = xconf.configurationsAt("data.org-openroadm-device.circuit-packs");
            return parseCircuitPacks(cpacks);
        } catch (Exception e) {
            log.error("Exception discoverPortDetails() {}", did(), e);
            return ImmutableList.of();
        }
    }

    /**
     * Parses circuit-pack information from OpenROADM XML configuration.
     *
     * @param components the XML document with components root.
     * @return List of ports
     *
     */
    protected List<PortDescription> parseCircuitPacks(List<HierarchicalConfiguration> cpacks) {
        List<PortDescription> cplist;
        List<PortDescription> list = new ArrayList<PortDescription>();
        for (HierarchicalConfiguration cpack : cpacks) {
            String cpname = cpack.getString("circuit-pack-name", "");
            List<HierarchicalConfiguration> ports = cpack.configurationsAt("ports");
            cplist = parsePorts(ports,cpname);
            list.addAll(cplist);
        }
        return list;
    }

    /**
     * Parses port information from OpenROADM XML configuration.
     *
     * @param components the XML document with components root.
     * @return List of ports
     *
     * //CHECKSTYLE:OFF
     * <pre>{@code
     * }</pre>
     * //CHECKSTYLE:ON
     */
    protected List<PortDescription> parsePorts(List<HierarchicalConfiguration> ports, String cpname) {
        List<PortDescription> list = new ArrayList<PortDescription>();
        for (HierarchicalConfiguration port : ports) {
            PortDescription pd = parsePortComponent(port, cpname);
            if (pd != null) {
                list.add(pd);
            }
        }
        return list;
    }


    /**
     * Parses a component XML doc into a PortDescription.
     *
     * @param port the port to parse
     * @return PortDescription or null
     */
    private PortDescription parsePortComponent(HierarchicalConfiguration port, String cpname) {

        Map<String, String> annotations = new HashMap<>();

        annotations.put("circuit-pack", cpname);

        String name = port.getString("port-name", "unnamed");
        annotations.put(AnnotationKeys.PORT_NAME, name);

        String lcp  = port.getString("logical-connection-point", "");
        annotations.put("logical-connection-point", lcp);

        String qual = port.getString("port-qual", "unnamed");

        // Rules
        // port N-M --> N*100+M
        // ext-txN --> 300+N
        // ext-rxN --> 400+N
        // int-txN --> 500+N
        // int-rxN --> 600+N

        long portNum = 0;
        String aux = name;
        if (name.startsWith("ext-tx")) {
          portNum = 300 + Long.parseLong(aux.replaceAll("\\D+", ""));
        } else if (name.startsWith("ext-rx")) {
          portNum = 400 + Long.parseLong(aux.replaceAll("\\D+", ""));
        } else if (name.startsWith("int-tx")) {
          portNum = 500 + Long.parseLong(aux.replaceAll("\\D+", ""));
        } else if (name.startsWith("int-rx")) {
          portNum = 600 + Long.parseLong(aux.replaceAll("\\D+", ""));
        } else {
          String[] parts = name.split("-");
          portNum = Long.parseLong(parts[0])*100 + Long.parseLong(parts[1]);
        }

        // log.error("discoverPortDetails port {} num {}", name, portNum);

        PortNumber pNum = PortNumber.portNumber(portNum);
        if (qual.equals("roadm-external")) {
            if (port.getString("port-wavelength-type", "wavelength").equals("wavelength")) {
                //OchSignal is needed for OchPortDescription constructor, but it's tunable
                OchSignal signalId = OchSignal.newDwdmSlot(ChannelSpacing.CHL_50GHZ, 1);
                return OchPortHelper.ochPortDescription(pNum, true /* enabled */,
                        OduSignalType.ODU4,
                        true /* tunable */ ,
                        signalId,
                        DefaultAnnotations.builder().putAll(annotations).build());
            } else {

                return OmsPortHelper.omsPortDescription(pNum, true /* enabled */,
                        START_CENTER_FREQ,
                        STOP_CENTER_FREQ,
                        CHANNEL_SPACING.frequency(),
                        DefaultAnnotations.builder().putAll(annotations).build());
            }
        } else {
            return null;
        }
    }
    }

