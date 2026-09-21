@rem ---------------------------------------------------------------------------
@rem  gradlew.bat для проекта «ACID Wallet»
@rem
@rem  Скрипт ищет Gradle в таком порядке:
@rem    1) gradle\wrapper\gradle-wrapper.jar (штатная обёртка);
@rem    2) команда gradle в PATH;
@rem    3) дистрибутив, который уже скачала Android Studio
@rem       (%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\*\gradle-8.9\bin);
@rem    4) если найден Gradle, а jar нет — обёртка создаётся автоматически.
@rem
@rem  Java берётся из JAVA_HOME, из PATH или из встроенного JBR Android Studio.
@rem
@rem  В Android Studio этот скрипт не нужен: IDE сама скачает Gradle.
@rem ---------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion
set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"

set "JAVA_BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_BIN (
  for %%P in (
    "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe"
    "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
    "%ProgramFiles%\Android Studio\jbr\bin\java.exe"
    "%LOCALAPPDATA%\JetBrains\Toolbox\apps\AndroidStudio\jbr\bin\java.exe"
  ) do if exist %%P set "JAVA_BIN=%%~P"
)
if not defined JAVA_BIN (
  for /f "delims=" %%J in ('where java 2^>nul') do if not defined JAVA_BIN set "JAVA_BIN=%%J"
)

if exist "%WRAPPER_JAR%" if defined JAVA_BIN (
  "%JAVA_BIN%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)

set "GRADLE_BIN="
for /f "delims=" %%G in ('where gradle 2^>nul') do if not defined GRADLE_BIN set "GRADLE_BIN=%%G"

if not defined GRADLE_BIN (
  for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\*") do (
    if exist "%%D\gradle-8.9\bin\gradle.bat" set "GRADLE_BIN=%%D\gradle-8.9\bin\gradle.bat"
  )
)

if not defined GRADLE_BIN (
  echo [gradlew] Gradle not found: no wrapper jar, no gradle in PATH, no Android Studio distribution.
  echo [gradlew] Open the project in Android Studio - it downloads Gradle automatically.
  exit /b 1
)

if not exist "%WRAPPER_JAR%" if defined JAVA_BIN (
  echo [gradlew] gradle-wrapper.jar not found - creating the standard wrapper... 1>&2
  call "%GRADLE_BIN%" -p "%APP_HOME%" wrapper --gradle-version 8.9 --distribution-type bin 1>&2
  if exist "%WRAPPER_JAR%" if defined JAVA_BIN (
    "%JAVA_BIN%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
    exit /b %ERRORLEVEL%
  )
)

call "%GRADLE_BIN%" -p "%APP_HOME%" %*
exit /b %ERRORLEVEL%
