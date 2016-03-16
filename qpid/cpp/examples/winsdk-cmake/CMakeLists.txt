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

#
# Description
#   For WinSDK:
#   Top-level CMake source to build version-independent C++ 
#   example solution and project files for Visual Studio.
#
# Usage:
#   Target: Visual Studio 2008, 32-bit
#     cd    <WinSDK>\examples\examples-cmake
#     cmake -G "Visual Studio 9 2008" .
#
#   Target: Visual Studio 2008, 64-bit
#     cd    <WinSDK>\examples\examples-cmake
#     cmake -G "Visual Studio 9 2008 Win64" .
#
#   Target: Visual Studio 2010, 32-bit
#     cd    <WinSDK>\examples\examples-cmake
#     cmake -G "Visual Studio 10" .
#
#   Target: Visual Studio 2010, 64-bit
#     cd    <WinSDK>\examples\examples-cmake
#     cmake -G "Visual Studio 10 Win64" .
#
#   Then execute the examples.sln created by cmake to build
#   the examples in Debug or Release configurations.
#

project(examples)

set (CMAKE_VERBOSE_MAKEFILE ON)

cmake_minimum_required(VERSION 2.8.7 FATAL_ERROR)

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
  
  target_link_libraries(${example} optimized qpidmessaging debug qpidmessagingd)
  target_link_libraries(${example} optimized qpidcommon    debug qpidcommond   )
  target_link_libraries(${example} optimized qpidtypes     debug qpidtypesd    )
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
add_example(messaging server_reconnect)
add_example(messaging hello_world)
add_example(messaging hello_xml)
