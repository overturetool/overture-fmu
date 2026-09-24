package org.overturetool.fmi.fmu.unifmu;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
            Assertions.assertNotNull(zipFile.getEntry("resources/"));
            Assertions.assertNotNull(zipFile.getEntry("resources/launch.toml"));
            Assertions.assertNotNull(zipFile.getEntry("resources/log4j2.xml"));
            Assertions.assertNotNull(zipFile.getEntry("resources/HardwareInterface.vdmrt"));
            Assertions.assertNotNull(zipFile.getEntry("resources/System.vdmrt"));
            Assertions.assertNotNull(zipFile.getEntry("resources/vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar"));

            String modelDescription = new String(zipFile.getInputStream(zipFile.getEntry("modelDescription.xml")).readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertTrue(modelDescription.contains("modelName=\"test-model\""));
            Assertions.assertTrue(modelDescription.contains("modelIdentifier=\"test-model\""));

            String launchToml = new String(zipFile.getInputStream(zipFile.getEntry("resources/launch.toml")).readAllBytes(), StandardCharsets.UTF_8);
            Assertions.assertTrue(launchToml.contains("-Dfmu.model.impl=org.overturetool.fmi.fmu.unifmu.model.VdmRuntimeFmiModel"));
            Assertions.assertTrue(launchToml.contains("org.overturetool.fmi.fmu.unifmu.protocol.Main"));
        }
    }
}
