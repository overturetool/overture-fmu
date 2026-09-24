package org.overturetool.fmi.fmu.unifmu.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.overturetool.fmi.fmu.unifmu.FmuPacker;
import org.overturetool.fmi.fmu.unifmu.protocol.CommunicationChannel;
import org.overturetool.fmi.fmu.unifmu.protocol.Fmi2Messages;
import org.overturetool.fmi.fmu.unifmu.protocol.FmiModel;
import org.overturetool.fmi.fmu.unifmu.protocol.FmiProtocol;
import org.overturetool.fmi.fmu.unifmu.protocol.Main;
import org.overturetool.fmi.fmu.unifmu.protocol.UnifmuHandshake;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@ResourceLock("fmu-channel")
public class FmuChannelTest {
    public static class LocalChannel implements CommunicationChannel {
        public static final int TIMEOUT = 50;
        private static final byte[] STOP = new byte[0];
        private static final BlockingQueue<byte[]> incomingMessages = new LinkedBlockingQueue<>();
        private static final BlockingQueue<byte[]> outgoingMessages = new LinkedBlockingQueue<>();

        static void reset() {
            incomingMessages.clear();
            outgoingMessages.clear();
        }


        static void queueIncoming(Fmi2Messages.Fmi2Command cmd) {
            incomingMessages.add(cmd.toByteArray());
        }

        static void queueIncoming(byte[] data) {
            incomingMessages.add(data);
        }

        static void shutdown() {
            incomingMessages.add(STOP);
        }

        static byte[] awaitOutgoing() throws InterruptedException {
            byte[] reply = outgoingMessages.poll(TIMEOUT, TimeUnit.SECONDS);
            Assertions.assertNotNull(reply, "Timed out waiting for protocol reply");
            return reply;
        }

        @Override
        public byte[] receive() {
            try {
                byte[] message = incomingMessages.poll(TIMEOUT, TimeUnit.SECONDS);
                if (message == null) {
                    throw new AssertionError("Timed out waiting for queued inbound message");
                }
                if (message == STOP) {
                    throw new AssertionError("Stop requested");
                }
                return message;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for inbound message", e);
            }
        }

        @Override
        public void send(byte[] data, int flags) {
            outgoingMessages.add(data);
        }

        @Override
        public void close() {
        }
    }

    public static class StubFmiModel implements FmiModel {
        public StubFmiModel(String modelInstanceName, URI resourceLocation, Boolean visible, Boolean loggingOn) {
        }
            @Override
        public void setLogCallback(org.overturetool.fmi.fmu.unifmu.protocol.FmiLogCallback fmiLogCallback) {
        }

