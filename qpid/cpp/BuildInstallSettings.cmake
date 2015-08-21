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

# Settings related to the Qpid build and install CMake/CTest/CPack procedure.
# These are used by both the C++ and WCF components.

# When doing installs, there are a number of components that the item can
# be associated with. Since there may be different sets of components desired
# for the various platforms, the component names are defined here. When
# setting the COMPONENT in an install directive, use these to ensure that
# the item is installed correctly.

if (WIN32)
  # Install types; these defines the component sets that are installed.
  # Each component (below) indicates which of these install type(s) it is
  # included in. The user can refine the components at install time.
  set (CPACK_ALL_INSTALL_TYPES Broker Development Full)

  set (QPID_COMPONENT_COMMON Common)
  set (CPACK_COMPONENT_COMMON_INSTALL_TYPES Broker Development Full)
  set (CPACK_COMPONENT_COMMON_DISPLAY_NAME "Required common runtime items")
  set (CPACK_COMPONENT_COMMON_DESCRIPTION
    "Run-time library common to all runtime components in Qpid.\nThis item is required by both broker and client components.")

  set (QPID_COMPONENT_BROKER Broker)
  set (CPACK_COMPONENT_BROKER_DEPENDS Common)
  set (CPACK_COMPONENT_BROKER_INSTALL_TYPES Broker Full)
  set (CPACK_COMPONENT_BROKER_DISPLAY_NAME "Broker")
  set (CPACK_COMPONENT_BROKER_DESCRIPTION
    "Messaging broker; controls message flow within the system.\nAt least one broker is required to run any messaging application.")

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

  set (QPID_COMPONENT_EXAMPLES Examples)
  set (CPACK_COMPONENT_EXAMPLES_INSTALL_TYPES Development Full)
  set (CPACK_COMPONENT_EXAMPLES_DISPLAY_NAME "C++ Client programming examples")
  set (CPACK_COMPONENT_EXAMPLES_DESCRIPTION
    "Example source code for using the C++ Client.")

  set (QPID_COMPONENT_QMF QMF)
  set (CPACK_COMPONENT_QMF_INSTALL_TYPES Development Full)
  set (CPACK_COMPONENT_QMF_DISPLAY_NAME
    "Qpid Management Framework (QMF)")
  set (CPACK_COMPONENT_QMF_DESCRIPTION
    "QMF Agent allows you to embed QMF management in your program.\nQMF Console allows you to build management programs using QMF.")

  set (QPID_INSTALL_BINDIR bin CACHE STRING
    "Directory to install user executables")
  set (QPID_INSTALL_CONFDIR conf CACHE STRING
    "Directory to install configuration files")
  set (QPID_INSTALL_SASLDIR conf CACHE STRING
    "Directory to install SASL configuration files")
  set (QPID_INSTALL_DATADIR conf CACHE STRING
    "Directory to install read-only arch.-independent data root")
  set (QPID_INSTALL_EXAMPLESDIR examples CACHE STRING
    "Directory to install programming examples in")
  set (QPID_INSTALL_DOCDIR docs CACHE STRING
    "Directory to install documentation")
  set (QPID_INSTALL_INCLUDEDIR include CACHE STRING
    "Directory to install programming header files")
  set (QPID_INSTALL_LIBDIR lib CACHE STRING
    "Directory to install library files")
  set (QPID_INSTALL_MANDIR docs CACHE STRING
    "Directory to install manual files")
  set (QPID_INSTALL_SBINDIR bin CACHE STRING
    "Directory to install system admin executables")
  set (QPID_INSTALL_TESTDIR bin CACHE STRING
    "Directory for test executables")
  set (QPIDC_MODULE_DIR plugins/client CACHE STRING
    "Directory to load client plug-in modules from")
  set (QPIDD_MODULE_DIR plugins/broker CACHE STRING
    "Directory to load broker plug-in modules from")

  # function to get absolute path from a variable that may be relative to the
  # install prefix - For Windows we can never know the absolute install prefix
  # as this is decided at install time so this just returns the input path
  function(set_absolute_install_path var input)
    set (${var} ${input} PARENT_SCOPE)
  endfunction(set_absolute_install_path)
endif (WIN32)

