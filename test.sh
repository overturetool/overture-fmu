mvn package -Dmaven.test.skip=true -q
mvn package -q

rm -f ~/unifmu.log 

fmu/src/test/resources/fmu-test.sh fmu/target/FmuPackerTest/mainCreatesFmuInTargetFromResourceModel/test-model.fmu

tail ~/unifmu.log