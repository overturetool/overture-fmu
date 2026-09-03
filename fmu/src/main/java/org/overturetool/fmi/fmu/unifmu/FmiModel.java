package org.overturetool.fmi.fmu.unifmu;

import java.util.List;

public interface FmiModel {

    void setLogCallback(FmiLogCallback fmiLogCallback);

    class Fmi2GetValuePair<T> {
        FmiProtocol.Fmi2Status status;
        List<T> values;

        public Fmi2GetValuePair(FmiProtocol.Fmi2Status status, List<T> values)

        {
            this.status = status;
            this.values = values;
        }
    }

    class Fmi2SerializeFmuStatePair {
        public FmiProtocol.Fmi2Status status;
        public byte[] bytes;

        public Fmi2SerializeFmuStatePair(FmiProtocol.Fmi2Status status, byte[] bytes) {
            this.status = status;
            this.bytes = bytes;
        }
    }


    void log(String message, FmiProtocol.Fmi2Status status, String category);

    FmiProtocol.Fmi2Status fmi2DoStep(double current_time, double step_size, boolean noStepPrior);

    FmiProtocol.Fmi2Status fmi2SetReal(Iterable<Integer> references, Iterable<Double> values) throws Exception;

    FmiProtocol.Fmi2Status fmi2SetInteger(Iterable<Integer> references, Iterable<Integer> values) throws Exception;

    FmiProtocol.Fmi2Status fmi2SetBoolean(Iterable<Integer> references, Iterable<Boolean> values) throws Exception;

    FmiProtocol.Fmi2Status fmi2SetString(Iterable<Integer> references, Iterable<String> values) throws Exception;

    Fmi2GetValuePair<Double> fmi2GetReal(Iterable<Integer> references) throws Exception;

    Fmi2GetValuePair<Integer> fmi2GetInteger(Iterable<Integer> references) throws Exception;

    Fmi2GetValuePair<Boolean> fmi2GetBoolean(Iterable<Integer> references) throws Exception;

    Fmi2GetValuePair<String> fmi2GetString(Iterable<Integer> references) throws Exception;

    FmiProtocol.Fmi2Status fmi2EnterInitializationMode();

    FmiProtocol.Fmi2Status fmi2ExitInitializationMode();

    FmiProtocol.Fmi2Status fmi2SetupExperiment(double start_time, Double stop_time, Double tolerance);

    FmiProtocol.Fmi2Status fmi2Reset();

    FmiProtocol.Fmi2Status fmi2Terminate();

    FmiProtocol.Fmi2Status fmi2CancelStep();

    Fmi2SerializeFmuStatePair fmi2SerializeFmuState() throws Exception;

    FmiProtocol.Fmi2Status fmi2DeserializeFmuState(byte[] bytes) throws Exception;

    FmiProtocol.Fmi2Status fmi2SetDebugLogging(Iterable<String> categories, Boolean logging_on);
}
