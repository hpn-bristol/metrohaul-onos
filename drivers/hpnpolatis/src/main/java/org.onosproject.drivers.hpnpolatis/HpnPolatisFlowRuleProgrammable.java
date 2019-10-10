/*
 * Copyright 2017 Open Networking Foundation
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

package org.onosproject.drivers.hpnpolatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;

import org.onosproject.net.PortNumber;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.OchSignalCriterion;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.protocol.rest.RestSBController;
import org.slf4j.Logger;


import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.slf4j.LoggerFactory.getLogger;

public class HpnPolatisFlowRuleProgrammable extends AbstractHandlerBehaviour implements FlowRuleProgrammable {

    public static final MediaType JSON = MediaType.valueOf(MediaType.APPLICATION_JSON);
    private static final Logger log = getLogger(HpnPolatisDeviceDiscovery.class);
    private static final int DEFAULT_PRIORITY = 88;
    private static final String DEFAULT_APP = "org.onosproject.drivers.hpnpolatis";


    @Override
    public Collection<FlowEntry> getFlowEntries() {

        FlowRuleService flowRuleService = handler().get(FlowRuleService.class);
        Iterable<FlowEntry> flowEntriesInONOS = flowRuleService.getFlowEntries(did());

        Collection<FlowEntry> flowEntries = getFlowEntriesFromDevice();

        try {
            if (flowEntries.size() != 0) {
                HashMap<FlowEntry, Boolean> flowDiscrepancy = new HashMap<>();

                for (FlowEntry flowEntry : flowEntries){
                    flowDiscrepancy.put(flowEntry, false);
                    for (FlowEntry flowEntryInONOS : flowEntriesInONOS){
                        if(flowEntry.equals(flowEntryInONOS)){
                            flowDiscrepancy.put(flowEntry, true);
                        }
                    }
                }

                for(FlowEntry flowEntry : flowDiscrepancy.keySet()){
                    if(!flowDiscrepancy.get(flowEntry)){
                        flowRuleService.applyFlowRules(flowEntry);
                    }
                }
                return flowEntries;
            }
        }
        catch(NullPointerException e){
            return ImmutableList.of();
        }

        return flowEntries;
    }

    @Override
    public Collection<FlowRule> applyFlowRules(Collection<FlowRule> rules) {

        //Collection<FlowEntry> flowEntries = getFlowRulesFromDevice();

        RestSBController restSBController = handler().get(RestSBController.class);

        outerloop:
        for(FlowRule rule: rules){

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode flowNode = mapper.createObjectNode();

            Set<Criterion> criteria = rule.selector().criteria();
            List<Instruction> instructions = rule.treatment().immediate();

            for (Criterion criterion : criteria)
                if (criterion instanceof OchSignalCriterion)
                    continue outerloop;

            PortNumber inPortNumber = criteria.stream()
                    .filter(c -> c instanceof PortCriterion)
                    .map(c -> ((PortCriterion) c).port())
                    .findAny()
                    .orElse(null);

            PortNumber outPortNumber = instructions.stream()
                    .filter(c -> c.type() == Instruction.Type.OUTPUT)
                    .map(c -> ((Instructions.OutputInstruction) c).port())
                    .findAny()
                    .orElse(null);

            flowNode.put(Long.toString(inPortNumber.toLong()), (int)outPortNumber.toLong());

            int response = restSBController.patch(did(), "/connections/",
                    new ByteArrayInputStream(flowNode.toString().getBytes()), JSON);

            log.info("Flow installed with response: {}", response);


        }

        return rules;
    }

    @Override
    public Collection<FlowRule> removeFlowRules(Collection<FlowRule> rules) {

        RestSBController restSBController = handler().get(RestSBController.class);

        for(FlowRule rule: rules){
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode flowNode = mapper.createObjectNode();

            ObjectMapper mapper2 = new ObjectMapper();
            ObjectNode flowNode2 = mapper.createObjectNode();

            Set<Criterion> criteria = rule.selector().criteria();
            List<Instruction> instructions = rule.treatment().immediate();

            PortNumber inPortNumber = criteria.stream()
                    .filter(c -> c instanceof PortCriterion)
                    .map(c -> ((PortCriterion) c).port())
                    .findAny()
                    .orElse(null);

            PortNumber outPortNumber = instructions.stream()
                    .filter(c -> c.type() == Instruction.Type.OUTPUT)
                    .map(c -> ((Instructions.OutputInstruction) c).port())
                    .findAny()
                    .orElse(null);

            flowNode2.put(Long.toString(inPortNumber.toLong()), (int)outPortNumber.toLong());

            flowNode.set("remove", flowNode2);

            int response = restSBController.patch(did(), "/connections/",
                    new ByteArrayInputStream(flowNode.toString().getBytes()), JSON);

            log.info("Flow deleted with response: {}", response);


        }

        return rules;


    }

    private DeviceId did() {
        return handler().data().deviceId();
    }

    private Collection<FlowEntry> getFlowEntriesFromDevice(){
        Collection<FlowEntry> flowEntries = new ArrayList<>();
        RestSBController restSBController = handler().get(RestSBController.class);
        InputStream flowStream = restSBController.get(did(), "/connections/", JSON);

        try {
            JsonNode jsonNode = new ObjectMapper().readTree(flowStream);
            Iterator<String> fieldNames = jsonNode.fieldNames();

            while(fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode field = jsonNode.get(fieldName);

                //log.info("HPNPolatis, Flow is: " + fieldName + ": " + field.asInt());

                TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
                selectorBuilder.matchInPort(PortNumber.portNumber(fieldName));
                TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
                treatmentBuilder.setOutput(PortNumber.portNumber(field.asInt()));

                FlowRule rule = DefaultFlowRule.builder()
                        .forDevice(did())
                        .withSelector(selectorBuilder.build())
                        .withTreatment(treatmentBuilder.build())
                        .withPriority(DEFAULT_PRIORITY)
                        .fromApp(handler().get(CoreService.class).getAppId(DEFAULT_APP))
                        .makePermanent()
                        .build();

                flowEntries.add(new DefaultFlowEntry(rule, FlowEntry.FlowEntryState.ADDED));
            }

            return flowEntries;
        }
        catch(IOException e){
            log.error("Exception discoverPortDetails() {}", did(), e);
            return ImmutableList.of();
        }

    }



}
