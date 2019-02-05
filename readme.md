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

1. Move all not closed issued to next milestone
2. Close current milestone
3. Update release note in repo
```bash
cd releaseNotes/
./github-fetch-milestone-issues.py 
git add ReleaseNotes_* && git commit -m "updated release note" && git push
```

4. Perform the release
- Update `overture.release.properties` and perform a commit.
- Checkout the release branch and merge to the commit above.
- Go to the build on Jenkins and answer `ABORT` to the prompt. (yes, `ABORT`).

5. Create a github release with text from relaseNotes and add the fmu-import-export jar and p2 repo from http://overture.au.dk/into-cps/vdm-tool-wrapper/master/latest/
6. Merge the newly released tag into master and push master
7. Update the into-cps dev release bundle. Latest bundle from here: https://github.com/into-cps/into-cps.github.io/tree/development/download
