package org.overturetool.fmi.fmu.unifmu.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.overturetool.fmi.fmu.unifmu.FmuPacker;
import org.intocps.unifmu.communication.CommunicationChannel;
import org.intocps.unifmu.protocol.Fmi2Messages;
import org.intocps.unifmu.model.fmi2.Fmi2Model;
import org.intocps.unifmu.model.fmi2.Fmi2Model.Fmi2Status;
import org.intocps.unifmu.communication.ChannelSelection;
import org.intocps.unifmu.model.fmi2.ModelSelection;
import org.intocps.unifmu.protocol.fmi2.Fmi2Protocol;
import org.intocps.unifmu.protocol.UnifmuHandshake;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@ResourceLock("fmu-channel")
public class FmuChannelTest {
    private String previousChannel;
    private String previousModel;
    private final AtomicReference<Throwable> protocolFailure = new AtomicReference<>();

    @BeforeEach
    void rememberConfiguration() {
        previousChannel = System.getProperty(ChannelSelection.PROPERTY_NAME);
        previousModel = System.getProperty("unifmu.model.impl");
    }

    @AfterEach
    void restoreConfiguration() {
        restoreProperty(ChannelSelection.PROPERTY_NAME, previousChannel);
        restoreProperty("unifmu.model.impl", previousModel);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    public static class LocalChannel implements CommunicationChannel {
        public LocalChannel(String endpoint) {
        }

        public static final int TIMEOUT = 50;
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
            queueIncoming(Fmi2Messages.Fmi2Command.newBuilder()
                    .setFmi2FreeInstance(Fmi2Messages.Fmi2FreeInstance.newBuilder()).build());
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
                return message;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for inbound message", e);
            }
        }

        @Override
        public void send(byte[] data) {
            outgoingMessages.add(data);
        }

