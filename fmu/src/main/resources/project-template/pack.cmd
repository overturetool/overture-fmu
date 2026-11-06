@echo off
setlocal DisableDelayedExpansion
set "PROJECT_DIR=%~dp0."
set "FMU_JAR=@FMU_JAR@"
set "FIRST_ARG=%~1"
if not defined FIRST_ARG goto checkjar
if "%FIRST_ARG:~0,1%"=="-" goto checkjar
set "FMU_JAR=%~1"
shift
:checkjar
if not exist "%FMU_JAR%" (
    echo Exporter JAR not found: "%FMU_JAR%" 1>&2
    echo Usage: %~nx0 [path\to\exporter.jar] [pack-options] 1>&2
    exit /b 2
)
set "PACK_ARGS="
:arguments
if "%~1"=="" goto pack
set "PACK_ARGS=%PACK_ARGS% %1"
shift
goto arguments
:pack
java -jar "%FMU_JAR%" pack "%PROJECT_DIR%" %PACK_ARGS%
exit /b %errorlevel%
