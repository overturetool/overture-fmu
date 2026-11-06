package org.overturetool.fmi.fmu.unifmu.model;

import com.fujitsu.vdmj.in.definitions.INClassList;
import com.fujitsu.vdmj.in.definitions.INSystemDefinition;
import com.fujitsu.vdmj.mapper.ClassMapper;
import com.fujitsu.vdmj.scheduler.*;
import com.fujitsu.vdmj.tc.definitions.TCClassList;
import com.fujitsu.vdmj.in.INNode;
import com.fujitsu.vdmj.values.*;
import org.intocps.unifmu.protocol.Fmi2Messages;
import org.overturetool.fmi.fmu.unifmu.FmuPacker;
import org.overturetool.fmi.fmu.vdmj.FmiCoSimClassInterpreter;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runtime-enabled VDM model interpreter with FMI co-simulation scheduler gating.
 * Extends VdmModelInterpreter to add step-controlled execution for FMU co-simulation.
 */
public class VdmRuntimeFmiModel extends VdmFmiModel {

    static final Fmi2Status ok = Fmi2Status.OK;
    static final Fmi2Status fatal = Fmi2Status.Fatal;
    static final Fmi2Status error = Fmi2Status.Error;
    static final Fmi2Status discard = Fmi2Status.Discard;

    Double time = (double) 0;
    double lastCommunicationPoint = 0;
    double lastStepSize = 0;
    CrescendoStateType protocolState = CrescendoStateType.None;
    private FmiCoSimClassInterpreter interpreter = null;

    public VdmRuntimeFmiModel(final List<Path> discoveredVdmrtFiles) {
        super(discoveredVdmrtFiles);
    }

    public VdmRuntimeFmiModel() {
        super();
    }

    public VdmRuntimeFmiModel(String modelInstanceName, URI resourceLocation, Boolean visible, Boolean loggingOn) {
        super(modelInstanceName, resourceLocation, visible, loggingOn);
    }

    public VdmRuntimeFmiModel(Fmi2Messages.Fmi2Instantiate instantiation, Consumer<LogMessage> logHandler) {
        this(instantiation.getInstanceName(), URI.create(instantiation.getFmuResourceLocation()),
                instantiation.getVisible(), instantiation.getLoggingOn());
        setLogHandler(logHandler);
    }

