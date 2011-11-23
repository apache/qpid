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
# For making WinSDK example solution and project files:
# Top-level CMake source to build version-independent C++ 
# example solution and project files for Visual Studio.
#
# mkdir msvc9-win32
# cd    msvc9-win32
# cmake -G "Visual Studio 9 2008" ..\cmake
# cd ..
#
# mkdir msvc9-x64
# cd    msvc9-x64
# cmake -G "Visual Studio 9 2008 x64" ..\cmake
# cd ..
#

project(examples)

set (CMAKE_VERBOSE_MAKEFILE ON)  # for debugging

cmake_minimum_required(VERSION 2.4.0 FATAL_ERROR)

#
# This script supports settings for the WinSDK build and
# for a normal source build.
#
option (BUILD_WINSDK
        "Generate for the WinSDK packaged environment" ON)

set (CMAKE_SUPPRESS_REGENERATION TRUE)

add_definitions(
  /D "_CRT_NONSTDC_NO_WARNINGS"
  /D "NOMINMAX" 
  /D "WIN32_LEAN_AND_MEAN" 
)

set (CMAKE_DEBUG_POSTFIX "d")

set (CMAKE_CONFIGURATION_TYPES Debug Release)

if (BUILD_WINSDK)
    # Set for WinSDK builds
    message(STATUS "BUILD_WINSDK is ON")
    set (includeDirs            "../../include")
    set (linkDirs               "../../lib")
else (BUILD_WINSDK)
    # Set for in-source builds
    message(STATUS "BUILD_WINSDK is OFF")
    set (includeDirs            "$(QPID_ROOT)/include;../../include")
    set (linkDirs               "$(QPID_ROOT)/src/$(Configuration);../../lib")
endif (BUILD_WINSDK)


include_directories      ( ${includeDirs} )
link_directories         ( ${linkDirs}    )

macro(add_example_properties example)
  set_target_properties(${example} PROPERTIES OUTPUT_NAME "${example}" )
  IF (BUILD_WINSDK)
    set_target_properties(${example} PROPERTIES PREFIX "../../../bin/")
  ENDIF (BUILD_WINSDK)
  
  target_link_libraries(${example} qpidmessaging debug qpidmessagingd)
  target_link_libraries(${example} qpidcommon    debug qpidcommond)
  target_link_libraries(${example} qpidtypes     debug qpidtypesd)
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

if (NOT BUILD_WINSDK)
  add_example(messaging hello_world)
  add_example(messaging hello_xml)
  
  add_example(qmf-console console)
  add_example(qmf-console ping)
  add_example(qmf-console printevents)
  add_example(qmf-console queuestats)
  
endif (NOT BUILD_WINSDK)