if (UNIX)
  # function to get absolute path from a variable that may be relative to the
  # install prefix
  function(set_absolute_install_path var input)
    if (${input} MATCHES "^/.*")
      set (${var} ${input} PARENT_SCOPE)
    else ()
      set (${var} ${CMAKE_INSTALL_PREFIX}/${input} PARENT_SCOPE)
    endif ()
  endfunction(set_absolute_install_path)

  # Figure out the default library suffix
  if (NOT DEFINED LIB_SUFFIX)
    get_property(LIB64 GLOBAL PROPERTY FIND_LIBRARY_USE_LIB64_PATHS)
    if (${LIB64} STREQUAL "TRUE" AND ${CMAKE_SIZEOF_VOID_P} STREQUAL "8")
      set(LIB_SUFFIX 64)
    else()
      set(LIB_SUFFIX "")
    endif()
  endif()

  # In rpm builds the build sets some variables with absolute paths:
  #  CMAKE_INSTALL_PREFIX - this is a standard cmake variable
  #  INCLUDE_INSTALL_DIR
  #  LIB_INSTALL_DIR
  #  SYSCONF_INSTALL_DIR
  #  SHARE_INSTALL_DIR
  # So make these cached variables and the specific variables non cached and
  # derived from them.
  set (INCLUDE_INSTALL_DIR include CACHE PATH "Include file directory")
  set (LIB_INSTALL_DIR lib${LIB_SUFFIX} CACHE PATH "Library object file directory")
  set (SYSCONF_INSTALL_DIR etc CACHE PATH "System read only configuration directory")
  set (SHARE_INSTALL_DIR share CACHE PATH "Shared read only data directory")
  set (DOC_INSTALL_DIR ${SHARE_INSTALL_DIR}/doc/${CMAKE_PROJECT_NAME}-${QPID_VERSION_FULL} CACHE PATH "Shared read only data directory")
  mark_as_advanced(INCLUDE_INSTALL_DIR LIB_INSTALL_DIR SYSCONF_INSTALL_DIR SHARE_INSTALL_DIR DOC_INSTALL_DIR)

  set (QPID_COMPONENT_BROKER broker)
  set (QPID_COMPONENT_CLIENT runtime)
  set (QPID_COMPONENT_COMMON runtime)
  set (CPACK_COMPONENT_RUNTIME_DISPLAY_NAME
    "Items required to run broker and/or client programs")
  set (QPID_COMPONENT_CLIENT_INCLUDE development)
  set (QPID_COMPONENT_EXAMPLES development)
  set (QPID_COMPONENT_QMF development)
  set (CPACK_COMPONENT_DEVELOPMENT_DISPLAY_NAME
    "Items required to build new C++ Qpid client programs")

  # These are always relative to $CMAKE_INSTALL_PREFIX
  set (QPID_INSTALL_BINDIR bin)
  set (QPID_INSTALL_SBINDIR sbin)
  set (QPID_INSTALL_TESTDIR libexec/qpid/tests) # Directory for test executables
  set (QPID_INSTALL_CONFDIR ${SYSCONF_INSTALL_DIR}/qpid)
  set (QPID_INSTALL_INITDDIR ${SYSCONF_INSTALL_DIR}/rc.d/init.d)
  set (QPID_INSTALL_SASLDIR ${SYSCONF_INSTALL_DIR}/sasl2)
  set (QPID_INSTALL_DATADIR ${SHARE_INSTALL_DIR}/qpid)
  set (QPID_INSTALL_EXAMPLESDIR ${SHARE_INSTALL_DIR}/qpid/examples)
  set (QPID_INSTALL_DOCDIR ${DOC_INSTALL_DIR}) # Directory to install documentation
  set (QPID_INSTALL_INCLUDEDIR ${INCLUDE_INSTALL_DIR})
  set (QPID_INSTALL_LIBDIR ${LIB_INSTALL_DIR})
  set (QPID_INSTALL_MANDIR share/man) # Directory to install manual files

  set_absolute_install_path (QPIDC_MODULE_DIR ${QPID_INSTALL_LIBDIR}/qpid/client) # Directory to load client plug-in modules from
  set_absolute_install_path (QPIDD_MODULE_DIR ${QPID_INSTALL_LIBDIR}/qpid/daemon) # Directory to load broker plug-in modules from

  #----
  # Set RPATH so that installe executables can run without setting
  # LD_LIBRARY_PATH or running ldconfig
  # (based on http://www.cmake.org/Wiki/CMake_RPATH_handling)

  # Add the automatically determined parts of the RPATH
  # which point to directories outside the build tree to the install RPATH
  set(CMAKE_INSTALL_RPATH_USE_LINK_PATH TRUE)

  # The RPATH to be used when installing, but only if it's not a system directory
  set_absolute_install_path (QPID_LIB_DIR ${QPID_INSTALL_LIBDIR})
  list(FIND CMAKE_PLATFORM_IMPLICIT_LINK_DIRECTORIES "${QPID_LIB_DIR}" isSystemDir)
  if("${isSystemDir}" STREQUAL "-1")
    set(CMAKE_INSTALL_RPATH "${QPID_LIB_DIR}")
  endif("${isSystemDir}" STREQUAL "-1")
endif (UNIX)
