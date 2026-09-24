package org.overturetool.fmi.fmu.unifmu.protocol;

public class Main {

    public static final String PROPERTY_COMMUNICATION_CHANNEL = "communicationChannel".toLowerCase();

    public static void main(String[] args) throws Exception {
        System.out.println("starting FMU");

        try (CommunicationChannel channel = getProtocol()) {
            FmiProtocol protocol = new FmiProtocol(channel);
            protocol.run();

        }
    }

    static CommunicationChannel getProtocol() throws Exception {
        String endpoint = System.getProperty("UNIFMU_DISPATCHER_ENDPOINT");
        String communicationChannel = System.getProperty(PROPERTY_COMMUNICATION_CHANNEL, "zmq");

        if (communicationChannel.equals("zmq")) {

            if (endpoint == null)
                throw new Exception("UNIFMU_DISPATCHER_ENDPOINT environment variable not set");

            return new ZMQProtocolChannel(endpoint);
        } else if (communicationChannel.startsWith("custom:")) {
            String className = communicationChannel.substring("custom:".length());
            Class<?> clazz = Class.forName(className);
            return (CommunicationChannel) clazz.getDeclaredConstructor().newInstance();
        } else {
            throw new Exception("Unknown protocol: " + communicationChannel);
        }
    }
}