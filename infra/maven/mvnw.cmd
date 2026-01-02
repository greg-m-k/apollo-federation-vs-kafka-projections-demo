@REM Maven Wrapper script for Windows
@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
@REM Remove trailing backslash
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%

set MAVEN_WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar
set MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties

if exist "%MAVEN_WRAPPER_JAR%" (
    java -cp "%MAVEN_WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %*
) else (
    where mvn >nul 2>nul
    if %ERRORLEVEL% equ 0 (
        mvn %*
    ) else (
        echo Error: Maven wrapper JAR not found and mvn not in PATH
        exit /b 1
    )
)
