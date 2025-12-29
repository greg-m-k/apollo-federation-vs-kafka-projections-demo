@REM Maven Wrapper script for Windows
@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar
set MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties

if exist "%MAVEN_WRAPPER_JAR%" (
    java -jar "%MAVEN_WRAPPER_JAR%" %*
) else (
    where mvn >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        mvn %*
    ) else (
        echo Error: Maven wrapper JAR not found and mvn not in PATH
        exit /b 1
    )
)
