package org.overturetool.fmi.fmu.unifmu.model;

import com.fujitsu.vdmj.lex.Dialect;
import org.overturetool.fmi.fmu.unifmu.protocol.FmiProtocol;

import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class VdmrtDiscoveryUtil {

    private static final FilenameFilter VDM_RT_FILTER = Dialect.VDM_RT.getFilter();

    @FunctionalInterface
    interface DiscoveryLog {
        void log(String message, FmiProtocol.Fmi2Status status, String category);
    }

    private VdmrtDiscoveryUtil() {
    }

    static List<Path> discoverForClass(Class<?> modelClass, DiscoveryLog logger) {
        Path loadOrigin = resolveClassLoadOrigin(modelClass);
        if (loadOrigin == null) {
            log(logger, "Unable to resolve class load origin for " + modelClass.getSimpleName(), FmiProtocol.Fmi2Status.Warning);
            return List.of();
        }

        try {
            List<Path> discovered = discover(loadOrigin);
            log(logger, "Discovered " + discovered.size() + " .vdmrt file(s) from: " + loadOrigin, FmiProtocol.Fmi2Status.OK);
            return discovered;
        } catch (IllegalArgumentException e) {
            log(logger, e.getMessage(), FmiProtocol.Fmi2Status.Warning);
            return List.of();
        } catch (IOException e) {
            log(logger, "Failed to discover .vdmrt files from " + loadOrigin + ": " + e.getMessage(), FmiProtocol.Fmi2Status.Error);
            return List.of();
        }
    }

    private static Path resolveClassLoadOrigin(Class<?> modelClass) {
        CodeSource codeSource = modelClass.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return null;
        }

        try {
            return Paths.get(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException e) {
            return Paths.get(codeSource.getLocation().getPath()).toAbsolutePath().normalize();
        }
    }

    public static List<Path> discover(Path loadOrigin) throws IOException {
        if (Files.isDirectory(loadOrigin)) {
            return discoverFromDirectory(loadOrigin);
        }

        String fileName = loadOrigin.getFileName() == null ? "" : loadOrigin.getFileName().toString().toLowerCase(Locale.ROOT);
        if (Files.isRegularFile(loadOrigin) && fileName.endsWith(".jar")) {
            return discoverFromJar(loadOrigin);
        }

        throw new IllegalArgumentException("Unsupported class load origin type: " + loadOrigin);
    }

    private static List<Path> discoverFromDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null
                            && matchesVdmRt(path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.naturalOrder())
                    .toList();

        }
    }

    private static List<Path> discoverFromJar(Path jarPath) throws IOException {
        List<Path> files = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && matchesVdmRt(entry.getName())) {
                    files.add(Path.of("jar:" + jarPath.toUri() + "!/" + entry.getName()));
                }
            }
        }

        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private static void log(DiscoveryLog logger, String message, FmiProtocol.Fmi2Status status) {
        if (logger != null) {
            logger.log(message, status, "model");
        }
    }

    private static boolean matchesVdmRt(String pathOrName) {
        String fileName = pathOrName;
        int slash = pathOrName.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < pathOrName.length()) {
            fileName = pathOrName.substring(slash + 1);
        }
        return VDM_RT_FILTER.accept(null, fileName);
    }
}


