package org.overturetool.fmi.fmu.unifmu.protocol;

public interface FmiLogCallback {
    public  void logFmiCallback(FmiProtocol.Fmi2Status status, String category, String message);
}
