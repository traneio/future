#!/bin/bash
cd ..
mvn clean install -Dmaven.test.skip=true
java -jar future-benchmark/target/benchmarks.jar -gc true -prof gc -foe true -jvmArgs "-Xms1G -Xmx1G" -f 1 -wi 4 -i 3 -rf csv $1
