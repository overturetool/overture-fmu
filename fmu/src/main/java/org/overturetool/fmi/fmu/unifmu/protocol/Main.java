package org.overturetool.fmi.fmu.unifmu.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    final static Logger logger = LoggerFactory.getLogger(Main.class);
    public static final String PROPERTY_COMMUNICATION_CHANNEL = "communicationChannel".toLowerCase();

    public static void main(String[] args) throws Exception {
        logger.debug("Starting FMU backend");

        try {
            try (CommunicationChannel channel = getProtocol()) {
                FmiProtocol protocol = new FmiProtocol(channel);
                protocol.run();
            }
        } catch (Exception e) {
            logger.error("FMU backend failed", e);
            throw e;
        }
    }

    static CommunicationChannel getProtocol() throws Exception {
        String endpoint = System.getProperty("UNIFMU_DISPATCHER_ENDPOINT");
        String endpointSource = "JVM property";
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = System.getenv("UNIFMU_DISPATCHER_ENDPOINT");
            endpointSource = "environment variable";
        }
        String communicationChannel = System.getProperty(PROPERTY_COMMUNICATION_CHANNEL, "zmq");
        logger.debug("Selected communication channel: {}", communicationChannel);

        if (communicationChannel.equals("zmq")) {
            if (endpoint == null || endpoint.isBlank()) {
                logger.error("UNIFMU_DISPATCHER_ENDPOINT is not set as a JVM property or environment variable");
                throw new Exception("UNIFMU_DISPATCHER_ENDPOINT is not set as a JVM property or environment variable");
            }

            logger.debug("Resolved ZeroMQ dispatcher endpoint from {}: {}", endpointSource, endpoint);
            return new ZMQProtocolChannel(endpoint);
        } else if (communicationChannel.startsWith("custom:")) {
            String className = communicationChannel.substring("custom:".length());
            logger.debug("Loading custom communication channel: {}", className);
            Class<?> clazz = Class.forName(className);
            return (CommunicationChannel) clazz.getDeclaredConstructor().newInstance();
        } else {
            logger.error("Unknown communication channel: {}", communicationChannel);
            throw new Exception("Unknown protocol: " + communicationChannel);
        }
    }
}