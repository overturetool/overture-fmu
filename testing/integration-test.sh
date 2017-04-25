#!/bin/bash
set -e

echo Prepare test program

cd fmu-tester
git submodule update --init
cmake .
make

cd ..


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

cd output
unzip $NAME.fmu
cd ..

RESOURCE=`readlink -f output/resources`
echo "Resource location: '${RESOURCE}'"

echo "Checking for platform..."
case "$OSTYPE" in
  solaris*) echo "SOLARIS" ;;
  darwin*)  echo "OSX"; LIB=`readlink -f output/binaries/darwin64/$NAME.dylib`;; 
  linux*)   echo "LINUX" ;;
  bsd*)     echo "BSD" ;;
  *)        echo "unknown: $OSTYPE" ;;
esac

echo "Testing will use the following configuration:"
echo -e "\tResources folder: '${RESOURCE}'"
echo -e "\tLibrary folder  : '${LIB}'"

echo ""
echo "Running test..."
echo "-----------------------------------------------------------------------------"

fmu-tester/fmu-tester $LIB $RESOURCE

echo "-----------------------------------------------------------------------------"

echo "Clean up"
rm -rf output
