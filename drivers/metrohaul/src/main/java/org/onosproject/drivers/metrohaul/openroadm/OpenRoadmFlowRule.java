/*
 * Copyright 2018-present Metro-Haul consortium
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

import org.onosproject.net.OchSignal;
import org.onosproject.net.OchSignalType;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.PortCriterion;
import org.onosproject.net.flow.criteria.OchSignalTypeCriterion;
import org.onosproject.net.flow.criteria.OchSignalCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions.OutputInstruction;

import static org.onosproject.net.flow.criteria.Criterion.Type.OCH_SIGID;
import static org.onosproject.net.flow.criteria.Criterion.Type.OCH_SIGTYPE;
import static org.onosproject.net.flow.criteria.Criterion.Type.IN_PORT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.base.MoreObjects;



public class OpenRoadmFlowRule extends DefaultFlowRule {

    public enum Type {
        EXPRESS_LINK,
        ADD_LINK,
        DROP_LINK
    }

    public Type type;
    private PortNumber inPortNumber;
    private PortNumber outPortNumber;
    private OchSignal ochSignal;
    private OchSignalType ochSignalType;

    private static final Logger log =
            LoggerFactory.getLogger(OpenRoadmFlowRule.class);

    /**
     * Constructor. Build an OpenRoadm flow rule from the passed rule.
     */
    public OpenRoadmFlowRule(FlowRule rule, List<PortNumber> linePorts) {
        super(rule);
        log.info("Line ports {}", linePorts);

        TrafficSelector trafficSelector = rule.selector();
        PortCriterion pc = (PortCriterion) trafficSelector.getCriterion(IN_PORT);
        checkArgument(pc != null, "Missing IN_PORT Criterion");
        inPortNumber = pc.port();

        OchSignalTypeCriterion ostc = (OchSignalTypeCriterion) trafficSelector.getCriterion(OCH_SIGTYPE);
        checkArgument(ostc != null, "Missing OCH_SIGTYPE Criterion");
        ochSignalType = ostc.signalType();

        OchSignalCriterion osc = (OchSignalCriterion) trafficSelector.getCriterion(OCH_SIGID);
        checkArgument(osc != null, "Missing OCH_SIGID Criterion");
        ochSignal = osc.lambda();

        TrafficTreatment TrafficTreatment = rule.treatment();
        List<Instruction> instructions = TrafficTreatment.immediate();

        outPortNumber = instructions.stream()
                .filter(i -> i.type() == Instruction.Type.OUTPUT)
                .map(i -> ((OutputInstruction)i).port())
                .findFirst()
                .orElse(null);
        checkArgument(outPortNumber != null, "Missing OUTPUT Instruction");

        // Express-Link, Add-Link or Drop-Link rule ?
        if (linePorts.contains(inPortNumber) &&
            linePorts.contains(outPortNumber)) {
            type = Type.EXPRESS_LINK;
        }
        if (!linePorts.contains(inPortNumber) &&
            linePorts.contains(outPortNumber)) {
            type = Type.ADD_LINK;
        }
        if (linePorts.contains(inPortNumber) &&
            !linePorts.contains(outPortNumber)) {
            type = Type.DROP_LINK;
        }

        // We should not get a null Type, this means ports are not well-configured
        checkArgument(type != null, "Wrong CrossConnect type");
    }

    public PortNumber inPort() {
        return inPortNumber;
    }

    public PortNumber outPort() {
        return outPortNumber;
    }

    public OchSignal ochSignal() {
        return ochSignal;
    }

    public OchSignalType ochSignalType() {
        return ochSignalType;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OpenRoadmFlowRule)) {
            return false;
        }
        OpenRoadmFlowRule that = (OpenRoadmFlowRule) o;
        return Objects.equals(this.inPortNumber,  that.inPortNumber) &&
               Objects.equals(this.outPortNumber, that.outPortNumber) &&
               Objects.equals(this.ochSignal, that.ochSignal) &&
               Objects.equals(this.ochSignalType, that.ochSignalType) &&
               Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inPortNumber, outPortNumber, ochSignal, ochSignalType, type);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("type", type)
                .add("inPortNumber", inPortNumber)
                .add("outPortNumber", outPortNumber)
                .add("ochSignal", ochSignal)
                .add("ochSignalType", ochSignalType)
                .toString();
    }
}
