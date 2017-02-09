#!/bin/bash
cd ..
mvn install -Dmaven.test.skip=true
java -jar future-benchmark/target/benchmarks.jar -gc true -prof gc -foe true -jvmArgs "-Xms1G -Xmx1G" -f 1 -wi 2 -i 2 -rf csv $1