    private void applyInputsToRuntime() {
        synchronized (inputs) {
            for (var entry : interfaceReferences) {
                if (entry.causality() == FmuPacker.Causality.input) {

                    var value = searchForMember(Arrays.stream((entry.name() + ".value").split("\\.")).toList());
                    if (value != null) {
                        var newValue = inputs.get(entry.valueReference());
                        if (newValue != null) {
                            if (value instanceof UpdatableValue uval) {
                                try {
                                    Value input = switch (entry.type()) {
                                        case Real -> new RealValue((Double) newValue);
                                        case Integer -> new IntegerValue((Integer) newValue);
                                        case Boolean -> new BooleanValue((Boolean) newValue);
                                        case String -> new SeqValue((String) newValue);
                                    };
                                    uval.set(null, input, null);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    private void readOutputsFromRuntime() {
        synchronized (outputs) {

            for (var entry : interfaceReferences) {
                if (entry.causality() == FmuPacker.Causality.output) {

                    var value = searchForMember(Arrays.stream((entry.name() + ".value").split("\\.")).toList());
                    if (value != null) {
                        try {
                            Object output = switch (entry.type()) {
                                case Real -> value.realValue(null);
                                case Integer -> Math.toIntExact(value.intValue(null));
                                case Boolean -> value.boolValue(null);
                                case String -> value.stringValue(null);
                            };
                            outputs.put(entry.valueReference(), output);
                        } catch (com.fujitsu.vdmj.runtime.ValueException e) {
                            throw new IllegalStateException("Cannot read FMI output " + entry.name(), e);
                        }

                    }
                }
            }
        }
    }

    private Value searchForMember(List<String> path) {

        if (path.size() < 3)
            return null;
        var systemClassName = path.getFirst();
        var systemStaticMemberName = path.get(1);
        var remainingPath = path.subList(2, path.size());

        var system = interpreter.getClasses().stream().filter(c -> c instanceof INSystemDefinition).map(INSystemDefinition.class::cast).findFirst();
        if (system.isPresent() && system.get().name.getName().equals(systemClassName)) {
            var staticSystemCtxt = system.get().getStatics();
            for (var member : staticSystemCtxt.getVisibleNames()) {
                if (member.getName().equals(systemStaticMemberName)) {
                    var value = staticSystemCtxt.get(member);
                    return searchForMember(value, remainingPath);
                }
            }
        }
        return null;
    }

    private Value searchForMember(Value value, List<String> path) {

        if (!path.isEmpty() && value.deref() instanceof ObjectValue object) {
            var memberName = path.getFirst();
            var remainingPath = path.subList(1, path.size());

            for (var member : object.members.entrySet()) {
                if (member.getKey().getName().equals(memberName)) {
                    if (remainingPath.isEmpty())
                        return member.getValue();
                    else if (member.getValue().deref() instanceof ObjectValue) {
                        return searchForMember(member.getValue(), remainingPath);
                    }
                }
            }
        }
        return null;
    }

    boolean checkStats(CrescendoStateType... st) {
        for (CrescendoStateType crescendoStateType : st) {
            if (crescendoStateType == protocolState) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Fmi2Status fmi2DoStep(double current_time, double step_size, boolean noStepPrior) {

        if (!checkStats(CrescendoStateType.Initialized)) {
            return fatal;
        }

        try {

            double nextFmiTime = current_time
                    + step_size;

//            this.lastCommunicationPoint = current_time;
//            this.lastStepSize = step_size;

            fmiLog(LogCategory.LogProtocol, "DoStep called: " + nextFmiTime);

            if (nextFmiTime < time) {
                fmiLog(LogCategory.LogProtocol, "DoStep skipping execution next time is: "
                        + time);
                return ok;
            }

            long internalVdmClockTime = SystemClock.timeToInternal(SystemClock.TimeUnit.seconds, nextFmiTime);

            log(LogCategory.LogAll, Fmi2Status.OK, "DoStep VDM internal stop time: "
                    + internalVdmClockTime);

            synchronized (this.interpreter.stepLock) {
                applyInputsToRuntime();

                this.interpreter.advance(internalVdmClockTime);

//                nextSimulationStop = internalVdmClockTime;
//                interpreterRunning = true;
//                stepLock.notifyAll();
//
//                stepLock.wait();

                readOutputsFromRuntime();
            }

//            System.out.println("DoStep target internal time: "
//                    + internalVdmClockTime + ". VDM at time: " + this.interpreter.getCurrentTime() + " ( " + VdmSystemTime.internalToTime(SystemClock.TimeUnit.seconds, this.interpreter.getCurrentTime()) + " [s]). Next possible time is: " + this.interpreter.getNextSchedulableActionTime());
            log(LogCategory.LogAll, ok, "DoStep VDM internal time reached: "
                    + internalVdmClockTime + " at doStep completion. VDM at time: " + this.interpreter.getCurrentTime());


            // Convert back to SI from internal VDM clock
            Double curTime = VdmSystemTime.internalToTime(SystemClock.TimeUnit.seconds, this.interpreter.getCurrentTime());
            log(LogCategory.LogAll, ok, "DoStep VDM clock conversion. Internal time: "
                    + internalVdmClockTime + " External [s]: " + curTime);

            time = curTime;
            fmiLog(LogCategory.LogProtocol, "DoStep waiting for next DoStep at: "
                    + time);


            var nextVdmTime = VdmSystemTime.internalToTime(SystemClock.TimeUnit.seconds, interpreter.getNextSchedulableActionTime());

            if (Math.abs(curTime - nextFmiTime) <= 1e-10 ||nextVdmTime > nextFmiTime) {
                return ok;
            } else {
                return discard;
            }

        } catch (Exception e) {
            logger.error("Error in doStep", e);
            fmiLog(LogCategory.LogError, "Error in DoStep: " + e.getMessage());
            return fatal;
        }

    }

    @Override
    public Fmi2Status fmi2ExitInitializationMode() {
        try {
            typeCheck();
            System.out.println("fmi2ExitInitializationMode");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        TCClassList checkedClasses = tcClassList;

        try {

            INClassList inClassList = ClassMapper.getInstance(INNode.MAPPINGS).init().convert(checkedClasses);
            this.interpreter = new FmiCoSimClassInterpreter(inClassList, checkedClasses);
            this.interpreter.start();

            protocolState = CrescendoStateType.Initialized;
            return Fmi2Status.OK;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    enum CrescendoStateType {
        None, Instantiated, Initialized, Stepping, Terminated
    }


}
