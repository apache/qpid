@REM
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM 
@REM   http://www.apache.org/licenses/LICENSE-2.0
@REM 
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM

@echo off
REM Script to run the Qpid Management Console

rem Guess QPIDMC_HOME if not defined
set CURRENT_DIR=%cd%
if not "%QPIDMC_HOME%" == "" goto gotHome
set QPIDMC_HOME=%CURRENT_DIR%
echo %QPIDMC_HOME%
if exist "%QPIDMC_HOME%\bin\qpidmc.bat" goto okHome
cd ..
set QPIDMC_HOME=%cd%
cd %CURRENT_DIR%
:gotHome
if exist "%QPIDMC_HOME%\bin\qpidmc.bat" goto okHome
echo The QPIDMC_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end
:okHome

if not "%JAVA_HOME%" == "" goto gotJavaHome
echo The JAVA_HOME environment variable is not defined
echo This environment variable is needed to run this program
goto exit
:gotJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
goto okJavaHome
:noJavaHome
echo The JAVA_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program.
goto exit
:okJavaHome

rem Slurp the command line arguments. This loop allows for an unlimited number
rem of agruments (up to the command line limit, anyway).

"%JAVA_HOME%\bin\java" -Xms40m -Xmx256m -Declipse.consoleLog=false -jar "%QPIDMC_HOME%\eclipse\startup.jar" org.eclipse.core.launcher.Main -launcher "%QPIDMC_HOME%\eclipse\eclipse" -name "Qpid Management Console" -showsplash 600 -configuration "file:%QPIDMC_HOME%\configuration" -os win32 -ws win32 -arch x86
