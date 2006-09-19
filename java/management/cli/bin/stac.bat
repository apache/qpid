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
set COREROOT=..\..\core
set AMQROOT=..\..\..\clients_java

set CP=..\lib\jython\jython.jar
set CP=%CP%;..\dist\amqp-stac.jar
set CP=%CP%;%COREROOT%\dist\amqp-management-common.jar
set CP=%CP%;%COREROOT%\lib\log4j\log4j-1.2.9.jar
set CP=%CP%;%COREROOT%\lib\xmlbeans\jsr173_api.jar
set CP=%CP%;%COREROOT%\lib\xmlbeans\resolver.jar
set CP=%CP%;%COREROOT%\lib\xmlbeans\xbean.jar
set CP=%CP%;%COREROOT%\lib\xmlbeans\xbean_xpath.jar
set CP=%CP%;%COREROOT%\lib\xmlbeans\xmlpublic.jar
set CP=%CP%;%COREROOT%\lib\xmlbeans\saxon8.jar
set CP=%CP%;%AMQROOT%\dist\amqp-common.jar
set CP=%CP%;%AMQROOT%\dist\amqp-jms.jar
set CP=%CP%;%AMQROOT%\lib\mina\mina-0.7.3.jar
set CP=%CP%;%AMQROOT%\lib\jms\jms.jar
set CP=%CP%;%AMQROOT%\lib\util-concurrent\backport-util-concurrent.jar
set CP=%CP%;%AMQROOT%\lib\jakarta-commons\commons-collections-3.1.jar

%JAVA_HOME%\bin\java -Damqj.logging.level="ERROR" -cp %CP% org.apache.qpid.stac.Stac
