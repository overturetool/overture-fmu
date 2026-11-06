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

## From a VDM specification to an FMU

### 1. Prepare the model

Put all `.vdmrt` files and required VDM libraries under one model directory.
The packer discovers `.vdmrt` files recursively and includes the directory's
contents in the FMU resources. Use a dedicated model directory, not the whole
project checkout.

The current backend expects:

- A `HardwareInterface` class containing annotated FMI ports.
- A VDM `system` class with a `HardwareInterface` instance. The example uses
  `System` and its `public static hwi` member.
- A `World` class with a public `run : () ==> ()` operation; the backend
  executes `new World().run()` to start the model's threads.
- The port classes in [`Fmi.vdmrt`](fmu/src/test/resources/model/lib/Fmi.vdmrt):
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

After building, run from the repository root:

```sh
java -jar fmu/target/vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar path/to/model target/export my-model
```

The arguments are `<model-directory> <output-directory> <fmu-name>`.
This creates `target/export/my-model.fmu`. Supply a base name without `.fmu`.
Keep the build's `fmu/target` resources available: the current packing command
loads native libraries and logging configuration from the checkout's build
output. A downloaded JAR alone is not yet a checkout-independent packing tool.

For a complete runnable example:

```sh
java -jar fmu/target/vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar fmu/src/test/resources/model target/export watertank
```

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
java -jar fmu/target/vdmfmu-0.3.0-SNAPSHOT-jar-with-dependencies.jar path/to/model target/export my-model --debug=5007
```

When the simulator starts the FMU, the JVM suspends until a debugger attaches.
In IntelliJ, create a **Remote JVM Debug** configuration for `localhost:5007`.
For Docker, publish the same port with `-p 5007:5007`. JDWP listens on all
interfaces and has no authentication; do not expose it publicly.

`pack-debug-fmu.sh` is a developer convenience script with a hardcoded external
example-project destination. Use the command above for your own projects.

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
