package org.overturetool.fmi.fmu.unifmu;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.overturetool.fmi.fmu.unifmu.model.VdmFmiModel;
import org.intocps.unifmu.model.fmi2.Fmi2Model.Fmi2Status;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

public class FmuPackerTest {

    @Test
    void mainCreatesFmuInTargetFromResourceModel() throws Exception {
        Path outputDir = Paths.get("target", "FmuPackerTest", "mainCreatesFmuInTargetFromResourceModel");
        Files.createDirectories(outputDir);

        Path archive = outputDir.resolve("test-model.fmu");
        Files.deleteIfExists(archive);

        FmuPacker.main(new String[]{
                Paths.get("src", "test", "resources", "model").toString(),
                outputDir.toString(),
                "test-model"
        });

        Assertions.assertTrue(Files.exists(archive));

        try (ZipFile zipFile = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            Assertions.assertNotNull(zipFile.getEntry("modelDescription.xml"));
            Assertions.assertNotNull(zipFile.getEntry("binaries/"));
            Assertions.assertNotNull(zipFile.getEntry("binaries/linux64/test-model.so"));
            Assertions.assertNotNull(zipFile.getEntry("binaries/aarch64-linux/test-model.so"));
            Assertions.assertNotNull(zipFile.getEntry("binaries/darwin64/test-model.dylib"));
            Assertions.assertNotNull(zipFile.getEntry("binaries/win64/test-model.dll"));
            Assertions.assertNull(zipFile.getEntry("binaries/linux64/unifmu.so"));
            Assertions.assertNotNull(zipFile.getEntry("resources/"));
            Assertions.assertNotNull(zipFile.getEntry("resources/launch.toml"));
            Assertions.assertNotNull(zipFile.getEntry("resources/log4j2.xml"));
            Assertions.assertNotNull(zipFile.getEntry("resources/HardwareInterface.vdmrt"));
            Assertions.assertNotNull(zipFile.getEntry("resources/System.vdmrt"));
            boolean usesFatJar = zipFile.getEntry("resources/vdmfmu-jar-with-dependencies.jar") != null;
            if (!usesFatJar) {
                Assertions.assertNotNull(zipFile.getEntry("resources/classes/org/overturetool/fmi/fmu/unifmu/FmuPacker.class"));
                Assertions.assertTrue(zipFile.stream().anyMatch(entry ->
                        entry.getName().startsWith("resources/lib/unifmu-java-runtime-")
                                && entry.getName().endsWith(".jar")));
            }

            String modelDescription = new String(zipFile.getInputStream(zipFile.getEntry("modelDescription.xml")).readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertTrue(modelDescription.contains("modelName=\"test-model\""));
            Assertions.assertTrue(modelDescription.contains("modelIdentifier=\"test-model\""));
            for (var category : VdmFmiModel.LogCategory.values()) {
                Assertions.assertTrue(modelDescription.contains(
                        "<Category name=\"" + category.categoryName() + "\""),
                        "Missing log category: " + category.categoryName());
            }
            for (var status : Fmi2Status.values()) {
                if (status != Fmi2Status.OK) {
                    Assertions.assertTrue(modelDescription.contains(
                            "<Category name=\"logStatus" + status.name() + "\""),
                            "Missing FMI status log category: " + status);
                }
            }
            Assertions.assertTrue(modelDescription.contains(
                    "<Category name=\"logUnifmuMessages\" description="));

            String launchToml = new String(zipFile.getInputStream(zipFile.getEntry("resources/launch.toml")).readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertTrue(launchToml.contains("-Dunifmu.model.impl=org.overturetool.fmi.fmu.unifmu.model.VdmRuntimeFmiModel"));
            Assertions.assertTrue(launchToml.contains("org.intocps.unifmu.Main"));
            Assertions.assertTrue(launchToml.contains("-Dunifmu.launch.mode=local"));
            Assertions.assertTrue(launchToml.contains("-Dunifmu.fmi.version=fmi2"));
            Assertions.assertTrue(launchToml.contains("-Dlog4j.configurationFile=log4j2.xml"));
            if (usesFatJar) {
                Assertions.assertTrue(launchToml.contains("\"-cp\", \"vdmfmu-jar-with-dependencies.jar\""));
            } else {
                Assertions.assertTrue(launchToml.contains("\"-cp\", \"classes/:lib/*\""));
                Assertions.assertTrue(launchToml.contains("\"-cp\", \"classes/;lib/*\""));
            }
            Assertions.assertFalse(launchToml.contains("resources/log4j2.xml"));
            Assertions.assertFalse(launchToml.contains("resources/vdmfmu-jar-with-dependencies.jar"));
            Assertions.assertFalse(launchToml.contains("-agentlib:jdwp"));
        }
    }

    @Test
    void debugArgumentAddsSuspendingJvmDebuggerAndPrintsConnectionDetails() throws Exception {
        Path outputDir = Paths.get("target", "FmuPackerTest", "debugArgumentAddsSuspendingJvmDebugger");
        Files.createDirectories(outputDir);

        Path archive = outputDir.resolve("debug-model.fmu");
        Files.deleteIfExists(archive);
        ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try (PrintStream capturedOut = new PrintStream(consoleOutput, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            FmuPacker.main(new String[]{
                    Paths.get("src", "test", "resources", "model").toString(),
                    outputDir.toString(),
                    "debug-model",
                    "--debug=5007"
            });
        } finally {
            System.setOut(originalOut);
        }

        Assertions.assertTrue(consoleOutput.toString(StandardCharsets.UTF_8)
                .contains("publish it with -p 5007:5007"));

        try (ZipFile zipFile = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            String launchToml = new String(
                    zipFile.getInputStream(zipFile.getEntry("resources/launch.toml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            Assertions.assertTrue(launchToml.contains(
                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5007"));
        }
    }
}
