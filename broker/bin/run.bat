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
set CORE_CLASSES=..\classes;..\testclasses

set COMMONDIR=..\..\common

set COMMONLIB=%COMMONDIR%\lib\slf4j\slf4j-simple.jar;%COMMONDIR%\lib\logging-log4j\log4j-1.2.9.jar;%COMMONDIR%\lib\mina\mina-core-0.9.2.jar

set DIST=..\lib\bdb\je-3.0.12.jar;..\dist\amqpd.jar;..\..\client\dist\amqp-common.jar

set CP=%CORE_CLASSES%;%DIST%;%COMMONLIB%

"%JAVA_HOME%\bin\java" -Xmx512m -Damqj.logging.level=INFO -cp %CP% %*
