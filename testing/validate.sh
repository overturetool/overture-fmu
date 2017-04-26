
#!/bin/bash

set -e

FMU_CHECK_DIR=fmuchecker

#rm -rf $FMU_CHECK_DIR
mkdir -p $FMU_CHECK_DIR
cd $FMU_CHECK_DIR

cmake ../FMUComplianceChecker/ > /dev/null

make -j4 >/dev/null

cd ..

echo Validate FMUs


echo "Fetch current version"

VERSION=`mvn -q -Dexec.executable="echo" -Dexec.args='${project.version}' --non-recursive org.codehaus.mojo:exec-maven-plugin:1.3.1:exec -f ../`

echo "Version is '${VERSION}'"

JAR="../core/fmu-import-export/target/fmu-import-export-${VERSION}-jar-with-dependencies.jar"
MODEL=../core/fmu-import-export/src/test/resources/model
NAME=wt2

echo "Test will be performed on: '${JAR}'"
echo "Test will be performed on model: ${MODEL}"

rm -rf output
java -jar $JAR -export tool -name $NAME -root $MODEL -output output

$FMU_CHECK_DIR/fmuCheck*  -h 0.001 -s 30  output/wt2.fmu
