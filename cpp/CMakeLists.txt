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

# Set default build type. Must come before project() which sets default to ""
set (CMAKE_BUILD_TYPE RelWithDebInfo CACHE string
  "Build type: Debug, Release, RelWithDebInfo or MinSizeRel (default RelWithDebInfo)")
if (CMAKE_BUILD_TYPE MATCHES "Deb")
  set (has_debug_symbols " (has debug symbols)")
endif (CMAKE_BUILD_TYPE MATCHES "Deb")
message("Build type is \"${CMAKE_BUILD_TYPE}\"${has_debug_symbols}")

project(qpid-cpp)

cmake_minimum_required(VERSION 2.6 FATAL_ERROR)
if(COMMAND cmake_policy)
  cmake_policy(VERSION 2.6)
endif(COMMAND cmake_policy)

if (${CMAKE_VERSION} VERSION_LESS "2.8.0")
    set (OPTIONAL_ARG "")
else()
    set (OPTIONAL_ARG OPTIONAL)
endif()

set(CMAKE_MODULE_PATH ${CMAKE_SOURCE_DIR}/CMakeModules)

# Parse the version from QPID_VERSION.txt.
# Use the top level qpid/ file if we're in an SVN checkout, source dir otherwise.
find_file(QPID_VERSION_FILE NAMES QPID_VERSION.txt PATHS ${PROJECT_SOURCE_DIR}/.. ${PROJECT_SOURCE_DIR} NO_DEFAULT_PATH)
mark_as_advanced(QPID_VERSION_FILE)
if(NOT QPID_VERSION_FILE)
  message(FATAL_ERROR "Cannot find QPID_VERSION.txt")
endif(NOT QPID_VERSION_FILE)

file(READ ${QPID_VERSION_FILE} QPID_VERSION)
string(REGEX REPLACE "^([0-9]+)\\.([0-9]+)\n" "\\1" QPID_VERSION_MAJOR "${QPID_VERSION}")
string(REGEX REPLACE "^([0-9]+)\\.([0-9]+)\n" "\\2" QPID_VERSION_MINOR "${QPID_VERSION}")
set (QPID_VERSION_FULL "${QPID_VERSION_MAJOR}.${QPID_VERSION_MINOR}")

set (qpidc_version ${QPID_VERSION_FULL})

include(BuildInstallSettings.cmake)

enable_testing()
include (CTest)

if (MSVC)
  # Change warning C4996 from level 1 to level 4. These are real and shouldn't
  # be completely ignored, but they're pretty well checked out and will throw
  # a run-time error if violated.
  # "warning C4996: 'std::equal': Function call with parameters that may
  # be unsafe..."
  add_definitions(/w44996)
endif (MSVC)

