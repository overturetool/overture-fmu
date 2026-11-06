package org.overturetool.fmi.fmu.unifmu;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "vdmfmu", mixinStandardHelpOptions = true,
        versionProvider = FmuCli.Version.class,
        description = "Create and package VDM-RT projects as FMI 2.0 Co-Simulation FMUs.",
        footer = "Get started: vdmfmu init my-model --with-stdlib%n"
                + "Then:        vdmfmu pack my-model",
        subcommands = {FmuCli.Init.class, FmuCli.Pack.class, FmuCli.Update.class})
public class FmuCli implements Runnable {
    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static int execute(String... args) {
        CommandLine cli = new CommandLine(new FmuCli());
        cli.setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
            commandLine.getErr().println("Error: " + exception.getMessage());
            return commandLine.getCommandSpec().exitCodeOnExecutionException();
        });
        return cli.execute(args);
    }

    static class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            var properties = new java.util.Properties();
            try (var input = FmuCli.class.getResourceAsStream(
                    "/META-INF/maven/org.overturetool.fmi.fmu/vdmfmu/pom.properties")) {
                if (input != null) {
                    properties.load(input);
                }
            }
            return new String[]{"vdmfmu " + properties.getProperty("version", "development")};
        }
    }

    @Command(name = "init", mixinStandardHelpOptions = true,
            description = "Create a runnable starter project without overwriting existing files.")
    static class Init implements Callable<Integer> {
        @Parameters(index = "0", paramLabel = "DIRECTORY", description = "New or empty project directory.")
        Path directory;
        @Option(names = "--with-stdlib", description = "Include all VDMJ VDM-RT standard libraries.")
        boolean libraries;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            FmuProject.create(directory, libraries, spec.commandLine().getOut());
            return 0;
        }
    }

    @Command(name = "update", mixinStandardHelpOptions = true,
            description = "Refresh packing and test scripts; preserve user-written model and resource files.")
    static class Update implements Callable<Integer> {
        @Parameters(index = "0", defaultValue = ".", paramLabel = "PROJECT")
        Path directory;
        @Option(names = "--with-stdlib", description = "Add missing VDMJ VDM-RT standard libraries.")
        boolean libraries;
        @Option(names = "--overwrite-libraries",
                description = "Replace bundled standard-library copies. Requires --with-stdlib.")
        boolean overwrite;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            if (overwrite && !libraries) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "--overwrite-libraries requires --with-stdlib");
            }
            FmuProject.update(directory, libraries, overwrite, spec.commandLine().getOut());
            return 0;
        }
    }

    @Command(name = "pack", mixinStandardHelpOptions = true,
            description = "Pack a project (only model/) or a directory of VDM specifications and resources.",
            footer = "Developer option: --debug[=PORT] suspends the backend JVM for a debugger (default: 5005).")
    static class Pack implements Callable<Integer> {
        @Parameters(index = "0", defaultValue = ".", paramLabel = "PROJECT_OR_MODEL")
        Path source;
        @Option(names = {"-o", "--output"}, paramLabel = "DIRECTORY",
                description = "Output directory (default: PROJECT/dist or MODEL/../dist).")
        Path output;
        @Option(names = {"-n", "--name"}, description = "FMU name (default: project or model directory name).")
        String name;
        @Option(names = "--debug", arity = "0..1", fallbackValue = "5005", hidden = true,
                description = "Suspend the backend JVM for remote debugging.")
        Integer debugPort;
        @Spec
        CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            Path directory = source.toAbsolutePath().normalize();
            boolean project = Files.isRegularFile(directory.resolve(FmuProject.MARKER));
            if (project) {
                FmuProject.requireProject(directory);
            }
            Path model = project ? directory.resolve("model") : directory;
            if (!Files.isDirectory(model)) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Model directory not found: " + model);
            }
            Path destination = output != null ? output : project
                    ? directory.resolve("dist") : directory.resolveSibling("dist");
            String modelName = name != null ? name : directory.getFileName().toString();
            Path archive = FmuPacker.packageFmu(model, destination, modelName, debugPort);
            spec.commandLine().getOut().println("Created FMU: " + archive);
            if (debugPort != null) {
                spec.commandLine().getOut().printf(
                        "Debug mode: backend JVM waits on TCP port %1$d. For Docker, publish it with -p %1$d:%1$d; "
                                + "connect IntelliJ Remote JVM Debug to localhost:%1$d. "
                                + "JDWP has no authentication; do not expose this port publicly.%n", debugPort);
            }
            return 0;
        }
    }
}