        @Override
        public void log(String message, FmiProtocol.Fmi2Status status, String category) {
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2DoStep(double current_time, double step_size, boolean noStepPrior) {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2SetReal(Iterable<Integer> references, Iterable<Double> values) {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2SetInteger(Iterable<Integer> references, Iterable<Integer> values) {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2SetBoolean(Iterable<Integer> references, Iterable<Boolean> values) {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2SetString(Iterable<Integer> references, Iterable<String> values) {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public Fmi2GetValuePair<Double> fmi2GetReal(Iterable<Integer> references) {
            return new Fmi2GetValuePair<>(FmiProtocol.Fmi2Status.OK, List.of(1.25));
        }

        @Override
        public Fmi2GetValuePair<Integer> fmi2GetInteger(Iterable<Integer> references) {
            return new Fmi2GetValuePair<>(FmiProtocol.Fmi2Status.OK, List.of(7));
        }

        @Override
        public Fmi2GetValuePair<Boolean> fmi2GetBoolean(Iterable<Integer> references) {
            return new Fmi2GetValuePair<>(FmiProtocol.Fmi2Status.OK, List.of(Boolean.TRUE));
        }

        @Override
        public Fmi2GetValuePair<String> fmi2GetString(Iterable<Integer> references) {
            return new Fmi2GetValuePair<>(FmiProtocol.Fmi2Status.OK, List.of("ok"));
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2EnterInitializationMode() {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2ExitInitializationMode() {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2SetupExperiment(double start_time, Double stop_time, Double tolerance) {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2Reset() {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2Terminate() {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2CancelStep() {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public Fmi2SerializeFmuStatePair fmi2SerializeFmuState() {
            return new Fmi2SerializeFmuStatePair(FmiProtocol.Fmi2Status.OK, new byte[]{1, 2, 3});
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2DeserializeFmuState(byte[] bytes) {
            return FmiProtocol.Fmi2Status.OK;
        }

        @Override
        public FmiProtocol.Fmi2Status fmi2SetDebugLogging(Iterable<String> categories, Boolean logging_on) {
            return FmiProtocol.Fmi2Status.OK;
        }
    }

    private static Fmi2Messages.Fmi2Return assertOkStatusReply() throws Exception {
        Fmi2Messages.Fmi2Return reply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertTrue(reply.hasStatus());
        Assertions.assertEquals(Fmi2Messages.Fmi2Status.FMI2_OK, reply.getStatus().getStatus());
        return reply;
    }

    private static void shutdownAndJoin(Thread thread) throws InterruptedException {
        LocalChannel.shutdown();
        thread.join(5_000);
        Assertions.assertFalse(thread.isAlive(), "Protocol thread did not stop");
    }

    @Test
    void testChannelBridgesQueuedInputAndCapturedOutput() throws Exception {
        LocalChannel.reset();
        System.setProperty(Main.PROPERTY_COMMUNICATION_CHANNEL, "custom:" + LocalChannel.class.getName());
        System.setProperty("fmu.model.impl", StubFmiModel.class.getName());

        LocalChannel.queueIncoming(
                Fmi2Messages.Fmi2Command.newBuilder()
                        .setFmi2Instantiate(Fmi2Messages.Fmi2Instantiate.newBuilder().build())
                        .build()
                        .toByteArray()
        );
        LocalChannel.queueIncoming(
                Fmi2Messages.Fmi2Command.newBuilder()
                        .setFmi2GetReal(
                                Fmi2Messages.Fmi2GetReal.newBuilder()
                                        .addReferences(42)
                                        .build()
                        )
                        .build()
                        .toByteArray()
        );

        Thread mainThread = new Thread(() -> {
            try {
                Main.main(new String[]{});
            } catch (Throwable ignored) {
            }
        });
        mainThread.setDaemon(true);
        mainThread.start();

        UnifmuHandshake.HandshakeReply handshakeReply =
                UnifmuHandshake.HandshakeReply.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertEquals(UnifmuHandshake.HandshakeStatus.OK, handshakeReply.getStatus());

        Fmi2Messages.Fmi2Return instantiateReply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertTrue(instantiateReply.hasEmpty());

        Fmi2Messages.Fmi2Return getRealReply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertTrue(getRealReply.hasGetReal());
        Assertions.assertEquals(Fmi2Messages.Fmi2Status.FMI2_OK, getRealReply.getGetReal().getStatus());
        Assertions.assertEquals(List.of(1.25), getRealReply.getGetReal().getValuesList());

        shutdownAndJoin(mainThread);
    }


    @Test
    void testChannelActualModel() throws Exception {
        LocalChannel.reset();
        System.setProperty(Main.PROPERTY_COMMUNICATION_CHANNEL, "custom:" + LocalChannel.class.getName());
        System.setProperty("fmu.model.impl", VdmRuntimeFmiModel.class.getName());

        Path vdmModelPath = Paths.get("src", "test", "resources", "model");
        LocalChannel.queueIncoming(
                Fmi2Messages.Fmi2Command.newBuilder()
                        .setFmi2Instantiate(Fmi2Messages.Fmi2Instantiate.newBuilder().setFmuResourceLocation(vdmModelPath.toUri().toString()).build())
                        .build()

        );


        Thread mainThread = new Thread(() -> {
            try {
                Main.main(new String[]{});
            } catch (Throwable ignored) {
                ignored.printStackTrace();
            }
        });
        mainThread.setDaemon(true);
        mainThread.start();

        UnifmuHandshake.HandshakeReply handshakeReply =
                UnifmuHandshake.HandshakeReply.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertEquals(UnifmuHandshake.HandshakeStatus.OK, handshakeReply.getStatus());

        Fmi2Messages.Fmi2Return instantiateReply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertTrue(instantiateReply.hasEmpty());

        //ok not its initialized
        var scalars = FmuPacker.getScalarVariables(VdmrtDiscoveryUtil.discover(vdmModelPath));
        for (var scalar : scalars) {
            if (scalar.causality() == FmuPacker.Causality.parameter) {
                var value = 0.0;
                if (scalar.name().contains("minlevel")) {
                    value = 1d;

                } else if (scalar.name().contains("maxlevel")) {
                    value = 2d;
                }
                LocalChannel.queueIncoming( Fmi2Messages.Fmi2Command.newBuilder()
                        .setFmi2SetReal(
                                Fmi2Messages.Fmi2SetReal.newBuilder()
                                        .addReferences(scalar.valueReference())
                                        .addValues(value)
                                        .build()
                        )
                        .build());

                assertOkStatusReply();
            }
        }

        {
            LocalChannel.queueIncoming( Fmi2Messages.Fmi2Command.newBuilder().setFmi2SetupExperiment(Fmi2Messages.Fmi2SetupExperiment.newBuilder().setStartTime(0.0).setStopTime(10d).build()).build());
            assertOkStatusReply();
        }
        LocalChannel.queueIncoming( Fmi2Messages.Fmi2Command.newBuilder().setFmi2EnterInitializationMode(Fmi2Messages.Fmi2EnterInitializationMode.newBuilder().build()).build());
        assertOkStatusReply();

        LocalChannel.queueIncoming( Fmi2Messages.Fmi2Command.newBuilder().setFmi2ExitInitializationMode(Fmi2Messages.Fmi2ExitInitializationMode.newBuilder().build()).build());
        assertOkStatusReply();

        var time = 0d;
        var stepSize = 0.01d;
        while (time < 10d) {
            time += stepSize;
            var cmd = Fmi2Messages.Fmi2Command.newBuilder().setFmi2DoStep(Fmi2Messages.Fmi2DoStep.newBuilder().setCurrentTime(time).setStepSize(stepSize).build()).build();
            LocalChannel.queueIncoming(cmd);
            assertOkStatusReply();
        }
        shutdownAndJoin(mainThread);
//        Fmi2Messages.Fmi2Return getRealReply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
//        Assertions.assertTrue(getRealReply.hasGetReal());
//        Assertions.assertEquals(Fmi2Messages.Fmi2Status.FMI2_OK, getRealReply.getGetReal().getStatus());
//        Assertions.assertEquals(List.of(1.25), getRealReply.getGetReal().getValuesList());
    }
}
