#!/bin/sh

./gradlew mainBenchmarkJar -PbenchmarksInclude="beerouter"
cd benchmark
java -jar build/benchmarks/main/jars/benchmark-main-jmh-1.7.8-JMH.jar -prof stack
#java -jar build/benchmarks/main/jars/benchmark-main-jmh-1.7.8-JMH.jar -prof "async:libPath=/var/home/jgillich/Downloads/async-profiler-4.3-linux-x64/lib/libasyncProfiler.so;output=flamegraph;dir=profile-results"
