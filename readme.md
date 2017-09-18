# Overture FMU Simulation and Exporter

<!-- [![Build Status](https://build.overture.au.dk/jenkins/buildStatus/icon?job=overture-development)](https://build.overture.au.dk/jenkins/job/overture-development/) -->
[![License](http://img.shields.io/:license-gpl3-blue.svg?style=flat-square)](http://www.gnu.org/licenses/gpl-3.0.html)
<!-- [![Maven Central](https://img.shields.io/maven-central/v/org.overturetool/core.svg?label=Maven%20Central)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.overturetool.core%22) -->

## Dependencies

This project depends on:
* FMI implementation: https://gitlab.au.dk/into-cps/shm-fmi
* Co-simulation extension for the Overture Interpreter: https://github.com/crescendotool/crescendo
* Overture Interpreter: https://github.com/overturetool/overture


## Release procedure

### Prepare

1. Move all not closed issued to next milestone
2. Close current milestone
3. Update release note in repo
```bash
cd releaseNotes/
./github-fetch-milestone-issues.py 
git add ReleaseNotes_* && git commit -m "updated release note" && git push
```

4. Perform the release

The prefered way is to update `overture.release.properties` and run the release scipt using jenkins and the release job. Otherwise this works for the core plugins.

```bash
mvn -Dmaven.repo.local=repository release:clean
mvn -Dmaven.repo.local=repository release:prepare -DreleaseVersion=${RELEASE_VER} -DdevelopmentVersion=${NEW_DEV_VER}
mvn -Dmaven.repo.local=repository release:perform
```
