package org.overturetool.fmi.fmu.unifmu.model;

import org.overturetool.fmi.fmu.unifmu.protocol.FmiModel;
import org.overturetool.fmi.fmu.unifmu.protocol.FmiProtocol;
import org.overturetool.fmi.fmu.unifmu.FmuPacker;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public abstract class FmiStateModel implements FmiModel {

String modelName;
    final Map<Integer, Object> inputs = new HashMap<>();
    final Map<Integer, Object> outputs = new HashMap<>();
    List<FmuPacker.ScalarVariable> interfaceReferences = new ArrayList<>();

    static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    protected void setInterfaceReferences(List<FmuPacker.ScalarVariable> interfaceReferences) {
        this.interfaceReferences = interfaceReferences;
    }

    abstract protected void setValueParameter(FmuPacker.ScalarVariable var, Object value);

    <T> FmiProtocol.Fmi2Status setInput(Iterable<Integer> references, Iterable<T> values) {
        var success = true;
        var interfacesSize = interfaceReferences.size();
        Iterator<Integer> refIt = references.iterator();
        Iterator<T> valIt = values.iterator();

        while (refIt.hasNext() && valIt.hasNext()) {
            Integer ref = refIt.next();
            T value = valIt.next();

            if (ref >= interfacesSize) {
                success = false;
                continue;
            }

            var modelVariable = interfaceReferences.get(ref);
            if (modelVariable == null) {
                success = false;
                continue;
            }

            switch (modelVariable.causality()) {
                case parameter, calculatedParameter -> {
                    setValueParameter(modelVariable, value);
                    success = true;
                }
                case input -> {
                    inputs.put(ref, value);
                    success = true;
                }
                case output, local, independent -> {
                    success = false;
                }
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

    private <T> Fmi2GetValuePair<T> getOutputs(Class<T> clz, Iterable<Integer> references) {
        //TODO return also parameters
        var values = stream(references).sorted().map(outputs::get).filter(clz::isInstance).map(clz::cast).toList();
        if (values.contains(null)|| values.size() != ((Collection<?>) references).size())
            return new Fmi2GetValuePair<>(FmiProtocol.Fmi2Status.Error, null);
        return new Fmi2GetValuePair<>(FmiProtocol.Fmi2Status.OK, values.stream().map(v -> v).toList());
    }

    @Override
    public Fmi2GetValuePair<Double> fmi2GetReal(Iterable<Integer> references) throws Exception {
        return getOutputs(Double.class, references);
    }

    @Override
    public Fmi2GetValuePair<Integer> fmi2GetInteger(Iterable<Integer> references) throws Exception {
        return getOutputs(Integer.class, references);
    }

    @Override
    public Fmi2GetValuePair<Boolean> fmi2GetBoolean(Iterable<Integer> references) throws Exception {
        return getOutputs(Boolean.class, references);
    }

    @Override
    public Fmi2GetValuePair<String> fmi2GetString(Iterable<Integer> references) throws Exception {
        return getOutputs(String.class, references);
    }
}
