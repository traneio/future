#!/bin/bash
cd ..
mvn clean install -Dmaven.test.skip=true
java -Xms1G -Xmx1G -jar future-benchmark/target/benchmarks.jar -foe true -f 0 -wi 0 -i 1 $1
