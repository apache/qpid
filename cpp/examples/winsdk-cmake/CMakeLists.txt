#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# Description
#   For WinSDK:
#   Top-level CMake source to build version-independent C++ 
#   example solution and project files for Visual Studio.
#
# Usage:
#   mkdir msvc9-win32
#   cd    msvc9-win32
#   cmake -G "Visual Studio 9 2008" ..\winsdk-cmake
#   cd ..
#
#   mkdir msvc9-x64
#   cd    msvc9-x64
#   cmake -G "Visual Studio 9 2008 Win64" ..\winsdk-cmake
#   cd ..
#
#   mkdir msvc10-win32
#   cd    msvc10-win32
#   cmake -G "Visual Studio 10" ..\winsdk-cmake
#   cd ..
#
#   mkdir msvc10-x64
#   cd    msvc10-x64
#   cmake -G "Visual Studio 10 Win64" ..\winsdk-cmake
#   cd ..
#

project(examples)

set (CMAKE_VERBOSE_MAKEFILE ON)

cmake_minimum_required(VERSION 2.8.6 FATAL_ERROR)
cmake_policy (SET CMP0015 NEW)

set (CMAKE_SUPPRESS_REGENERATION TRUE)

add_definitions(
  /D "_CRT_NONSTDC_NO_WARNINGS"
  /D "NOMINMAX" 
  /D "WIN32_LEAN_AND_MEAN" 
)

set (CMAKE_DEBUG_POSTFIX "d")

include_directories      ( "../../include" )
link_directories         ( "../../lib"     )

macro(add_example_properties example)
  set_target_properties(${example} PROPERTIES OUTPUT_NAME "${example}" )
  set_target_properties(${example} PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/../../bin/${CMAKE_BUILD_TYPE})
  
  target_link_libraries(${example} qpidmessaging)
  target_link_libraries(${example} qpidcommon   )
  target_link_libraries(${example} qpidtypes    )
endmacro(add_example_properties)

macro(add_example srcdirectory example)
  add_executable(${example} ../${srcdirectory}/${example}.cpp)
  add_example_properties(${example})
endmacro(add_example)

macro(add_example_with_parser srcdirectory example)
  add_executable(${example} ../${srcdirectory}/${example}.cpp ../messaging/OptionParser.cpp)
  add_example_properties(${example})
endmacro(add_example_with_parser)

add_example_with_parser(messaging drain)
add_example_with_parser(messaging spout)

add_example(messaging map_receiver)
add_example(messaging map_sender)
add_example(messaging client)
add_example(messaging server)
