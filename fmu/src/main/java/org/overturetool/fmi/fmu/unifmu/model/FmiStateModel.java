package org.overturetool.fmi.fmu.unifmu.model;

import com.fujitsu.vdmj.values.Value;
import org.overturetool.fmi.fmu.unifmu.FmiModel;
import org.overturetool.fmi.fmu.unifmu.FmiProtocol;

import java.util.*;

public abstract class FmiStateModel implements FmiModel {

    public enum ModelInterface {
        Value,
        RuntimeVariable
    }

    enum Mode {
        ParserModel,
        RuntimeModel

    }

    public static class ModelVariable {
        String name;
        ModelInterface modelInterface;

        public ModelVariable(String name, ModelInterface modelInterface) {
            this.name = name;
            this.modelInterface = modelInterface;
        }
    }

    List<ModelVariable> interfaceReferences = new ArrayList<>();
    Mode mode = Mode.ParserModel;
    Map<ModelVariable, Value> runtimeValues = new HashMap<>();

    protected void setInterfaceReferences(List<ModelVariable> interfaceReferences) {
        this.interfaceReferences = interfaceReferences;
        this.mode = Mode.ParserModel;
    }


    @Override
    public FmiProtocol.Fmi2Status fmi2ExitInitializationMode() {
        mode = Mode.RuntimeModel;
        return FmiProtocol.Fmi2Status.OK;
    }

    abstract protected void setValueParameter(ModelVariable var, Object value);


    Map<Integer, Object> inputs = new HashMap<>();


    <T> FmiProtocol.Fmi2Status setInput(Iterable<Integer> references, Iterable<T> values) {
        var success = true;
        var interfacesSize = interfaceReferences.size();
        Iterator<Integer> refIt = references.iterator();
        Iterator<T> valIt = values.iterator();

        while (refIt.hasNext() && valIt.hasNext()) {
            Integer ref = refIt.next();
            T value = valIt.next();

            if (ref > interfacesSize) {
                success = false;
                continue;
            }

            var modelVariable = interfaceReferences.get(ref);
            if (modelVariable == null) {
                success = false;
                continue;
            }

            if (modelVariable.modelInterface != ModelInterface.RuntimeVariable)

                if (inputs.containsKey(ref)) {
                    inputs.put(ref, value);
                } else {
                    success = false;
                }
        }

        if (refIt.hasNext() || valIt.hasNext()) {
            success = false;
        }

        return success ? FmiProtocol.Fmi2Status.OK : FmiProtocol.Fmi2Status.Error;
    }


    @Override
    public FmiProtocol.Fmi2Status fmi2SetReal(Iterable<Integer> references, Iterable<Double> values) throws Exception {
        return setInput(references, values);
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2SetInteger(Iterable<Integer> references, Iterable<Integer> values) throws Exception {
        return setInput(references, values);
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2SetBoolean(Iterable<Integer> references, Iterable<Boolean> values) throws Exception {
        return setInput(references, values);
    }

    @Override
    public FmiProtocol.Fmi2Status fmi2SetString(Iterable<Integer> references, Iterable<String> values) throws Exception {
        return setInput(references, values);
    }

    @Override
    public Fmi2GetValuePair<Double> fmi2GetReal(Iterable<Integer> references) throws Exception {
        return null;
    }

    @Override
    public Fmi2GetValuePair<Integer> fmi2GetInteger(Iterable<Integer> references) throws Exception {
        return null;
    }

    @Override
    public Fmi2GetValuePair<Boolean> fmi2GetBoolean(Iterable<Integer> references) throws Exception {
        return null;
    }

    @Override
    public Fmi2GetValuePair<String> fmi2GetString(Iterable<Integer> references) throws Exception {
        return null;
    }
}