        @Override
        public void close() {
        }
    }

    public static class StubFmiModel implements Fmi2Model {
        private Consumer<LogMessage> logHandler;

        public StubFmiModel(Fmi2Messages.Fmi2Instantiate instantiation, Consumer<LogMessage> logHandler) {
            setLogHandler(logHandler);
        }

        @Override
        public void setLogHandler(Consumer<LogMessage> logHandler) {
            this.logHandler = logHandler;
        }

        @Override
        public Fmi2Status fmi2DoStep(double current_time, double step_size, boolean noStepPrior) {
            logHandler.accept(new LogMessage(Fmi2Status.OK, "logAll", "Step completed"));
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2SetReal(Iterable<Integer> references, Iterable<Double> values) {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2SetInteger(Iterable<Integer> references, Iterable<Integer> values) {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2SetBoolean(Iterable<Integer> references, Iterable<Boolean> values) {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2SetString(Iterable<Integer> references, Iterable<String> values) {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2GetValuePair<Double> fmi2GetReal(Iterable<Integer> references) {
            return new Fmi2GetValuePair<>(Fmi2Status.OK, List.of(1.25));
        }

        @Override
        public Fmi2GetValuePair<Integer> fmi2GetInteger(Iterable<Integer> references) {
            return new Fmi2GetValuePair<>(Fmi2Status.OK, List.of(7));
        }

        @Override
        public Fmi2GetValuePair<Boolean> fmi2GetBoolean(Iterable<Integer> references) {
            return new Fmi2GetValuePair<>(Fmi2Status.OK, List.of(Boolean.TRUE));
        }

        @Override
        public Fmi2GetValuePair<String> fmi2GetString(Iterable<Integer> references) {
            return new Fmi2GetValuePair<>(Fmi2Status.OK, List.of("ok"));
        }

        @Override
        public Fmi2Status fmi2EnterInitializationMode() {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2ExitInitializationMode() {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2SetupExperiment(double start_time, Double stop_time, Double tolerance) {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2Reset() {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2Terminate() {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2CancelStep() {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2SerializeFmuStatePair fmi2SerializeFmuState() {
            return new Fmi2SerializeFmuStatePair(Fmi2Status.OK, new byte[]{1, 2, 3});
        }

        @Override
        public Fmi2Status fmi2DeserializeFmuState(byte[] bytes) {
            return Fmi2Status.OK;
        }

        @Override
        public Fmi2Status fmi2SetDebugLogging(Iterable<String> categories, Boolean logging_on) {
            return Fmi2Status.OK;
        }
    }

    private static Fmi2Messages.Fmi2Return assertOkStatusReply() throws Exception {
        Fmi2Messages.Fmi2Return reply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertTrue(reply.hasStatus());
        Assertions.assertEquals(Fmi2Messages.Fmi2Status.FMI2_OK, reply.getStatus().getStatus());
        return reply;
    }

    private void shutdownAndJoin(Thread thread) throws InterruptedException {
        LocalChannel.shutdown();
        thread.join(5_000);
        Assertions.assertFalse(thread.isAlive(), "Protocol thread did not stop");
        Assertions.assertNull(protocolFailure.get(), "Protocol failed");
    }

    private Thread startProtocol() {
        Thread thread = new Thread(() -> {
            try (CommunicationChannel channel = ChannelSelection.connect("test")) {
                new Fmi2Protocol(channel, ModelSelection.fmi2Factory()).run();
            } catch (Throwable failure) {
                protocolFailure.set(failure);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Test
    void runtimeCreatesVdmModelWithLogHandler() throws Exception {
        System.setProperty("unifmu.model.impl", VdmRuntimeFmiModel.class.getName());
        var messages = new ArrayList<Fmi2Model.LogMessage>();
        var instantiation = Fmi2Messages.Fmi2Instantiate.newBuilder()
                .setInstanceName("runtime-model")
                .setFmuResourceLocation(Paths.get("src", "test", "resources", "model").toUri().toString())
                .build();
        var model = ModelSelection.fmi2Factory().create(instantiation, messages::add);
        var vdmModel = Assertions.assertInstanceOf(VdmRuntimeFmiModel.class, model);
        vdmModel.fmiLog(VdmFmiModel.LogCategory.LogAll, Fmi2Status.OK, "Runtime callback");
        Assertions.assertEquals(List.of(new Fmi2Model.LogMessage(
                Fmi2Status.OK, "logAll", "Runtime callback")), messages);
    }

    @Test
    void testChannelBridgesQueuedInputAndCapturedOutput() throws Exception {
        LocalChannel.reset();
        System.setProperty(ChannelSelection.PROPERTY_NAME, LocalChannel.class.getName());
        System.setProperty("unifmu.model.impl", StubFmiModel.class.getName());

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

        Thread mainThread = startProtocol();

        UnifmuHandshake.HandshakeReply handshakeReply =
                UnifmuHandshake.HandshakeReply.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertEquals(UnifmuHandshake.HandshakeStatus.OK, handshakeReply.getStatus());

        Fmi2Messages.Fmi2Return instantiateReply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertTrue(instantiateReply.hasEmpty());

        Fmi2Messages.Fmi2Return getRealReply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertTrue(getRealReply.hasGetReal());
        Assertions.assertEquals(Fmi2Messages.Fmi2Status.FMI2_OK, getRealReply.getGetReal().getStatus());
        Assertions.assertEquals(List.of(1.25), getRealReply.getGetReal().getValuesList());

        LocalChannel.queueIncoming(Fmi2Messages.Fmi2Command.newBuilder()
                .setFmi2DoStep(Fmi2Messages.Fmi2DoStep.newBuilder().setStepSize(0.01)).build());
        Fmi2Messages.Fmi2Return logReply = Fmi2Messages.Fmi2Return.parseFrom(LocalChannel.awaitOutgoing());
        Assertions.assertTrue(logReply.hasLog());
        Assertions.assertEquals("logAll", logReply.getLog().getCategory());
        Assertions.assertEquals("Step completed", logReply.getLog().getLogMessage());
        LocalChannel.queueIncoming(Fmi2Messages.Fmi2Command.newBuilder()
                .setFmi2CallbackContinue(Fmi2Messages.Fmi2CallbackContinue.newBuilder()).build());
        assertOkStatusReply();

        shutdownAndJoin(mainThread);
    }


    @Test
    void testChannelActualModel() throws Exception {
        LocalChannel.reset();
        System.setProperty(ChannelSelection.PROPERTY_NAME, LocalChannel.class.getName());
        System.setProperty("unifmu.model.impl", VdmRuntimeFmiModel.class.getName());

        Path vdmModelPath = Paths.get("src", "test", "resources", "model");
        LocalChannel.queueIncoming(
                Fmi2Messages.Fmi2Command.newBuilder()
                        .setFmi2Instantiate(Fmi2Messages.Fmi2Instantiate.newBuilder().setFmuResourceLocation(vdmModelPath.toUri().toString()).build())
                        .build()

        );


        Thread mainThread = startProtocol();

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
