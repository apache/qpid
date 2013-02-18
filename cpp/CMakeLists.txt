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
project(qpid-cpp)

cmake_minimum_required(VERSION 2.4.0 FATAL_ERROR)
if(COMMAND cmake_policy)
  cmake_policy(SET CMP0003 NEW)
endif(COMMAND cmake_policy)

include(BuildInstallSettings.cmake)

if (${CMAKE_VERSION} VERSION_LESS "2.8.0")
    set (OPTIONAL_ARG "")
else()
    set (OPTIONAL_ARG OPTIONAL)
endif()

set (qpidc_version ${QPID_VERSION_MAJOR}.${QPID_VERSION_MINOR})

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
  include(InstallRequiredSystemLibraries)
  set (CPACK_GENERATOR "NSIS")
  set (CPACK_NSIS_MUI_ICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-icon.ico")
  set (CPACK_NSIS_MUI_UNIICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-icon.ico")
  set (CPACK_PACKAGE_ICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-install-banner.bmp")
  set (CPACK_NSIS_URL_INFO_ABOUT "http://qpid.apache.org/")
  # Needs this to correctly set up Start menu links later.
  set (CPACK_PACKAGE_EXECUTABLES "")
endif (WIN32)

set_absolute_install_path (QPIDC_CONF_PATH ${QPID_INSTALL_CONFDIR}/qpidc.conf)
set_absolute_install_path (QPIDD_CONF_PATH ${QPID_INSTALL_CONFDIR}/qpidd.conf)
set (QPIDC_CONF_FILE ${QPIDC_CONF_PATH} CACHE STRING
     "Name of the Qpid client configuration file")
set (QPIDD_CONF_FILE ${QPIDD_CONF_PATH} CACHE STRING
     "Name of the Qpid broker configuration file")

install(FILES  LICENSE NOTICE  DESTINATION ${QPID_INSTALL_DOCDIR})
install(FILES  include/qpid/qpid.i
               bindings/swig_perl_typemaps.i
               bindings/swig_python_typemaps.i
               bindings/swig_ruby_typemaps.i
               DESTINATION ${QPID_INSTALL_DATADIR}/qpid)
install(FILES  include/qmf/qmfengine.i
               include/qmf/qmf2.i
        DESTINATION ${QPID_INSTALL_INCLUDEDIR}/qmf)

if (WIN32)
   set (CMAKE_DEBUG_POSTFIX "d")
endif (WIN32)

# set(CMAKE_INCLUDE_CURRENT_DIR ON)

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

add_subdirectory(managementgen)
add_subdirectory(src)
add_subdirectory(etc)
add_subdirectory(bindings)
add_subdirectory(docs/api)
add_subdirectory(docs/man)
add_subdirectory(examples)

include (CPack)
