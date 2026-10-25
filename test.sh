#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
task_java="${JAVA_HOME:-/home/nuttyinccalebgamerpro9999/dev-tools/java-22}"
mkdir -p build/host-tests
"$task_java/bin/javac" -d build/host-tests app/src/main/java/com/nuttyvr/explorer/LocalFiles.java app/src/main/java/com/nuttyvr/explorer/ApkFiles.java tests/LocalFilesTest.java tests/ApkFilesTest.java
"$task_java/bin/java" -cp build/host-tests com.nuttyvr.explorer.LocalFilesTest
"$task_java/bin/java" -cp build/host-tests com.nuttyvr.explorer.ApkFilesTest
