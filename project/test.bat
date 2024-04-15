@echo off
if "%1"=="" goto noArgs
.\gradlew run --args="-i=%1%"
goto end
:noArgs
.\gradlew run
:end