# Overture FMU Simulation and Exporter

<!-- [![Build Status](https://build.overture.au.dk/jenkins/buildStatus/icon?job=overture-development)](https://build.overture.au.dk/jenkins/job/overture-development/) -->
[![License](http://img.shields.io/:license-gpl3-blue.svg?style=flat-square)](http://www.gnu.org/licenses/gpl-3.0.html)
<!-- [![Maven Central](https://img.shields.io/maven-central/v/org.overturetool/core.svg?label=Maven%20Central)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.overturetool.core%22) -->

## Dependencies

This project depends on (**See https://github.com/overturetool/overture-release-scripts/wiki/Overture-FMU-Details**):
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
