package org.overturetool.fmi.fmu.unifmu.protocol;

public interface CommunicationChannel extends AutoCloseable {

    byte[] receive();
    void send(byte[] data, int flags);

}
