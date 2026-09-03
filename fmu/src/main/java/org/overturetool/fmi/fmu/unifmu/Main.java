package org.overturetool.fmi.fmu.unifmu;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("starting FMU");

        try (CommunicationChannel channel = new ZMQProtocolChannel(System.getenv("UNIFMU_DISPATCHER_ENDPOINT"))) {
            FmiProtocol protocol = new FmiProtocol(channel);
            protocol.run();

        }
    }
}