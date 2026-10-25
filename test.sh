#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
task_java="${JAVA_HOME:-/home/nuttyinccalebgamerpro9999/dev-tools/java-22}"
python3 tools/fetch_dependencies.py
mkdir -p build/host-tests
"$task_java/bin/javac" -cp "app/libs/*" -d build/host-tests app/src/main/java/com/nuttyvr/explorer/LocalFiles.java app/src/main/java/com/nuttyvr/explorer/ApkFiles.java tests/LocalFilesTest.java tests/ApkFilesTest.java app/src/main/java/com/nuttyvr/explorer/ArchiveFiles.java tests/ArchiveFilesTest.java
"$task_java/bin/java" -cp "build/host-tests:app/libs/*" com.nuttyvr.explorer.LocalFilesTest
"$task_java/bin/java" -cp "build/host-tests:app/libs/*" com.nuttyvr.explorer.ApkFilesTest
"$task_java/bin/java" -cp "build/host-tests:app/libs/*" com.nuttyvr.explorer.ArchiveFilesTest
