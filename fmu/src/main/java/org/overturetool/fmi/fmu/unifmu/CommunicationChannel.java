package org.overturetool.fmi.fmu.unifmu;

public interface CommunicationChannel extends AutoCloseable {

    byte[] receive();
    void send(byte[] data, int flags);

}
