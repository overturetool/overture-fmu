@echo off
setlocal
if not "%~2"=="" exit /b 2
if "%~1"=="" (
    call "%~dp0pack.cmd" --name test-model
) else (
    call "%~dp0pack.cmd" "%~1" --name test-model
)
if errorlevel 1 exit /b %errorlevel%
set "VENV=%~dp0dist\.fmpy-venv"
if not exist "%VENV%\Scripts\python.exe" (
    py -3 -m venv "%VENV%"
    if errorlevel 1 exit /b 1
)
"%VENV%\Scripts\python.exe" -m pip install "fmpy[complete]"
if errorlevel 1 exit /b %errorlevel%
"%VENV%\Scripts\python.exe" -m fmpy simulate "%~dp0dist\test-model.fmu" --stop-time 8 --show-plot
exit /b %errorlevel%
