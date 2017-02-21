#!/bin/bash
cd ..
java -jar future-benchmark/target/benchmarks.jar -prof gc -foe true -jvmArgs "-Xms1G -Xmx1G" -gc true -f 1 -wi 4 -i 3 -rf csv $1
cat jmh-result.csv | sed 's/Benchmark./Benchmark","/g' | grep ops > thrpt.csv
cat jmh-result.csv | sed 's/Benchmark./Benchmark","/g' | grep gc.alloc.rate.norm > gc.csv
