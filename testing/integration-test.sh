#!/bin/bash
set -e


# Prepare the fmu tester.
echo Prepare test program

git submodule update --init --recursive
cd fmu-tester
cmake .
make

cd ..


# Get latest version of the FMU exporter.
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


# Export tool wrapper and source code FMUs.
java -jar $JAR -export tool -name $NAME -root $MODEL -output output
java -jar $JAR -export source -name $NAME -root $MODEL -output output-source


# Test tool wrapper FMU.
cd output
unzip $NAME.fmu
cd ..

RESOURCE=`readlink -f output/resources`
echo "Resource location: '${RESOURCE}'"

echo "Checking for platform..."
case "$OSTYPE" in
  solaris*) echo "SOLARIS" ;;
  darwin*)  echo "OSX"; LIB=`readlink -f output/binaries/darwin64/$NAME.dylib`;; 
  linux*)   echo "LINUX"; LIB=`readlink -f output/binaries/linux64/$NAME.so`;; 
  bsd*)     echo "BSD" ;;
  *)        echo "unknown: $OSTYPE" ;;
esac

echo "Testing will use the following configuration:"
echo -e "\tResources folder: '${RESOURCE}'"
echo -e "\tLibrary folder  : '${LIB}'"

echo ""
echo "Running test..."
echo "-----------------------------------------------------------------------------"

fmu-tester/fmu-tester "$LIB" "$RESOURCE" "no-guid-needed"

echo "-----------------------------------------------------------------------------"

echo "Clean up"
rm -rf output


# Test source code FMU.
cd output-source

unzip $NAME.fmu

cat sources/defines.def
cat sources/includes.txt

cp ../CMakeLists.txt .
sed -i "s/##NAME##/$NAME/g" CMakeLists.txt

## Read defines if any
if [ -e "sources/defines.def" ] 
then

defs=""

while IFS='' read -r line || [[ -n "$line" ]]; do
    echo "Text read from file: $line"
  defs="$defs -D$line"
done < "sources/defines.def"

defs="add_definitions(${defs})"

sed -i "s/##DEFINITIONS##/${defs}/g" CMakeLists.txt

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
sed -i "s|##INCLUDES##|${includes}|g" CMakeLists.txt

fi


## Compile source code FMU.
cmake .
make -j5
mkdir -p binaries/linux64
cp wt2.so binaries/linux64
cd ..


## Test source code FMU.
case "$OSTYPE" in
  solaris*) echo "SOLARIS" ;;
  darwin*)  echo "OSX"; LIB=`readlink -f output-source/binaries/darwin64/$NAME.dylib`;; 
  linux*)   echo "LINUX"; LIB=`readlink -f output-source/binaries/linux64/$NAME.so`;; 
  bsd*)     echo "BSD" ;;
  *)        echo "unknown: $OSTYPE" ;;
esac

FMUGUID=`grep guid output-source/modelDescription.xml |awk -F '"' '{print $2}'`

fmu-tester/fmu-tester "$LIB" "$RESOURCE" "$FMUGUID"

echo "Clean up."
rm -rf output-source
