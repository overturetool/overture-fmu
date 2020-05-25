#!/bin/bash

set -e

FMU_CHECK_DIR=fmuchecker

#rm -rf $FMU_CHECK_DIR
mkdir -p $FMU_CHECK_DIR
cd $FMU_CHECK_DIR

cmake -H../FMUComplianceChecker/ -B. > /dev/null

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
rm -rf output-source 

java -jar $JAR -export tool -name $NAME -root $MODEL -output output
java -jar $JAR -export source -name $NAME -root $MODEL -output output-source


# Validate tool wrapper FMU.

$FMU_CHECK_DIR/fmuCheck*  -h 0.001 -s 30  output/wt2.fmu
rm -rf output

# Validate source code FMU.
## Create source code FMU.
cd output-source

unzip $NAME.fmu

cp ../CMakeLists.txt .
sed -i.bak "s/##NAME##/$NAME/g" CMakeLists.txt

## Read defines if any
if [ -e "sources/defines.def" ] 
then

defs=""

while IFS='' read -r line || [[ -n "$line" ]]; do
    echo "Text read from file: $line"
  defs="$defs -D$line"
done < "sources/defines.def"

defs="add_definitions(${defs})"

sed -i.bak "s/##DEFINITIONS##/${defs}/g" CMakeLists.txt

fi


## Read additional includes if any
if [ -e "sources/includes.txt" ] 
then

includes=""

while IFS='' read -r line || [[ -n "$line" ]]; do
    echo "Text read from file: $line"
  includes="$includes sources/$line"
done < "sources/includes.txt"


includes="include_directories(${includes})"
echo "additional includes ${includes}"
sed -i.bak "s|##INCLUDES##|${includes}|g" CMakeLists.txt

fi

## Compile source code FMU.
cmake .
make -j5


case "$OSTYPE" in
  solaris*) echo "SOLARIS" ;;
  darwin*)  echo "OSX"; LIB=`readlink -f output-source/binaries/darwin64/$NAME.dylib`
			BIN=darwin64;; 
  linux*)   echo "LINUX"; LIB=`readlink -f output-source/binaries/linux64/$NAME.so`
			BIN=linux64;; 
  bsd*)     echo "BSD" ;;
  *)        echo "unknown: $OSTYPE" ;;
esac



mkdir -p binaries/$BIN
cp $LIB binaries/$BIN

##  Add library to source code FMU.
zip -ur wt2.fmu binaries/
cd ..


## Test source code FMU.
case "$OSTYPE" in
  solaris*) echo "SOLARIS" ;;
  darwin*)  echo "OSX"; LIB=`readlink -f output-source/binaries/darwin64/$NAME.dylib`;; 
  linux*)   echo "LINUX"; LIB=`readlink -f output-source/binaries/linux64/$NAME.so`;; 
  bsd*)     echo "BSD" ;;
  *)        echo "unknown: $OSTYPE" ;;
esac

$FMU_CHECK_DIR/fmuCheck*  -h 0.001 -s 30  output-source/wt2.fmu

echo "Clean up."
rm -rf output-source
