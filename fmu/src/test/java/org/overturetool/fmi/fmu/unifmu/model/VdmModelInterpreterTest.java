package org.overturetool.fmi.fmu.unifmu.model;

import com.fujitsu.vdmj.lex.Dialect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.overturetool.fmi.fmu.unifmu.protocol.FmiProtocol;
import org.overturetool.fmi.fmu.unifmu.FmuPacker;

import java.io.BufferedWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class VdmModelInterpreterTest {
    private static final String OUTPUT_CSV_NAME = "level_plot.csv";
    private static final String PLOT_SCRIPT_NAME = "plot_level.py";

    public static List<Path> findFiles(Path root, FilenameFilter filter) throws IOException {
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> filter.accept(p.getParent().toFile(), p.getFileName().toString()))
                .collect(Collectors.toList());
    }

    private static Path getTargetOutputDir(String testName) throws IOException {
        Path currentDir = Paths.get("").toAbsolutePath();
        Path moduleDir = currentDir.resolve("fmu");
        Path projectRoot = Files.isDirectory(moduleDir) && Files.exists(moduleDir.resolve("pom.xml"))
                ? moduleDir
                : currentDir;

        Path outputDir = projectRoot.resolve("target").resolve(VdmModelInterpreterTest.class.getSimpleName()).resolve(testName);
        Files.createDirectories(outputDir);
        return outputDir;
    }

    private static void writeCsv(Path outputDir, List<Double> times, List<Double> levels, List<Boolean> valveState, double minLevel, double maxLevel) throws IOException {
        Path csvPath = outputDir.resolve(OUTPUT_CSV_NAME);
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write("time,level,valve,minlevel,maxlevel\n");
            for (int i = 0; i < times.size(); i++) {
                writer.write(String.format(Locale.US, "%.6f,%.6f,%s,%.6f,%.6f%n",
                        times.get(i), levels.get(i), valveState.get(i), minLevel, maxLevel));
            }
        }
    }

    private static void createPythonPlotScript(Path outputDir) throws IOException {
        Path scriptPath = outputDir.resolve(PLOT_SCRIPT_NAME);
        String script = """
#!/usr/bin/env python3
import csv
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

base_dir = Path(__file__).resolve().parent
csv_path = base_dir / "level_plot.csv"
plot_path = base_dir / "level_plot.png"

with csv_path.open(newline="") as csvfile:
    reader = csv.DictReader(csvfile)
    rows = list(reader)

if not rows:
    raise SystemExit(f"No data found in {csv_path}")

times = [float(row[\"time\"]) for row in rows]
levels = [float(row[\"level\"]) for row in rows]
valve_states = [row[\"valve\"].strip().lower() == \"true\" for row in rows]
min_level = float(rows[0][\"minlevel\"])
max_level = float(rows[0][\"maxlevel\"])

fig, ax = plt.subplots(figsize=(10, 6))

ax.plot(times, levels, label=\"Level\", color=\"tab:blue\", linewidth=2)
ax.axhline(min_level, color=\"tab:orange\", linestyle=\"--\", linewidth=1.5, label=\"Min level\")
ax.axhline(max_level, color=\"tab:red\", linestyle=\"--\", linewidth=1.5, label=\"Max level\")
ax.set_ylabel(\"Level\")
ax.set_xlabel(\"Time\")
ax.grid(True, linestyle=\"--\", alpha=0.4)
ax.set_title(\"VDM Model Interpreter Level Trace\")

valve_values = [1.0 if v else 0.0 for v in valve_states]
ax2 = ax.twinx()
ax2.step(times, valve_values, where=\"post\", color=\"lightgray\", linestyle=\"--\", linewidth=2, label=\"Valve\")
ax2.set_ylabel(\"Valve\")
ax2.set_ylim(-0.1, 1.1)
ax2.set_yticks([0, 1])
ax2.set_yticklabels([\"False\", \"True\"])

lines1, labels1 = ax.get_legend_handles_labels()
lines2, labels2 = ax2.get_legend_handles_labels()
ax.legend(lines1 + lines2, labels1 + labels2, loc=\"best\")

fig.tight_layout()
fig.savefig(plot_path, dpi=200)
print(f\"Saved plot to {plot_path}\")
""".strip() + "\n";
        Files.writeString(scriptPath, script, StandardCharsets.UTF_8);
        scriptPath.toFile().setExecutable(true);
    }

    private static void ensurePythonEnvironment(Path outputDir) throws IOException, InterruptedException {
        Path venvDir = outputDir.resolve(".venv");
        if (!Files.exists(venvDir)) {
            ProcessBuilder venvBuilder = new ProcessBuilder("python3", "-m", "venv", venvDir.toString());
            venvBuilder.redirectErrorStream(true);
            Process venvProcess = venvBuilder.start();
            String venvOutput = new String(venvProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = venvProcess.waitFor();
            if (exitCode != 0) {
                throw new IOException("Failed to create Python virtual environment: " + venvOutput);
            }
        }

        Path pythonExecutable = Files.exists(venvDir.resolve("bin").resolve("python"))
                ? venvDir.resolve("bin").resolve("python")
                : venvDir.resolve("Scripts").resolve("python.exe");

        if (!Files.exists(pythonExecutable)) {
            throw new IOException("Python executable not found in environment: " + pythonExecutable);
        }

        ProcessBuilder installBuilder = new ProcessBuilder(pythonExecutable.toString(), "-m", "pip", "install", "--quiet", "matplotlib");
        installBuilder.redirectErrorStream(true);
        installBuilder.directory(outputDir.toFile());
        Process installProcess = installBuilder.start();
        String installOutput = new String(installProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int installExitCode = installProcess.waitFor();
        if (installExitCode != 0) {
            throw new IOException("Failed to install matplotlib in virtual environment: " + installOutput);
        }
    }

    private static void runPythonPlotter(Path outputDir) throws IOException, InterruptedException {
        ensurePythonEnvironment(outputDir);
        Path venvDir = outputDir.resolve(".venv");
        Path pythonExecutable = Files.exists(venvDir.resolve("bin").resolve("python"))
                ? venvDir.resolve("bin").resolve("python")
                : venvDir.resolve("Scripts").resolve("python.exe");
        Path plotScript = outputDir.resolve(PLOT_SCRIPT_NAME);

        ProcessBuilder builder = new ProcessBuilder(pythonExecutable.toString(), plotScript.toString());
        builder.redirectErrorStream(true);
        builder.directory(outputDir.toFile());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Python plot script failed: " + output);
        }
        System.out.println(output);
    }

    @Test
    public void testInit() throws Exception {
        var interpreter = new VdmRuntimeFmiModel(findFiles(Paths.get("src", "test", "resources", "model"), Dialect.VDM_RT.getFilter()));

        interpreter.init();
        interpreter.setValueParameter(new FmuPacker.ScalarVariable(0,"HardwareInterface.maxlevel", FmuPacker.Type.Real, FmuPacker.Variability.constant, FmuPacker.Causality.parameter,null,null), 8.9);

        interpreter.fmi2ExitInitializationMode();

    }

    @Test
    public void testInterpreterStep() throws Exception {
        List<Path> vdmrtFiles = findFiles(Paths.get("src", "test", "resources", "model"), Dialect.VDM_RT.getFilter());
        var interpreter = new VdmRuntimeFmiModel(vdmrtFiles);

        double endTime = 5.0;
        double time = 0.0;
        interpreter.init();
        Assertions.assertEquals(FmiProtocol.Fmi2Status.OK, interpreter.fmi2SetupExperiment(time, endTime, 0.1));

        var scalars = FmuPacker.getScalarVariables(vdmrtFiles);
        var maxLevelSv = scalars.stream().filter(sv -> sv.name().equals("HardwareInterface.maxlevel")).findFirst().orElseThrow();
        var minLevelSv = scalars.stream().filter(sv -> sv.name().equals("HardwareInterface.minlevel")).findFirst().orElseThrow();
        var maxLevel = -1d;
        interpreter.setValueParameter(new FmuPacker.ScalarVariable(0, "HardwareInterface.maxlevel", FmuPacker.Type.Real, FmuPacker.Variability.constant, FmuPacker.Causality.parameter, null,null), maxLevel);
        maxLevel = 2.0;
        var minLevel = 1.0;
        Assertions.assertEquals(FmiProtocol.Fmi2Status.OK, interpreter.fmi2SetReal(List.of(minLevelSv.valueReference(), maxLevelSv.valueReference()), List.of(minLevel, maxLevel)));

        var valveSv = scalars.stream().filter(sv -> sv.name().equals("System.hwi.valveState")).findFirst().orElseThrow();
        var levelSv = scalars.stream().filter(sv -> sv.name().equals("System.hwi.level")).findFirst().orElseThrow();
        interpreter.fmi2ExitInitializationMode();

        double level = 0.0;
        double stepSize = .01;
        List<Double> times = new ArrayList<>();
        List<Double> levels = new ArrayList<>();
        List<Boolean> valveStates = new ArrayList<>();

        while (time < endTime) {
            Assertions.assertEquals(FmiProtocol.Fmi2Status.OK, interpreter.fmi2SetReal(List.of(levelSv.valueReference()), List.of(level)));
            Assertions.assertEquals(FmiProtocol.Fmi2Status.OK, interpreter.fmi2DoStep(time, stepSize, false));
            var valveValue = interpreter.fmi2GetBoolean(List.of(valveSv.valueReference()));
            Assertions.assertNotNull(valveValue);
            Assertions.assertEquals(FmiProtocol.Fmi2Status.OK, valveValue.status());
            boolean valve = valveValue.values().getFirst();
            if (!valve) {
                level += 0.1;
            } else {
                level -= 0.1;
            }
            time += stepSize;
            times.add(time);
            levels.add(level);
            valveStates.add(valve);
        }

        Path outputDir = getTargetOutputDir("testInterpreterStep");
        writeCsv(outputDir, times, levels, valveStates, minLevel, maxLevel);
        createPythonPlotScript(outputDir);
        runPythonPlotter(outputDir);
    }


    @Test
    public void testScalarVariableExtraction() throws Exception {
        var files = findFiles(Paths.get("src", "test", "resources", "model"), Dialect.VDM_RT.getFilter());

        var variables = FmuPacker.getScalarVariables(files);
        Assertions.assertNotNull(variables);
    }
}
