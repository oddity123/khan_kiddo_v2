#!/usr/bin/env bash
# 固定使用 Temurin 21，避免与系统默认 JDK 8 冲突
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
exec mvn "$@"
