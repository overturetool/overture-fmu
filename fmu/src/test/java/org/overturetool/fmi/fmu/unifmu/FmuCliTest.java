package org.overturetool.fmi.fmu.unifmu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.io.File;
import org.overturetool.fmi.fmu.unifmu.model.VdmRuntimeFmiModel;
import org.intocps.unifmu.model.fmi2.Fmi2Model.Fmi2Status;

import static org.junit.jupiter.api.Assertions.*;

class FmuCliTest {
    @TempDir
    Path temporary;
    private final StringWriter output = new StringWriter();

    private int execute(String... args) {
        return new CommandLine(new FmuCli())
                .setOut(new PrintWriter(output, true))
                .setErr(new PrintWriter(output, true)).execute(args);
    }

    @Test
    void helpHighlightsStartingAndPackingNotDebugging() {
        assertEquals(0, execute("--help"));
        assertTrue(output.toString().contains("init"));
        assertTrue(output.toString().contains("pack"));
        assertTrue(output.toString().contains("update"));
        output.getBuffer().setLength(0);
        assertEquals(0, execute("pack", "--help"));
        assertTrue(output.toString().contains("--output"));
        assertTrue(output.toString().contains("Developer option"));
    }

    @Test
    void createsAndPacksAllTypesAndPreservesModelResources() throws Exception {
        Path project = temporary.resolve("starter");
        assertEquals(0, execute("init", project.toString()), output.toString());
        for (String file : List.of(".vdmfmu", ".gitignore", "pack.sh", "pack.cmd", "test.sh", "test.cmd", "model/System.vdmrt",
                "model/World.vdmrt", "model/HardwareInterface.vdmrt", "model/lib/Fmi.vdmrt")) {
            assertTrue(Files.isRegularFile(project.resolve(file)), file);
        }
        assertFalse(Files.exists(project.resolve("model/lib/IO.vdmrt")));
        assertEquals("/dist/\n", Files.readString(project.resolve(".gitignore")));
        String exporter = Path.of(FmuPacker.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize().toString();
        assertTrue(Files.readString(project.resolve("pack.sh"))
                .contains("JAR='" + exporter.replace("'", "'\"'\"'") + "'"));
        assertTrue(Files.readString(project.resolve("pack.cmd"))
                .contains("set \"FMU_JAR=" + exporter.replace("%", "%%") + "\""));
        assertFalse(Files.readString(project.resolve("pack.sh")).contains("@FMU_JAR@"));
        assertFalse(Files.readString(project.resolve("pack.cmd")).contains("@FMU_JAR@"));
        Files.createDirectories(project.resolve("model/data"));
        Files.writeString(project.resolve("model/data/config.json"), "{\"gain\":2}");
        Files.writeString(project.resolve("model/data/helper.sh"), "user model resource");
        assertEquals(0, execute("pack", project.toString()), output.toString());
        try (ZipFile zip = new ZipFile(project.resolve("dist/starter.fmu").toFile())) {
            assertNotNull(zip.getEntry("resources/data/config.json"));
            assertNotNull(zip.getEntry("resources/data/helper.sh"));
            assertNull(zip.getEntry("resources/pack.sh"));
            assertNull(zip.getEntry("resources/pack.cmd"));
            assertNull(zip.getEntry("resources/test.sh"));
            assertNull(zip.getEntry("resources/test.cmd"));
            assertNull(zip.getEntry("resources/.vdmfmu"));
            String xml = new String(zip.getInputStream(zip.getEntry("modelDescription.xml")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            for (String type : List.of("Real", "Integer", "Boolean", "String")) {
                assertTrue(xml.contains("<" + type), xml);
            }
        }
    }

    @Test
    void updatesScriptsAndLibrariesWithoutOverwritingUserModels() throws Exception {
        Path project = temporary.resolve("with-libraries");
        assertEquals(0, execute("init", project.toString(), "--with-stdlib"), output.toString());
        for (String library : List.of("IO", "CSV", "MATH", "VDMUtil", "VDMUnit")) {
            assertTrue(Files.size(project.resolve("model/lib/" + library + ".vdmrt")) > 0);
        }
        assertEquals(0, execute("pack", project.toString()), output.toString());
        Files.writeString(project.resolve("model/HardwareInterface.vdmrt"), "user model");
        Files.writeString(project.resolve("model/lib/IO.vdmrt"), "edited library");
        Files.writeString(project.resolve("pack.sh"), "old script");
        Files.writeString(project.resolve("test.sh"), "old test script");
        Files.delete(project.resolve("model/lib/CSV.vdmrt"));
        assertEquals(0, execute("update", project.toString(), "--with-stdlib"), output.toString());
        assertEquals("user model", Files.readString(project.resolve("model/HardwareInterface.vdmrt")));
        assertEquals("edited library", Files.readString(project.resolve("model/lib/IO.vdmrt")));
        assertTrue(Files.readString(project.resolve("pack.sh")).startsWith("#!/bin/sh"));
        assertTrue(Files.readString(project.resolve("test.sh")).contains("fmpy"));
        assertTrue(Files.isRegularFile(project.resolve("model/lib/CSV.vdmrt")));
        assertEquals(0, execute("update", project.toString(), "--with-stdlib", "--overwrite-libraries"));
        assertNotEquals("edited library", Files.readString(project.resolve("model/lib/IO.vdmrt")));
        assertEquals("user model", Files.readString(project.resolve("model/HardwareInterface.vdmrt")));
    }

    @Test
    void rejectsOverwriteInvalidProjectsAndInvalidPackingOptions() throws Exception {
        Path project = temporary.resolve("existing");
        assertEquals(0, execute("init", project.toString()));
        assertNotEquals(0, execute("init", project.toString()));
        assertNotEquals(0, execute("update", temporary.toString()));
        assertNotEquals(0, execute("update", project.toString(), "--overwrite-libraries"));
        assertNotEquals(0, execute("pack", project.toString(), "--debug=0"));
        assertNotEquals(0, execute("pack", project.toString(), "--name", "../escape"));
        assertNotEquals(0, execute("pack", project.toString(), "--output", project.resolve("model/out").toString()));
        assertFalse(Files.exists(project.resolve("model/out")));
        assertNotEquals(0, execute("pack", project.toString(), "--unknown-option"));
    }

    @Test
    void preservesResourceCollisionsByFailingAndReportsInvalidSpecifications() throws Exception {
        Path project = temporary.resolve("invalid");
        assertEquals(0, execute("init", project.toString()));
        Files.writeString(project.resolve("model/launch.toml"), "user data");
        Files.writeString(project.resolve("dist/invalid.fmu"), "previous FMU");
        assertNotEquals(0, execute("pack", project.toString()));
        assertTrue(output.toString().contains("reserved backend path"));
        assertEquals("previous FMU", Files.readString(project.resolve("dist/invalid.fmu")));
        Files.delete(project.resolve("model/launch.toml"));
        Files.writeString(project.resolve("model/Controller.vdmrt"), "not valid VDM");
        assertNotEquals(0, execute("pack", project.toString()));
    }

    @Test
    void supportsNamedOutputAndDeveloperDebugOption() throws Exception {
        Path project = temporary.resolve("debug-project");
        assertEquals(0, execute("init", project.toString()));
        assertEquals(0, execute("pack", project.toString(), "-n", "custom", "-o",
                temporary.resolve("output").toString(), "--debug"), output.toString());
        assertTrue(output.toString().contains("publish it with -p 5005:5005"));
        try (ZipFile zip = new ZipFile(temporary.resolve("output/custom.fmu").toFile())) {
            String launch = new String(zip.getInputStream(zip.getEntry("resources/launch.toml")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(launch.contains("suspend=y,address=*:5005"));
        }
    }

    @Test
    void starterSimulatesEveryPortType() throws Exception {
        Path project = temporary.resolve("simulation");
        assertEquals(0, execute("init", project.toString()));
        List<Path> specifications;
        try (var files = Files.walk(project.resolve("model"))) {
            specifications = files.filter(path -> path.toString().endsWith(".vdmrt")).sorted().toList();
        }
        var variables = FmuPacker.getScalarVariables(specifications);
        var references = variables.stream().collect(java.util.stream.Collectors.toMap(
                FmuPacker.ScalarVariable::name, FmuPacker.ScalarVariable::valueReference));
        var model = new VdmRuntimeFmiModel("starter", project.resolve("model").toUri(), false, false);
        assertEquals(Fmi2Status.OK, model.fmi2SetupExperiment(0, 3.0, null));
        assertEquals(Fmi2Status.OK, model.fmi2SetReal(List.of(references.get("System.hwi.realInput")), List.of(1.5)));
        assertEquals(Fmi2Status.OK, model.fmi2SetInteger(List.of(references.get("System.hwi.integerInput")), List.of(7)));
        assertEquals(Fmi2Status.OK, model.fmi2SetBoolean(List.of(references.get("System.hwi.booleanInput")), List.of(true)));
        assertEquals(Fmi2Status.OK, model.fmi2SetString(List.of(references.get("System.hwi.stringInput")), List.of("test")));
        assertEquals(List.of(0), model.fmi2GetInteger(List.of(references.get("System.hwi.integerOutput"))).values);
        assertEquals(List.of(""), model.fmi2GetString(List.of(references.get("System.hwi.stringOutput"))).values);
        assertEquals(Fmi2Status.OK, model.fmi2EnterInitializationMode());
        assertEquals(Fmi2Status.OK, model.fmi2ExitInitializationMode());
        assertEquals(Fmi2Status.OK, model.fmi2DoStep(0, 0.5, false));
        double first = model.fmi2GetReal(List.of(references.get("System.hwi.realOutput"))).values.getFirst();
        assertTrue(first > 1.9 && first < 2.1, "Triangle at 0.5 s: " + first);
        assertEquals(List.of(7), model.fmi2GetInteger(List.of(references.get("System.hwi.integerOutput"))).values);
        assertEquals(List.of(true), model.fmi2GetBoolean(List.of(references.get("System.hwi.booleanOutput"))).values);
        assertEquals(List.of("test low"), model.fmi2GetString(List.of(references.get("System.hwi.stringOutput"))).values);
        assertEquals(Fmi2Status.OK, model.fmi2DoStep(0.5, 1, false));
        double second = model.fmi2GetReal(List.of(references.get("System.hwi.realOutput"))).values.getFirst();
        assertTrue(second > 2.9 && second < 3.1, "Triangle at 1.5 s: " + second);
        assertEquals(List.of(8), model.fmi2GetInteger(List.of(references.get("System.hwi.integerOutput"))).values);
        assertEquals(List.of(false), model.fmi2GetBoolean(List.of(references.get("System.hwi.booleanOutput"))).values);
        assertEquals(List.of("test high"), model.fmi2GetString(List.of(references.get("System.hwi.stringOutput"))).values);
        assertEquals(Fmi2Status.OK, model.fmi2DoStep(1.5, 1, false));
        double third = model.fmi2GetReal(List.of(references.get("System.hwi.realOutput"))).values.getFirst();
        assertTrue(third > 2.9 && third < 3.1, "Triangle at 2.5 s: " + third);
        assertEquals(List.of(9), model.fmi2GetInteger(List.of(references.get("System.hwi.integerOutput"))).values);
        assertEquals(List.of(true), model.fmi2GetBoolean(List.of(references.get("System.hwi.booleanOutput"))).values);
        assertEquals(List.of("test low"), model.fmi2GetString(List.of(references.get("System.hwi.stringOutput"))).values);
    }

    @Test
    void generatedPlatformHelperRunsFromAnotherDirectoryWithSpacesInPaths() throws Exception {
        Path project = temporary.resolve("project with spaces");
        assertEquals(0, execute("init", project.toString()));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, FmuPacker.class.getName());
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(path -> Path.of(path).toAbsolutePath().toUri().toASCIIString())
                .collect(Collectors.joining(" "));
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classpath);
        Path launcher = temporary.resolve("test launcher.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(launcher), manifest)) {
            jar.flush();
        }
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        ProcessBuilder builder = windows
                ? new ProcessBuilder("cmd.exe", "/d", "/c", "call", project.resolve("pack.cmd").toString(),
                        launcher.toString(), "--name", "helper-test")
                : new ProcessBuilder("sh", project.resolve("pack.sh").toString(),
                        launcher.toString(), "--name", "helper-test");
        Path log = temporary.resolve("helper.log");
        Process process = builder.directory(temporary.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertTrue(finished, "Packing helper timed out");
        assertEquals(0, process.exitValue(), Files.readString(log));
        assertTrue(Files.isRegularFile(project.resolve("dist/helper-test.fmu")), Files.readString(log));
    }
}
