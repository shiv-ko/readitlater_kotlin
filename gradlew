#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 1

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: JDK 17 が見つかりません。JAVA_HOME を設定してください。" >&2
    exit 1
fi

exec "$JAVACMD" \
    -Dfile.encoding=UTF-8 \
    -Dorg.gradle.appname=gradlew \
    -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    "$@"

