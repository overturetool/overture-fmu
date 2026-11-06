# Overture FMU Simulation and Exporter

<!-- [![Build Status](https://build.overture.au.dk/jenkins/buildStatus/icon?job=overture-development)](https://build.overture.au.dk/jenkins/job/overture-development/) -->
[![License](http://img.shields.io/:license-gpl3-blue.svg?style=flat-square)](http://www.gnu.org/licenses/gpl-3.0.html)
<!-- [![Maven Central](https://img.shields.io/maven-central/v/org.overturetool/core.svg?label=Maven%20Central)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.overturetool.core%22) -->

## Dependencies

### UniFMU Java runtime

Build the FMU packer and its generated runtime dependency from the repository root:

```sh
mvn -pl fmu -am package
```

Packaging produces a versioned release JAR,
`fmu/target/vdmfmu-<version>-jar-with-dependencies.jar`, and an additional local
runtime JAR, `fmu/target/vdmfmu-jar-with-dependencies.jar`. Only the versioned
assembly is attached for Maven installation/deployment. The packer and
`pack-debug-fmu.sh` use the unversioned file, so they do not need changing for
each release.

The `unifmu-runtime` module downloads the native UniFMU generator for macOS/Linux
(x86_64 or ARM64), generates a Java FMI 2 FMU, and packages the runtime JAR from
that FMU as a Maven dependency. Windows uses Docker with `--platform linux/amd64`.
No separately published release JAR is used. Change `unifmu.version` in the root
`pom.xml` to select the release.

Downloads, generated binaries, and the runtime JAR stay under
`unifmu-runtime/target`; generation is skipped when the selected version's JAR
and all expected binaries are present. macOS/Linux require `sh`, `curl`, and
`unzip`; Windows requires Docker with Linux containers. The FMU module uses the
upstream communication/protocol implementation, configured with
`unifmu.model.impl`, `unifmu.fmi.version`, and `unifmu.launch.mode`.

Run module-specific tests with, for example,
`mvn -pl fmu -am -Dtest=FmuChannelTest -Dsurefire.failIfNoSpecifiedTests=false test`.
To build `fmu/pom.xml` on its own, first install the reactor with
`mvn -pl fmu -am install`.

For IntelliJ, import the root `pom.xml` and reload all Maven projects after the
first build. The `intellij-unifmu-runtime` profile activates automatically when
IntelliJ supplies `idea.version`, adding the generated runtime JAR as an explicit
IDE library. This lets the editor resolve `Fmi2Model` and its nested
`Fmi2Status`; the regular reactor dependency is still used by command-line
builds. If your IntelliJ importer does not activate the profile automatically,
enable it in the Maven tool window and reload. After `mvn clean`, regenerate the
JAR with the build command above before reloading.

This project depends on (**See https://gitlab.au.dk/software-engineering/tool-dependencies/-/wikis/Overture-FMU**):
* FMI implementation: https://github.com/overturetool/shm-fmi
* Co-simulation extension for the Overture Interpreter: https://github.com/crescendotool/crescendo
* Overture Interpreter: https://github.com/overturetool/overture


## Release procedure

### Prepare

1. Move all open issued to next milestone
2. Close the current milestone
3. Create the release note:
```bash
cd releaseNotes/
./github-fetch-milestone-issues.py 
git add ReleaseNotes_* && git commit -m "updated release note" && git push
```

4. Perform the release
- Update `overture.release.properties` to contain the release version and the next development version and commit/push this change.
- Checkout the release branch and merge this change.
- Go to the build task found [here](https://build.overture.au.dk/jenkins/view/Overture/job/overture-fmu/job/release/) and instruct the build server to perform the release by checking and clicking 'Yes'.

5. Create a github release with the content of the release note and attach the fmu-import-export JAR and p2 repository from http://overture.au.dk/into-cps/vdm-tool-wrapper/master/latest/
6. Merge the newly released tag into master and push this change
7. Update the into-cps dev release bundle. Latest bundle from here: https://github.com/into-cps/into-cps.github.io/tree/development/download
