#!/usr/bin/env bash
# 在仓库根目录调用，实际构建 backend 模块
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
exec mvn -f "$ROOT/backend/pom.xml" "$@"
