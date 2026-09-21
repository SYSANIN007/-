#!/usr/bin/env sh
# ---------------------------------------------------------------------------
#  gradlew для проекта «ACID Wallet»
#
#  В репозитории намеренно НЕТ бинарного gradle/wrapper/gradle-wrapper.jar
#  (бинарники в git не нужны). Поэтому скрипт умеет работать в трёх режимах:
#
#   1) если gradle/wrapper/gradle-wrapper.jar есть — запускается штатная обёртка;
#   2) если в PATH есть команда gradle — используется она;
#   3) иначе Gradle нужной версии скачивается в ~/.gradle/acidwallet-dists/
#      и запускается оттуда.
#
#  В Android Studio этот скрипт вообще не нужен: IDE сама скачает
#  дистрибутив по адресу из gradle/wrapper/gradle-wrapper.properties.
# ---------------------------------------------------------------------------
set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd)
APP_NAME="acidwallet"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
PROPS_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

JAVA_BIN="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
fi

if [ -f "$WRAPPER_JAR" ]; then
  exec "$JAVA_BIN" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  echo "[gradlew] gradle-wrapper.jar не найден — использую gradle из PATH" >&2
  exec gradle "$@"
fi

DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROPS_FILE" | tr -d '\r' | sed 's/\\:/:/g')
if [ -z "${DIST_URL:-}" ]; then
  echo "[gradlew] Не удалось прочитать distributionUrl из $PROPS_FILE" >&2
  exit 1
fi

DIST_NAME=$(basename "$DIST_URL" .zip)
DIST_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}/acidwallet-dists/$DIST_NAME"

if [ ! -x "$DIST_HOME/bin/gradle" ]; then
  echo "[gradlew] Скачиваю Gradle: $DIST_URL" >&2
  mkdir -p "$DIST_HOME"
  TMP_ZIP="$DIST_HOME/gradle.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --progress-bar -o "$TMP_ZIP" "$DIST_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$TMP_ZIP" "$DIST_URL"
  else
    echo "[gradlew] Нужен curl или wget. Проще всего открыть проект в Android Studio." >&2
    exit 1
  fi
  unzip -q -o "$TMP_ZIP" -d "$DIST_HOME.unpacked"
  rm -rf "$DIST_HOME"
  mv "$DIST_HOME.unpacked"/* "$(dirname "$DIST_HOME")/" 2>/dev/null || true
  rm -rf "$DIST_HOME.unpacked" "$TMP_ZIP"
fi

if [ ! -x "$DIST_HOME/bin/gradle" ]; then
  echo "[gradlew] Не удалось распаковать Gradle. Откройте проект в Android Studio — она сделает это сама." >&2
  exit 1
fi

exec "$DIST_HOME/bin/gradle" -p "$APP_HOME" "$@"
