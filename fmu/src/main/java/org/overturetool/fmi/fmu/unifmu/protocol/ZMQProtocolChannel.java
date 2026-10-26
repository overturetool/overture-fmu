package org.overturetool.fmi.fmu.unifmu.protocol;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZMQProtocolChannel implements CommunicationChannel, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ZMQProtocolChannel.class);

    private final ZContext context;
    ZMQ.Socket socket;

    public ZMQProtocolChannel(String endpoint) {
        logger.debug("Creating ZeroMQ REQ socket for dispatcher endpoint {}", endpoint);
        this.context = new ZContext();
        socket = context.createSocket(SocketType.REQ);
        socket.connect(endpoint);
        logger.debug("ZeroMQ connection initiated for endpoint {}; awaiting handshake", endpoint);
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
