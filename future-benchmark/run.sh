#!/bin/bash
cd ..
mvn clean install -Dmaven.skip.test=true
java -jar future-benchmark/target/benchmarks.jar $1 -prof gc -f 1 -gc true -wi 5 -i 5 $@
