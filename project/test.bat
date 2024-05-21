@echo off
if "%1"=="" goto noArgs
.\gradlew run --args="-i=%1% -o=true -r=0"
goto end
:noArgs
.\gradlew run
:end