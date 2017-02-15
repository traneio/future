#!/bin/bash
cd ..
mvn clean install -Dmaven.test.skip=true
java -jar future-benchmark/target/benchmarks.jar -prof gc -foe true -jvmArgs "-Xms1G -Xmx1G" -gc true -f 1 -wi 4 -i 3 -rf csv $1
