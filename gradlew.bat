@rem ---------------------------------------------------------------------------
@rem  gradlew.bat для проекта «ACID Wallet»
@rem
@rem  Если gradle\wrapper\gradle-wrapper.jar отсутствует, скрипт ищет
@rem  gradle в PATH, иначе просит открыть проект в Android Studio
@rem  (IDE сама скачает нужный дистрибутив Gradle).
@rem ---------------------------------------------------------------------------
@echo off
setlocal

set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"

if exist "%WRAPPER_JAR%" (
  if defined JAVA_HOME (
    set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
  ) else (
    set "JAVA_BIN=java"
  )
  "%JAVA_BIN%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  goto :eof
)

where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  echo [gradlew] gradle-wrapper.jar not found - using gradle from PATH 1>&2
  gradle %*
  goto :eof
)

echo [gradlew] gradle-wrapper.jar not found and no gradle in PATH. 1>&2
echo [gradlew] Open the project in Android Studio - it downloads Gradle automatically. 1>&2
exit /b 1
