@echo off
set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%
if not "%APP_HOME%"=="" set APP_HOME=%APP_HOME:~0,-1%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
