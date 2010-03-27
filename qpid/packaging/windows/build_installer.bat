rem
rem Licensed to the Apache Software Foundation (ASF) under one
rem or more contributor license agreements.  See the NOTICE file
rem distributed with this work for additional information
rem regarding copyright ownership.  The ASF licenses this file
rem to you under the Apache License, Version 2.0 (the
rem "License"); you may not use this file except in compliance
rem with the License.  You may obtain a copy of the License at
rem
rem   http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing,
rem software distributed under the License is distributed on an
rem "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
rem KIND, either express or implied.  See the License for the
rem specific language governing permissions and limitations
rem under the License.

@set vshome="%ProgramFiles%\Microsoft Visual Studio 9.0"
if "%ProgramFiles(x86)%" == "" goto x86only
@set vshome="%ProgramFiles(x86)%\Microsoft Visual Studio 9.0"
if /i %1 == x86  goto x86
if /i %1 == x64  goto x64
echo Error in script usage. The correct usage is:
echo     %0 [arch]
echo where [arch] is: x86 ^| x64
goto :eof

:x86only
@set vsarch=x86
@set bits=32
goto run

:x86
@set vsarch=x86
@set bits=32
goto run

:x64
@set vsarch=amd64
@set bits=64

:run
rem Two environment variables need to be set:
rem    QPID_BUILD_ROOT: root of the build directory; $cwd\build unless the
rem                     build_dir property is set in msbuild properties below.
rem    BOOST_ROOT:      root of the Boost installation

set QPID_BUILD_ROOT=%CD%\build

rem If the local cmake needs help, add options to the cmake_options property.
rem For example: cmake_options="-DBOOST_INCLUDE_DIR=C:/Boost/boost-1_40 
rem -DBOOST_LIBRARYDIR=C:/Boost/boost-1_40/lib64"
call %vshome%\VC\vcvarsall.bat %vsarch%
msbuild /property:bits=%bits% installer.proj
