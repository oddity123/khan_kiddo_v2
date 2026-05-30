#!/usr/bin/env bash
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
exec mvn -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/pom.xml" "$@"
