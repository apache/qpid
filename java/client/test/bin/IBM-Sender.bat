@REM
@REM Copyright (c) 2006 The Apache Software Foundation
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@echo off
REM Script to run the Qpid Java Broker

set CMD="IBM-Sender.bat"
set JAVACLASS=JMSPerfHarness -pc JNDI -ii com.sun.jndi.fscontext.RefFSContextFactory -iu file:/C:/temp/IBMPerfTestsJNDI/ -cf amq/ConnectionFactory  -d amq/Queue -tc jms.r11.Sender -ms $MSGSIZE -mg 1000000 %*

rem Guess QPID_HOME if not defined
set CURRENT_DIR=%cd%
if not "%QPID_HOME%" == "" goto gotHome
set QPID_HOME=%CURRENT_DIR%
echo %QPID_HOME%
if exist "%QPID_HOME%\bin\%CMD%" goto okHome
cd ..
set QPID_HOME=%cd%
cd %CURRENT_DIR%
:gotHome
if exist "%QPID_HOME%\bin\%CMD%" goto okHome
echo The QPID_HOME environment variable is not defined correctly
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

set CLIENT_TEST_CLASSES=%QPID_HOME%\lib\client-test-launch.jar

echo on
"%JAVA_HOME%\bin\java" -server -Xmx1024m -DQPID_HOME="%QPID_HOME%" -cp "%CLIENT_TEST_CLASSES%" %JAVACLASS%

:end

pause