#!/usr/bin/env sh
# ---------------------------------------------------------------------------
#  gradlew для проекта «ACID Wallet»
#
#  В репозитории намеренно НЕТ бинарного gradle/wrapper/gradle-wrapper.jar
#  (бинарники в git не нужны). Поэтому скрипт умеет работать сам:
#
#   1) если gradle/wrapper/gradle-wrapper.jar есть — запускается штатная обёртка;
#   2) если в PATH есть команда gradle — используется она;
#   3) если дистрибутив Gradle уже скачала Android Studio — берём его
#      из ~/.gradle/wrapper/dists (после первого sync в IDE ничего качать не нужно);
#   4) если Gradle найден, а jar отсутствует — обёртка создаётся автоматически
#      командой `gradle wrapper` (проект становится обычным);
#   5) иначе Gradle скачивается в ~/.gradle/acidwallet-dists/ и запускается оттуда.
#
#  Java ищется в JAVA_HOME, в PATH и во встроенном JBR Android Studio.
#
#  В Android Studio этот скрипт вообще не нужен: IDE сама скачает дистрибутив
#  по адресу из gradle/wrapper/gradle-wrapper.properties.
# ---------------------------------------------------------------------------
set -eu

APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
PROPS_FILE="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

# ── 1. Где взять Java ───────────────────────────────────────────────────────
find_java() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    echo "$JAVA_HOME/bin/java"
    return 0
  fi
  if command -v java >/dev/null 2>&1; then
    command -v java
    return 0
  fi
  # JBR, встроенный в Android Studio (типовые места установки)
  for candidate in \
    "$HOME/android-studio/jbr/bin/java" \
    "/opt/android-studio/jbr/bin/java" \
    "/usr/local/android-studio/jbr/bin/java" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" \
    "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" \
    "$HOME"/.local/share/JetBrains/Toolbox/apps/AndroidStudio/*/jbr/bin/java \
    "$HOME"/.local/share/JetBrains/Toolbox/apps/android-studio/*/jbr/bin/java \
    "$HOME"/.var/app/com.google.AndroidStudio/data/jbr/bin/java
  do
    if [ -x "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

JAVA_BIN=$(find_java || true)

# ── 2. Версия Gradle из свойств обёртки ─────────────────────────────────────
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$PROPS_FILE" | tr -d '\r' | sed 's/\\:/:/g')
DIST_NAME=$(basename "${DIST_URL:-gradle-8.9-bin.zip}" .zip)
DIST_VERSION=${DIST_NAME#gradle-}
DIST_VERSION=${DIST_VERSION%-bin}
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"

# ── 3. Где взять Gradle ─────────────────────────────────────────────────────
find_gradle() {
  if command -v gradle >/dev/null 2>&1; then
    command -v gradle
    return 0
  fi
  # дистрибутив, который уже распаковала Android Studio после первого sync
  for candidate in \
    "$GRADLE_HOME_DIR"/wrapper/dists/"$DIST_NAME"/*/gradle-"$DIST_VERSION"/bin/gradle \
    "$GRADLE_HOME_DIR"/wrapper/dists/"$DIST_NAME"/*/gradle-*/bin/gradle \
    "$GRADLE_HOME_DIR"/acidwallet-dists/"$DIST_NAME"/bin/gradle
  do
    if [ -x "$candidate" ]; then
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

GRADLE_BIN=$(find_gradle || true)

# ── 4. Штатная обёртка ──────────────────────────────────────────────────────
if [ -f "$WRAPPER_JAR" ] && [ -n "$JAVA_BIN" ]; then
  exec "$JAVA_BIN" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" \
    org.gradle.wrapper.GradleWrapperMain "$@"
fi

# ── 5. Обёртки нет, но Gradle найден — создаём стандартный gradle-wrapper.jar ─
if [ ! -f "$WRAPPER_JAR" ] && [ -n "$GRADLE_BIN" ] && [ -n "$JAVA_BIN" ]; then
  echo "[gradlew] gradle-wrapper.jar отсутствует — создаю стандартную обёртку..." >&2
  if "$GRADLE_BIN" -p "$APP_HOME" wrapper --gradle-version "$DIST_VERSION" --distribution-type bin >&2 2>&1; then
    if [ -f "$WRAPPER_JAR" ]; then
      exec "$JAVA_BIN" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" \
        org.gradle.wrapper.GradleWrapperMain "$@"
    fi
  fi
  echo "[gradlew] обёртку создать не удалось — запускаю Gradle напрямую" >&2
  exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
fi

# ── 6. Gradle есть, но Java для обёртки нет — работаем напрямую ──────────────
if [ -n "$GRADLE_BIN" ]; then
  exec "$GRADLE_BIN" -p "$APP_HOME" "$@"
fi

# ── 7. Совсем ничего нет: скачиваем дистрибутив ─────────────────────────────
if [ -z "$JAVA_BIN" ]; then
  echo "[gradlew] Не найден Java (ни в PATH, ни в JAVA_HOME, ни в Android Studio)." >&2
  echo "[gradlew] Проще всего открыть проект в Android Studio — она скачает всё сама." >&2
  exit 1
fi

DIST_HOME="$GRADLE_HOME_DIR/acidwallet-dists/$DIST_NAME"
if [ ! -x "$DIST_HOME/bin/gradle" ]; then
  echo "[gradlew] Скачиваю Gradle: $DIST_URL" >&2
  mkdir -p "$DIST_HOME"
  TMP_ZIP="$DIST_HOME/gradle.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --connect-timeout 15 --retry 2 --progress-bar -o "$TMP_ZIP" "$DIST_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$TMP_ZIP" "$DIST_URL"
  else
    echo "[gradlew] Нужен curl или wget. Откройте проект в Android Studio." >&2
    exit 1
  fi
  unzip -q -o "$TMP_ZIP" -d "$DIST_HOME.unpacked"
  rm -rf "$DIST_HOME"
  mv "$DIST_HOME.unpacked"/* "$(dirname "$DIST_HOME")/" 2>/dev/null || true
  rm -rf "$DIST_HOME.unpacked" "$TMP_ZIP"
fi

if [ ! -x "$DIST_HOME/bin/gradle" ]; then
  echo "[gradlew] Не удалось распаковать Gradle. Откройте проект в Android Studio." >&2
  exit 1
fi

# Теперь, когда Gradle есть, можно сразу сделать и стандартную обёртку.
if [ ! -f "$WRAPPER_JAR" ] && [ -n "$JAVA_BIN" ]; then
  "$DIST_HOME/bin/gradle" -p "$APP_HOME" wrapper --gradle-version "$DIST_VERSION" --distribution-type bin >&2 2>&1 || true
  if [ -f "$WRAPPER_JAR" ]; then
    exec "$JAVA_BIN" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" \
      org.gradle.wrapper.GradleWrapperMain "$@"
  fi
fi

exec "$DIST_HOME/bin/gradle" -p "$APP_HOME" "$@"
