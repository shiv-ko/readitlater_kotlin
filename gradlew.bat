@echo off
setlocal

set APP_HOME=%~dp0

if defined JAVA_HOME (
    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
    set JAVA_EXE=java.exe
)

%JAVA_EXE% -version >NUL 2>&1
if ERRORLEVEL 1 (
    echo ERROR: JDK 17 が見つかりません。JAVA_HOME を設定してください。 1>&2
    exit /b 1
)

%JAVA_EXE% -Dfile.encoding=UTF-8 -Dorg.gradle.appname=gradlew -jar "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" %*

endlocal

