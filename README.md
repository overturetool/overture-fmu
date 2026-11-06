# VDM-RT FMU Exporter

Expose a **VDM Real-Time (VDM-RT)** specification as an **FMI 2.0
Co-Simulation FMU**. [VDMJ](https://github.com/nickbattle/vdmj) interprets the VDM
model; [UniFMU](https://github.com/INTO-CPS-Association/unifmu) supplies the native
FMI embedding and Java communication runtime. The specification stays in VDM:
it is not translated into C.

```text
FMI importer <-> UniFMU native library <-> Java runtime <-> VDMJ <-> VDM-RT model
```

The packer generates `modelDescription.xml`, includes the model and native
libraries, and configures the Java backend. During simulation, FMI inputs and
outputs map to VDM port values and `fmi2DoStep` advances the interpreter.

## Requirements

- Java **21** and Maven to build; internet access for dependencies and UniFMU.
- macOS/Linux: `sh`, `curl`, and `unzip`.
- Windows x86_64: Windows PowerShell.
- To run an exported FMU: Java 21 available as `java` on the simulator's `PATH`,
  including inside a container, and an FMI 2.0 Co-Simulation importer.

FMUs include Linux x86_64 and ARM64, universal macOS x86_64/ARM64, and Windows
x86_64 native binaries. They require the Java backend at runtime; they are not
standalone native executables.

## Build

From the repository root:

```sh
mvn package
```

This builds and tests both modules and produces the executable release JAR
`fmu/target/vdmfmu-<version>-jar-with-dependencies.jar`.
You can also download the versioned JAR from a GitHub release or build artifact.
The examples below use `0.3.0-SNAPSHOT`; substitute the version you downloaded
or built.

## Quick start

Use the release JAR anywhere; no checkout or Maven installation is needed after
downloading it. Create a project and pack its runnable starter model:

```sh
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar init my-model --with-stdlib
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar pack my-model
```

The second command creates `my-model/dist/my-model.fmu`. The starter controller
runs every 10 ms and produces changing outputs even with no inputs connected:
a four-second triangle wave scaled by `gain`, a counter incrementing each second,
and Boolean/String outputs alternating each second. Real and Integer inputs add
offsets, the Boolean input inverts the alternating signal, and the String input
prefixes the `" low"` / `" high"` labels.

To pack and check the model in one command, run the generated test helper
(Python 3 and Java 21 required):

```sh
./my-model/test.sh
```

On Windows (with the Python `py` launcher installed):

```bat
my-model\test.cmd
```

The script calls the packing helper to create `dist/test-model.fmu`, creates a
Python virtual environment under `dist/.fmpy-venv`, installs FMPy (internet
required), and runs the FMU with an eight-second plot. It does not modify any
model files. String outputs are not plotted.
Both test helpers remember the exporter JAR used to generate the project scripts.
Pass a replacement JAR path as the first argument if it moved or you want another version.
For example: `./my-model/test.sh /path/to/another.jar` (Windows:
`my-model\test.cmd C:\tools\another.jar`).

Alternatively, run [FMPy](https://github.com/CATIA-Systems/FMPy) yourself:

```sh
python -m pip install "fmpy[complete]"
fmpy simulate my-model/dist/my-model.fmu --stop-time 8 --show-plot
```

```text
my-model/
  .vdmfmu                  Project marker (keep this file)
  .gitignore               Ignores /dist/
  model/
    System.vdmrt           System, hardware interface and CPU deployment
    World.vdmrt            Entry point that starts the controller
    HardwareInterface.vdmrt  Gain parameter and an input/output of each FMI type
    Controller.vdmrt       Example periodic model behaviour
    lib/
      Fmi.vdmrt            FMI port classes (always included)
      *.vdmrt              Optional VDMJ standard libraries
  pack.sh                  macOS/Linux packing helper
  pack.cmd                 Windows packing helper
  test.sh                  macOS/Linux pack-and-simulate helper
  test.cmd                 Windows pack-and-simulate helper
  dist/                    Generated FMUs (ignored by Git)
```

Omit `--with-stdlib` if you do not need the VDMJ libraries. With it, all bundled
VDM-RT library sources (currently IO, CSV, MATH, VDMUtil, and VDMUnit) are installed
under `model/lib/`. Their Java implementations are already in the tool/runtime.

Get help at any level:

```sh
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar --help
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar init --help
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar pack --help
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar update --help
```

## From a VDM specification to an FMU

### 1. Prepare the model

Edit the generated `model/` directory, or put your existing `.vdmrt` files and
required VDM libraries in a dedicated directory.
The packer discovers `.vdmrt` files recursively and includes the directory's
contents in the FMU resources. Use a dedicated model directory, not the whole
project checkout. Data files and other resources placed anywhere under `model/`
are preserved, including any scripts needed by your model. The project's root
packing/test scripts, metadata, and `dist/` are not included.

The current backend expects:

- A `HardwareInterface` class containing annotated FMI ports.
- A VDM `system` class with a `HardwareInterface` instance. The example uses
  `System` and its `public static hwi` member.
- A `World` class with a public `run : () ==> ()` operation; the backend
  executes `new World().run()` to start the model's threads.
- The port classes in [`Fmi.vdmrt`](fmu/src/main/resources/project-template/lib/Fmi.vdmrt):
  `RealPort`, `IntPort`, `BoolPort`, and `StringPort`.

Expose parameters as annotated value definitions and inputs/outputs as
annotated instance variables. For example:

```vdm
class HardwareInterface

values
    -- @ScalarVariable(<parameter>, <fixed>)
    public threshold : RealPort = new RealPort(2.0);

instance variables
    -- @ScalarVariable(<input>, <continuous>)
    public level : RealPort := new RealPort(0.0);

    -- @ScalarVariable(<output>, <discrete>)
    public valve : BoolPort := new BoolPort(false);

end HardwareInterface
```

Use the port `getValue()` and `setValue()` operations from the model. The
annotation's first argument is FMI causality; the second is variability.
The packer assigns value references and derives types and initial values.
With a system member named `System.hwi`, variable names appear as
`System.hwi.level` and `System.hwi.valve`; the parameter above appears as
`HardwareInterface.threshold`.

See the complete [water-tank controller](fmu/src/test/resources/model), especially
[`System.vdmrt`](fmu/src/test/resources/model/System.vdmrt) and
[`World.vdmrt`](fmu/src/test/resources/model/World.vdmrt), for system setup,
CPU deployment, and thread startup. Retain the example's structure when
adapting a model; this is not yet a general exporter for arbitrary entry points
or arbitrary VDM data types.

### 2. Pack the FMU

Pack a generated project:

```sh
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar pack my-model
```

Only `my-model/model/` becomes FMU resources; output defaults to
`my-model/dist/`. With no argument, `pack` uses the current directory.

You can also pack an existing model directory directly and choose the name and
output directory:

```sh
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar pack path/to/spec --output exported --name controller
```

This creates `exported/controller.fmu`. Choose a name starting with a letter
or underscore, containing letters, digits, underscores or hyphens. When omitted,
the name defaults to the project/model directory name. Output must be outside
the specification directory so that previous FMUs do not become model resources.
For project directory names containing spaces, pass a valid name, for example
`./my-model/pack.sh --name controller`.
Root-level `launch.toml`, `log4j2.xml`, `backend.jar`, `classes/`, and
`runtime-libs/` are reserved for the backend; conflicting model resources cause
an error rather than being overwritten.

Or use the generated helpers without arguments:

```sh
./my-model/pack.sh
```

On Windows:

```bat
my-model\pack.cmd
```

The helpers remember the absolute path to the JAR used by `init` or `update`.
They work from any working directory and write FMUs to the project's `dist/`
by default. If that JAR has moved, they report the missing path; pass a replacement
as the first argument, for example `./my-model/pack.sh /path/to/another.jar`.
Both helpers accept additional `pack` options, such as `--name controller` or
`--output /another/directory`. Running `update` with a newer JAR refreshes the
remembered path.

For the repository's existing water-tank example:

```sh
java -jar fmu/target/vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar pack fmu/src/test/resources/model --output target/export --name watertank
```

### Updating an existing project

Use a newer tool JAR to refresh project support files:

```sh
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar update my-model
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar update my-model --with-stdlib
```

`update` replaces `pack.sh`, `pack.cmd`, `test.sh`, and `test.cmd` but leaves your
VDM classes, `lib/Fmi.vdmrt`, other model resources, and project metadata intact.
`--with-stdlib` adds missing library files and reports existing ones as
preserved. To intentionally replace the standard-library copies with the
tool's bundled versions, add `--overwrite-libraries` (requires `--with-stdlib`).
Back up any library edits before doing so. `init` refuses non-empty directories;
it never resets an existing project.

### 3. Simulate

Import the `.fmu` into an FMI 2.0 Co-Simulation tool, configure its parameters
and inputs, and run it with Java 21 available to the backend.

For example, install [FMPy](https://github.com/CATIA-Systems/FMPy) in your Python
environment and run the FMU directly:

```sh
python -m pip install "fmpy[complete]"
fmpy simulate target/export/watertank.fmu --stop-time 5 --show-plot
```

On macOS/Linux, the included helper sets up FMPy in a local virtual environment:

```sh
fmu/src/test/resources/fmu-test.sh target/export/watertank.fmu
```

The helper needs Python 3 and creates a repository-local `.venv`, installing
`fmpy[complete]` if needed. Add `--show-plot` to display the result.
For a meaningful closed-loop water-tank simulation, connect the controller's
level input to a plant model; the helper does not supply that plant.

## Debug the Java backend

Append `--debug` (port 5005) or `--debug=5007` when packing:

```sh
java -jar vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar pack my-model --debug=5007
```

When the simulator starts the FMU, the JVM suspends until a debugger attaches.
In IntelliJ, create a **Remote JVM Debug** configuration for `localhost:5007`.
For Docker, publish the same port with `-p 5007:5007`. JDWP listens on all
interfaces and has no authentication; do not expose it publicly.

`pack-debug-fmu.sh` is a developer convenience script with a hardcoded external
example-project destination. Use the command above for your own projects.
The `--debug` option is intentionally kept out of the main options list; its
usage is described in the `pack --help` developer note. The original positional
`<model> <output> <name> [--debug[=port]]` invocation remains supported for old
scripts; new scripts should use `pack`.

## Development

The `unifmu-runtime` module generates a Java FMU and takes its embedded runtime
JAR as a dependency. Downloads and generated output stay under
`unifmu-runtime/target` and are reused while complete. Change `unifmu.version`
in the root POM to select the UniFMU release. Windows uses the native generator;
the optional `unifmu-windows-docker` profile requires Linux containers
(`--platform linux/amd64`) and disabling `unifmu-windows-native`.

Packaging also creates an unattached `vdmfmu-jar-with-dependencies.jar` for local
development scripts; use the versioned JAR for releases.

### Upgrading dependencies

| Dependency | Where to change the version |
|---|---|
| VDMJ | `fmu/pom.xml`: change `vdmj.version`, shared by `vdmj` and `stdlib` |
| UniFMU | Root `pom.xml`: change the `unifmu.version` property |

After changing either dependency, run `mvn clean package` to
regenerate the runtime and run the tests. A UniFMU upgrade may also require
adapting to changes in the generated Java model/protocol API.

Import the root `pom.xml` into IntelliJ and build once. Reload Maven projects;
the `intellij-unifmu-runtime` profile exposes the generated JAR to the editor.
If it is not activated automatically, select it in Maven's Profiles list.
After `mvn clean`, build again before reloading.

Run all tests with `mvn test`, or select a test:

```sh
mvn -Dtest=FmuChannelTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Interpreter tests also use Python 3 and install matplotlib in a test-local
virtual environment. Build from the root so Maven generates the runtime
dependency first. To build `fmu/pom.xml` independently, first run
`mvn install`.

| Path | Purpose |
|---|---|
| `fmu/` | VDMJ backend, annotations, packer, example model, and tests |
| `unifmu-runtime/` | Platform-specific UniFMU bootstrap and runtime dependency |
| `.github/workflows/` | Cross-platform builds and GitHub releases |
| `scripts/` | Release version selection and its tests |
| `testing/` | Preserved legacy integration tooling and submodules; not part of the current Maven build |

## Builds and releases

**Build and Test** runs on pushes, pull requests, and manual dispatch with
Java 21 on `ubuntu-latest`, `macos-latest`, and `windows-latest`. Versioned JARs
are available under each run's **Artifacts** for **3 days**. Manual builds
accept an optional stable version, with or without a `v` prefix.

Run **Actions -> Release -> Run workflow** on the desired branch to release.
Supply a stable version, or leave it blank for the next patch (or patch zero
when the POM revision begins a newer major/minor line). Version selection
recognizes both `v<version>` and historical `Release/<version>` tags and rejects
versions that precede the POM revision or existing releases.

The workflow builds and tests, commits the release version to a tagged commit,
and creates a **draft GitHub release** with generated notes and the versioned
JAR. It does not update the selected branch. Select `release_automatically` to
publish immediately, or review and publish the draft manually. Release assets
do not expire with workflow artifacts. Neither workflow deploys to Maven Central
or any other Maven repository.

## License

[GNU General Public License version 3](LICENSE).
