# Overture FMU Simulation and Exporter

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
