#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-$DIR/.gradle/jdk}"
exec "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
