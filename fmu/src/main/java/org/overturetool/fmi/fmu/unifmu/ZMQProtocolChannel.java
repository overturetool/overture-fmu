package org.overturetool.fmi.fmu.unifmu;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;

public class ZMQProtocolChannel implements CommunicationChannel , AutoCloseable {

    private final ZContext context;
    ZMQ.Socket socket;

    public ZMQProtocolChannel(String endpoint) {
        this.context = new ZContext();
        socket = context.createSocket(SocketType.REQ);
        socket.connect(endpoint);
    }


    @Override
    public byte[] receive() {
        return socket.recv();
    }

    @Override
    public void send(byte[] data, int flags) {
        socket.send(data, flags);
    }

    @Override
    public void close() {
        socket.close();
        context.close();
    }
}
