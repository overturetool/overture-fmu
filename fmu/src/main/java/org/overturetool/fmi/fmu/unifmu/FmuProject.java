package org.overturetool.fmi.fmu.unifmu;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class FmuProject {
    static final String MARKER = ".vdmfmu";
    private static final String FORMAT = "vdmfmu-project=1\n";
    private static final List<String> TEMPLATES = List.of(
            "System.vdmrt", "World.vdmrt", "HardwareInterface.vdmrt", "Controller.vdmrt", "lib/Fmi.vdmrt");

    private FmuProject() {
    }

    static void create(Path directory, boolean libraries, PrintWriter out) throws Exception {
        Path root = directory.toAbsolutePath().normalize();
        if (Files.exists(root)) {
            if (!Files.isDirectory(root)) {
                throw new IOException("Project path is not a directory: " + root);
            }
            try (var entries = Files.list(root)) {
                if (entries.findAny().isPresent()) {
                    throw new IOException("Refusing to overwrite a non-empty directory: " + root);
                }
            }
        }
        Files.createDirectories(root.resolve("model/lib"));
        Files.createDirectories(root.resolve("dist"));
        for (String template : TEMPLATES) {
            Files.write(root.resolve("model").resolve(template),
                    resource("/project-template/" + template), StandardOpenOption.CREATE_NEW);
        }
        if (libraries) {
            libraries(root, false, out);
        }
        scripts(root);
        Files.writeString(root.resolve(".gitignore"), "/dist/\n", StandardOpenOption.CREATE_NEW);
        Files.writeString(root.resolve(MARKER), FORMAT, StandardOpenOption.CREATE_NEW);
        out.println("Created project: " + root);
        out.println("Edit model/HardwareInterface.vdmrt and model/Controller.vdmrt, then run:");
        out.println("  java -jar <vdmfmu-version-jar-with-dependencies.jar> pack \"" + root + "\"");
    }

    static void update(Path directory, boolean libraries, boolean overwrite, PrintWriter out) throws Exception {
        Path root = directory.toAbsolutePath().normalize();
        requireProject(root);
        scripts(root);
        if (libraries) {
            libraries(root, overwrite, out);
        }
        Files.createDirectories(root.resolve("dist"));
        out.println("Updated packing and test scripts in " + root + "; model templates and user resources were preserved.");
    }

    static void requireProject(Path root) throws IOException {
        if (!Files.isRegularFile(root.resolve(MARKER))
                || !Files.readString(root.resolve(MARKER)).equals(FORMAT)
                || !Files.isDirectory(root.resolve("model"))) {
            throw new IOException("Not a supported VDM FMU project: " + root + ". Use init to create one.");
        }
    }

    private static void libraries(Path root, boolean overwrite, PrintWriter out) throws IOException {
        Path directory = root.resolve("model/lib");
        Files.createDirectories(directory);
        for (String library : standardLibraries()) {
            Path file = directory.resolve(library);
            if (Files.exists(file) && !overwrite) {
                out.println("Preserving existing library: " + file);
            } else {
                Files.write(file, resource("/" + library));
                out.println("Installed library: " + file);
            }
        }
    }

    private static List<String> standardLibraries() throws IOException {
        var source = FmuProject.class.getResource("/IO.vdmrt");
        if (source == null) {
            throw new IOException("VDMJ standard-library sources are missing from the classpath.");
        }
        if (source.openConnection() instanceof JarURLConnection connection) {
            connection.setUseCaches(false);
            try (var jar = connection.getJarFile()) {
                return jar.stream().filter(entry -> !entry.isDirectory())
                        .map(java.util.zip.ZipEntry::getName)
                        .filter(name -> !name.contains("/") && name.endsWith(".vdmrt"))
                        .sorted().toList();
            }
        }
        try (var files = Files.list(Path.of(source.toURI()).getParent())) {
            return files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".vdmrt")).sorted().toList();
        } catch (URISyntaxException e) {
            throw new IOException("Cannot locate VDMJ standard-library sources", e);
        }
    }

    private static void scripts(Path root) throws IOException {
        String jar;
        try {
            jar = Path.of(FmuProject.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize().toString();
        } catch (URISyntaxException e) {
            throw new IOException("Cannot locate the exporter JAR for project scripts", e);
        }
        for (String name : List.of("pack.sh", "test.sh")) {
            Path shell = root.resolve(name);
            Files.writeString(shell, new String(resource("/project-template/" + name), StandardCharsets.UTF_8)
                    .replace("@FMU_JAR@", jar.replace("'", "'\"'\"'")));
            if (shell.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                var permissions = Files.getPosixFilePermissions(shell);
                permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(shell, permissions);
            }
        }
        for (String name : List.of("pack.cmd", "test.cmd")) {
            Files.writeString(root.resolve(name),
                    new String(resource("/project-template/" + name), StandardCharsets.UTF_8)
                            .replace("@FMU_JAR@", jar.replace("%", "%%"))
                            .replace("\r\n", "\n").replace("\n", "\r\n"));
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream stream = FmuProject.class.getResourceAsStream(name)) {
            if (stream == null) {
                throw new IOException("Bundled project resource is missing: " + name);
            }
            return stream.readAllBytes();
        }
    }
}
