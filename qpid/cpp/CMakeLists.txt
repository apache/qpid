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

set (QPID_VERSION_MAJOR 0)
set (QPID_VERSION_MINOR 6)
set (qpidc_version ${QPID_VERSION_MAJOR}.${QPID_VERSION_MINOR})

enable_testing()
include (CTest)

# When doing installs, there are a number of components that the item can
# be associated with. Since there may be different sets of components desired
# for the various platforms, the component names are defined here. When
# setting the COMPONENT in an install directive, use these to ensure that
# the item is installed correctly.
#
# This section also has all the setup for various packaging-specific options.
if (WIN32)
  set (CPACK_RESOURCE_FILE_LICENSE "${CMAKE_CURRENT_SOURCE_DIR}/LICENSE")
  set (CPACK_NSIS_MUI_ICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-icon.ico")
  set (CPACK_NSIS_MUI_UNIICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-icon.ico")
  set (CPACK_PACKAGE_ICON "${CMAKE_CURRENT_SOURCE_DIR}/packaging/NSIS\\\\qpid-install-banner.bmp")

  # Install types; these defines the component sets that are installed.
  # Each component (below) indicates which of these install type(s) it is
  # included in. The user can refine the components at install time.
  set (CPACK_ALL_INSTALL_TYPES Broker Development Full)

  set (QPID_COMPONENT_COMMON Common)
  set (CPACK_COMPONENT_COMMON_INSTALL_TYPES Broker Development Full)
  set (CPACK_COMPONENT_COMMON_DISPLAY_NAME "Required common runtime items")
  set (CPACK_COMPONENT_COMMON_DESCRIPTION
       "Run-time library common to all runtime components in Qpid.\n
        This item is required by both broker and client components.")

  set (QPID_COMPONENT_BROKER Broker)
  set (CPACK_COMPONENT_BROKER_DEPENDS Common)
  set (CPACK_COMPONENT_BROKER_INSTALL_TYPES Broker Full)
  set (CPACK_COMPONENT_BROKER_DISPLAY_NAME "Broker")
  set (CPACK_COMPONENT_BROKER_DESCRIPTION
       "Messaging broker; controls message flow within the system.\n
        At least one broker is required to run any messaging application.")

  set (QPID_COMPONENT_CLIENT Client)
  set (CPACK_COMPONENT_CLIENT_DEPENDS Common)
  set (CPACK_COMPONENT_CLIENT_INSTALL_TYPES Development Full)
  set (CPACK_COMPONENT_CLIENT_DISPLAY_NAME "Client runtime libraries")
  set (CPACK_COMPONENT_CLIENT_DESCRIPTION
       "Runtime library components required to build and execute a client application.")

  set (QPID_COMPONENT_CLIENT_INCLUDE ClientInclude)
  set (CPACK_COMPONENT_CLIENTINCLUDE_INSTALL_TYPES Development Full)
  set (CPACK_COMPONENT_CLIENTINCLUDE_DISPLAY_NAME
       "Client programming header files")
  set (CPACK_COMPONENT_CLIENTINCLUDE_DESCRIPTION
       "C++ header files required to build any Qpid messaging application.")

  set (QPID_COMPONENT_QMF QMF)
  set (CPACK_COMPONENT_QMF_INSTALL_TYPES Development Full)
  set (CPACK_COMPONENT_QMF_DISPLAY_NAME
       "Qpid Management Framework (QMF)")
  set (CPACK_COMPONENT_QMF_DESCRIPTION
       "QMF Agent allows you to embed QMF management in your program.\n
        QMF Console allows you to build management programs using QMF.")

  set (QPID_INSTALL_BINDIR bin CACHE STRING
       "Directory to install user executables")
  set (QPID_INSTALL_CONFDIR conf CACHE STRING
       "Directory to install configuration files")
  set (QPID_INSTALL_DATADIR conf CACHE STRING
       "Directory to install read-only arch.-independent data root")
  set (QPID_INSTALL_INCLUDEDIR include CACHE STRING
       "Directory to install programming header files")
  set (QPID_INSTALL_LIBDIR bin CACHE STRING
       "Directory to install library files")
  set (QPID_INSTALL_SBINDIR bin CACHE STRING
       "Directory to install system admin executables")
  set (QPIDC_MODULE_DIR plugins/client CACHE STRING
       "Directory to load client plug-in modules from")
  set (QPIDD_MODULE_DIR plugins/broker CACHE STRING
       "Directory to load broker plug-in modules from")
endif (WIN32)
if (CMAKE_SYSTEM_NAME STREQUAL Linux)
  # Set up install locations. Since the Linux install puts some files in
  # /etc and most in the install location, we need to use a DESTDIR build
  # rather than the usual simple use of CPACK_INSTALL_PREFIX.
  set (CPACK_SET_DESTDIR ON)

  set (QPID_COMPONENT_BROKER runtime)
  set (QPID_COMPONENT_CLIENT runtime)
  set (QPID_COMPONENT_COMMON runtime)
  set (CPACK_COMPONENT_RUNTIME_DISPLAY_NAME
       "Items required to run broker and/or client programs")
  set (QPID_COMPONENT_CLIENT_INCLUDE development)
  set (QPID_COMPONENT_QMF development)
  set (CPACK_COMPONENT_DEVELOPMENT_DISPLAY_NAME
       "Items required to build new C++ Qpid client programs")


  set (QPID_INSTALL_BINDIR bin CACHE STRING
       "Directory to install user executables")
  set (QPID_INSTALL_CONFDIR /etc/qpid CACHE STRING
       "Directory to install configuration files")
  set (QPID_INSTALL_DATADIR share/qpid CACHE STRING
       "Directory to install read-only arch.-independent data root")
  set (QPID_INSTALL_INCLUDEDIR include CACHE STRING
       "Directory to install programming header files")
  set (QPID_INSTALL_LIBDIR lib CACHE STRING
       "Directory to install library files")
  set (QPID_INSTALL_SBINDIR sbin CACHE STRING
       "Directory to install system admin executables")
  set (QPIDC_MODULE_DIR ${QPID_INSTALL_LIBDIR}/qpid/client CACHE STRING
       "Directory to load client plug-in modules from")
  set (QPIDD_MODULE_DIR ${QPID_INSTALL_LIBDIR}/qpid/daemon CACHE STRING
       "Directory to load broker plug-in modules from")
endif (CMAKE_SYSTEM_NAME STREQUAL Linux)

set (QPIDC_CONF_FILE ${QPID_INSTALL_CONFDIR}/qpidc.conf CACHE STRING
     "Name of the Qpid client configuration file")
set (QPIDD_CONF_FILE ${QPID_INSTALL_CONFDIR}/qpidd.conf CACHE STRING
     "Name of the Qpid broker configuration file")


install(FILES  LICENSE NOTICE README SSL RELEASE_NOTES DESIGN
               xml/cluster.xml INSTALL-WINDOWS
        DESTINATION ${QPID_INSTALL_DATADIR})

if (WIN32)
   set (CMAKE_DEBUG_POSTFIX "d")
endif (WIN32)

# set(CMAKE_INCLUDE_CURRENT_DIR ON)

add_subdirectory(managementgen)
add_subdirectory(etc)
add_subdirectory(src)
# add_subdirectory(docs/api)
# add_subdirectory(docs/man)
add_subdirectory(examples)

set(CPACK_PACKAGE_NAME "qpid-cpp")
set(CPACK_PACKAGE_VENDOR "Apache Software Foundation")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "Apache Qpid C++")
set(CPACK_PACKAGE_VERSION "${qpidc_version}")
set(CPACK_PACKAGE_VERSION_MAJOR "${QPID_VERSION_MAJOR}")
set(CPACK_PACKAGE_VERSION_MINOR "${QPID_VERSION_MINOR}")
set(CPACK_PACKAGE_VERSION_PATCH "0")
set(CPACK_PACKAGE_INSTALL_DIRECTORY "qpidc-${qpidc_version}")

include (CPack)
