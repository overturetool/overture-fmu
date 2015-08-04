#!/bin/bash

echo Building linux 64 and windows 64
cd native-fmi/src/main/vagrant/ubuntu64
sh build.sh
cd ..
cd ubuntu32
echo Building linux 32 and windows 32
sh build.sh
cd ../../../../../

echo Building max 64
mvn package -Pnative-mac

echo Ready for deploy