# Overall packaging/install options.
# This section also has all the setup for various packaging-specific options.
set (CPACK_RESOURCE_FILE_LICENSE "${CMAKE_CURRENT_SOURCE_DIR}/LICENSE")
if (WIN32)
  # Include installing the MSVCRT library
  set (CMAKE_INSTALL_DEBUG_LIBRARIES ON)
  include(InstallRequiredSystemLibraries)
  set (CPACK_GENERATOR "NSIS")
  set (CPACK_NSIS_MUI_ICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-icon.ico")
  set (CPACK_NSIS_MUI_UNIICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-icon.ico")
  set (CPACK_PACKAGE_ICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-install-banner.bmp")
  set (CPACK_NSIS_URL_INFO_ABOUT "http://qpid.apache.org/")
  # Needs this to correctly set up Start menu links later.
  set (CPACK_PACKAGE_EXECUTABLES "")
endif (WIN32)

set_absolute_install_path (QPIDC_CONF_FILE ${QPID_INSTALL_CONFDIR}/qpidc.conf)
set_absolute_install_path (QPIDD_CONF_FILE ${QPID_INSTALL_CONFDIR}/qpidd.conf)

install(FILES  LICENSE NOTICE  DESTINATION ${QPID_INSTALL_DOCDIR})
install(FILES  include/qmf/qmf2.i
        DESTINATION ${QPID_INSTALL_INCLUDEDIR}/qmf)

if (WIN32)
   set (CMAKE_DEBUG_POSTFIX "d")
endif (WIN32)

# set(CMAKE_INCLUDE_CURRENT_DIR ON)
set(CPACK_SET_DESTDIR ON)
set(CPACK_PACKAGE_NAME "qpid-cpp")
set(CPACK_PACKAGE_VENDOR "Apache Software Foundation")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "Apache Qpid C++")
set(CPACK_PACKAGE_VERSION "${qpidc_version}")
set(CPACK_PACKAGE_VERSION_MAJOR "${QPID_VERSION_MAJOR}")
set(CPACK_PACKAGE_VERSION_MINOR "${QPID_VERSION_MINOR}")
set(CPACK_PACKAGE_VERSION_PATCH "0")
set(CPACK_PACKAGE_INSTALL_DIRECTORY "qpidc-${qpidc_version}")

# Add custom target for docs since we don't include a cmake file there directly.
# If we can't use OPTIONAL in the install command then we have to make the docs
# every time so that the install target succeeds
if (OPTIONAL_ARG)
  add_custom_target(docs)
else (OPTIONAL_ARG)
  add_custom_target(docs ALL)
endif (OPTIONAL_ARG)

# uninstall target
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake_uninstall.cmake.in"
    "${CMAKE_CURRENT_BINARY_DIR}/cmake_uninstall.cmake"
    IMMEDIATE @ONLY)

add_custom_target(uninstall
    COMMAND ${CMAKE_COMMAND} -P ${CMAKE_CURRENT_BINARY_DIR}/cmake_uninstall.cmake)

# Define windows versions and library helpers
include (src/msvc.cmake)

# Do not keep on linking against transitive library dependencies
# TODO Need to rework CMake files to use INTERFACE_LINK_LIBRARIES target property
# When that is done we can remove the next 4 lines completely
set (CMAKE_LINK_INTERFACE_LIBRARIES "")
if (DEFINED CMAKE_VERSION AND NOT CMAKE_VERSION VERSION_LESS "2.8.12")
  cmake_policy(SET CMP0022 OLD)
endif (DEFINED CMAKE_VERSION AND NOT CMAKE_VERSION VERSION_LESS "2.8.12")

if (CMAKE_CXX_COMPILER_ID STREQUAL GNU)
   # Warnings: Enable as many as possible, keep the code clean. Please
   # do not disable warnings or remove -Werror without discussing on
   # qpid-dev list.
   #
   # The following warnings are deliberately omitted, they warn on valid code.
   # -Wunreachable-code -Wpadded -Winline
   # -Wshadow - warns about boost headers.
   set (WARNING_FLAGS
        "-Werror -pedantic -Wall -Wextra -Wno-shadow -Wpointer-arith -Wcast-qual -Wcast-align -Wno-long-long -Wvolatile-register-var -Winvalid-pch -Wno-system-headers -Woverloaded-virtual")

   set (CATCH_UNDEFINED "-Wl,--no-undefined")
   # gcc on SunOS uses native linker whose "-z defs" is too fussy
   if (CMAKE_SYSTEM_NAME STREQUAL SunOS)
      set (CATCH_UNDEFINED "")
   endif (CMAKE_SYSTEM_NAME STREQUAL SunOS)
  set (NOSTRICT_ALIASING "-fno-strict-aliasing")
  set (COMPILER_FLAGS "-fvisibility-inlines-hidden")
   # gcc 4.1.2 on RHEL 5 needs -Wno-attributes to avoid an error that's fixed
   # in later gcc versions.
   execute_process(COMMAND ${CMAKE_CXX_COMPILER} -dumpversion
                   OUTPUT_VARIABLE GCC_VERSION)
   if (GCC_VERSION VERSION_EQUAL 4.1.2)
     message (STATUS "Cannot restrict library symbol export on gcc 4.1.2")
     set (HIDE_SYMBOL_FLAGS "-fno-visibility-inlines-hidden")
   else (GCC_VERSION VERSION_EQUAL 4.1.2)
     set (HIDE_SYMBOL_FLAGS "-fno-visibility-inlines-hidden -fvisibility=hidden")
     set (QPID_LINKMAP ${CMAKE_CURRENT_SOURCE_DIR}/src/qpid.linkmap)
     set (LINK_VERSION_SCRIPT_FLAG "-Wl,--version-script=${QPID_LINKMAP}")
   endif (GCC_VERSION VERSION_EQUAL 4.1.2)

   if (NOT CMAKE_SYSTEM_NAME STREQUAL Windows)
     add_definitions(-pthread)
     set (CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -pthread")
     set (CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${CATCH_UNDEFINED} -pthread")
     set (CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} ${CATCH_UNDEFINED} -pthread")
   endif (NOT CMAKE_SYSTEM_NAME STREQUAL Windows)
endif (CMAKE_CXX_COMPILER_ID STREQUAL GNU)

if (CMAKE_CXX_COMPILER_ID STREQUAL SunPro)
  set (COMPILER_FLAGS "-library=stlport4 -mt")
  set (WARNING_FLAGS "+w")
  set (CATCH_UNDEFINED "")
  set (HIDE_SYMBOL_FLAGS "")
endif (CMAKE_CXX_COMPILER_ID STREQUAL SunPro)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  # Allow MSVC user to select 'WinXP-SP3/Windows Server 2003' as build target version
  set (win32_winnt_default OFF)
  if (MSVC)
    set (win32_winnt_default ON)
  endif (MSVC)
  option(SET_WIN32_WINNT "In Windows-MSVC build: define _WIN32_WINNT=0x0502 to select target version: Windows XP with SP3" ${win32_winnt_default})
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

if (MSVC)
  add_definitions(
    /D "_CRT_NONSTDC_NO_WARNINGS"
    /D "NOMINMAX"
    /D "WIN32_LEAN_AND_MEAN"
    /wd4244
    /wd4800
    /wd4355
    /wd4267
  )

  if (SET_WIN32_WINNT)
    add_definitions(/D "_WIN32_WINNT=0x0502")
  endif (SET_WIN32_WINNT)

  # set the RelWithDebInfo compile/link switches to equal Release
  set (CMAKE_CXX_FLAGS_RELWITHDEBINFO "/MD /O2 /Ob2 /D NDEBUG")
  set (CMAKE_SHARED_LINKER_FLAGS_RELWITHDEBINFO "/debug /INCREMENTAL:NO")

  if (EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/bindings/qpid/dotnet/src)
    # Set the windows version for the .NET Binding cpp project
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_BINARY_DIR}/src/windows/resources" DOTNET_src)
    file(TO_CMAKE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/bindings/qpid/dotnet/src/resource1.h" DOTNET_tgt)
    file(RELATIVE_PATH DOTNET_relPathToResource ${DOTNET_src} ${DOTNET_tgt})
    configure_file(${CMAKE_CURRENT_SOURCE_DIR}/bindings/qpid/dotnet/src/org.apache.qpid.messaging.template.rc
                   ${CMAKE_CURRENT_BINARY_DIR}/src/windows/resources/org.apache.qpid.messaging.rc)
    configure_file(${CMAKE_CURRENT_SOURCE_DIR}/bindings/qpid/dotnet/src/AssemblyInfo-template.cpp
                   ${CMAKE_CURRENT_BINARY_DIR}/src/windows/generated_src/AssemblyInfo.cpp)
    # Set the windows version for the .NET Binding sessionreceiver project
    configure_file(${CMAKE_CURRENT_SOURCE_DIR}/bindings/qpid/dotnet/src/sessionreceiver/Properties/sessionreceiver-AssemblyInfo-template.cs
                   ${CMAKE_CURRENT_BINARY_DIR}/src/windows/generated_src/sessionreceiver-AssemblyInfo.cs)
  endif (EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/bindings/qpid/dotnet/src)
endif (MSVC)

# Subdirectories
add_subdirectory(managementgen)
add_subdirectory(src)
add_subdirectory(etc)
add_subdirectory(bindings)
add_subdirectory(docs/api)
add_subdirectory(docs/man)
add_subdirectory(examples)

include (CPack)

# Build type message again, last so it is visible at end of output.
message("Build type is \"${CMAKE_BUILD_TYPE}\"${has_debug_symbols}")

