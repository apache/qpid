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

# library versions
include (versions.cmake)

# Option to require building optional plugins
foreach (r ${REQUIRE})
  set(${r}_force ON)
  message(STATUS "Forcing ${r} to ${${r}_force}")
endforeach(r)

# Capture specified C++ compiler (if any)
if (NOT ENV_CXX)
  if (NOT "$ENV{CXX}" STREQUAL "")
    set(CXX $ENV{CXX})
  else(NOT "$ENV{CXX}" STREQUAL "")
    set(CXX ${CMAKE_CXX_COMPILER})
  endif(NOT "$ENV{CXX}" STREQUAL "")
  set(ENV_CXX ${CXX} CACHE INTERNAL "C++ compiler specified in cmake environment")
endif (NOT ENV_CXX)

include(CheckFunctionExists)
include(CheckIncludeFileCXX)
include(CheckIncludeFiles)
include(CheckIncludeFileCXX)
include(CheckLibraryExists)
include(CheckSymbolExists)
include(CheckSizetDistinct)

find_package(PkgConfig)
find_package(Ruby)
find_package(PythonInterp REQUIRED)

find_package(Doxygen)

find_program(VALGRIND_EXECUTABLE valgrind DOC "Location of the valgrind program")
mark_as_advanced(VALGRIND_EXECUTABLE)
find_package_handle_standard_args(VALGRIND DEFAULT_MSG VALGRIND_EXECUTABLE)

find_program(SASLPASSWD2_EXECUTABLE saslpasswd2 DOC "Location of the saslpasswd2 program")
mark_as_advanced(SASLPASSWD2_EXECUTABLE)

# See if Cyrus SASL is desired and available
CHECK_LIBRARY_EXISTS (sasl2 sasl_checkpass "" FOUND_SASL_LIB)
CHECK_INCLUDE_FILES (sasl/sasl.h FOUND_SASL_H)
find_package_handle_standard_args(SASL DEFAULT_MSG FOUND_SASL_LIB FOUND_SASL_H)

#set (CMAKE_VERBOSE_MAKEFILE ON)  # for debugging

# Add a test to check the exported library API against expected API symbols
MACRO (add_api_test libname)
  if (NOT CMAKE_SYSTEM_NAME STREQUAL Windows AND BUILD_TESTING)
    add_test(api_check_${libname} ${CMAKE_CURRENT_SOURCE_DIR}/check-abi "${ENV_CXX}" ${CMAKE_CURRENT_BINARY_DIR}/lib${libname}.so ${CMAKE_CURRENT_SOURCE_DIR}/lib${libname}-api-symbols.txt)
  endif (NOT CMAKE_SYSTEM_NAME STREQUAL Windows AND BUILD_TESTING)
ENDMACRO (add_api_test libname)


# check if we generate source as part of the build
#   - rubygen generates the amqp spec
#   - managementgen generates the broker management code
#
# rubygen subdir is excluded from stable distributions
# If the main AMQP spec is present, then check if ruby and python are
# present, and if any sources have changed, forcing a re-gen of source code.
find_file(QPID_AMQP_SPEC NAMES amqp.0-10-qpid-errata.stripped.xml PATHS ${qpid-cpp_SOURCE_DIR}/specs ${qpid-cpp_SOURCE_DIR}/../specs NO_DEFAULT_PATH)
mark_as_advanced(QPID_AMQP_SPEC)
if (NOT QPID_AMQP_SPEC)
  message(FATAL_ERROR "Can't find amqp 0-10 spec for framing code generation")
endif (NOT QPID_AMQP_SPEC)
if (NOT RUBY_EXECUTABLE)
  message(FATAL_ERROR "Can't locate ruby, needed to generate amqp 0-10 framing code.")
endif (NOT RUBY_EXECUTABLE)

set(specs ${QPID_AMQP_SPEC})
set(regen_amqp OFF)
set(rgen_dir ${qpid-cpp_SOURCE_DIR}/rubygen)
file(GLOB_RECURSE rgen_progs ${rgen_dir}/*.rb)
# If any of the specs, or any of the sources used to generate code, change
# then regenerate the sources.
foreach (spec_file ${specs} ${rgen_progs})
  if (${spec_file} IS_NEWER_THAN ${CMAKE_CURRENT_BINARY_DIR}/rubygen.cmake)
    set(regen_amqp ON)
  endif (${spec_file} IS_NEWER_THAN ${CMAKE_CURRENT_BINARY_DIR}/rubygen.cmake)
endforeach (spec_file ${specs})
if (regen_amqp)
  message(STATUS "Regenerating AMQP protocol sources")
  execute_process(COMMAND ${RUBY_EXECUTABLE} -I ${rgen_dir} ${rgen_dir}/generate ${CMAKE_CURRENT_BINARY_DIR} ${CMAKE_CURRENT_BINARY_DIR}/../include  ${specs} all rubygen.cmake
                  WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR})
else (regen_amqp)
  message(STATUS "No need to generate AMQP protocol sources")
endif (regen_amqp)

find_file(QPID_BROKER_MANAGEMENT_SPEC NAMES management-schema.xml PATHS ${CMAKE_CURRENT_SOURCE_DIR}/qpid/broker ${qpid-cpp_SOURCE_DIR}/../specs NO_DEFAULT_PATH)
mark_as_advanced(QPID_BROKER_MANAGEMENT_SPEC)
if (NOT QPID_BROKER_MANAGEMENT_SPEC)
  message(FATAL_ERROR "Can't find broker management spec for code generation")
endif (NOT QPID_BROKER_MANAGEMENT_SPEC)
if (NOT PYTHON_EXECUTABLE)
  message(FATAL_ERROR "Can't locate python, needed to generate broker management code.")
endif (NOT PYTHON_EXECUTABLE)
set(mgmt_specs ${QPID_BROKER_MANAGEMENT_SPEC}
               ${CMAKE_CURRENT_SOURCE_DIR}/qpid/acl/management-schema.xml
               ${CMAKE_CURRENT_SOURCE_DIR}/qpid/ha/management-schema.xml
               ${CMAKE_CURRENT_SOURCE_DIR}/qpid/legacystore/management-schema.xml
               ${CMAKE_CURRENT_SOURCE_DIR}/qpid/linearstore/management-schema.xml
)
set(mgen_dir ${qpid-cpp_SOURCE_DIR}/managementgen)
set(regen_mgmt OFF)
foreach (spec_file ${mgmt_specs})
  if (${spec_file} IS_NEWER_THAN ${CMAKE_CURRENT_BINARY_DIR}/managementgen.cmake)
    message(STATUS "${spec_file} is newer")
    set(regen_mgmt ON)
  endif (${spec_file} IS_NEWER_THAN ${CMAKE_CURRENT_BINARY_DIR}/managementgen.cmake)
endforeach (spec_file ${mgmt_specs})
if (regen_mgmt)
  message(STATUS "Regenerating Qpid Management Framework sources")
  execute_process(COMMAND ${PYTHON_EXECUTABLE} ${mgen_dir}/qmf-gen -c managementgen.cmake -b -l -q -o ${CMAKE_CURRENT_BINARY_DIR}/qmf ${mgmt_specs}
                  WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR})
else (regen_mgmt)
  message(STATUS "No need to generate Qpid Management Framework sources")
endif (regen_mgmt)

# Pull in the names of the generated files, i.e. ${rgen_framing_srcs}
include (${CMAKE_CURRENT_BINARY_DIR}/rubygen.cmake)
include (${CMAKE_CURRENT_BINARY_DIR}/managementgen.cmake)

# FindDoxygen module tries to locate doxygen and Graphviz dot
if (DOXYGEN_FOUND)
  option(BUILD_DOCS "Build user documentation" ON)
else (DOXYGEN_FOUND)
  message(STATUS "Can't locate the doxygen command; user documentation cannot be generated")
endif (DOXYGEN_FOUND)

if (VALGRIND_FOUND)
  option(ENABLE_VALGRIND "Use valgrind to detect run-time problems" ON)
endif (VALGRIND_FOUND)

option(ENABLE_WARNINGS "Enable lots of compiler warnings (recommended)" ON)
if (NOT ENABLE_WARNINGS)
  set (WARNING_FLAGS "")
endif (NOT ENABLE_WARNINGS)

set (CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMPILER_FLAGS} ${WARNING_FLAGS}")

# Expand a bit from the basic Find_Boost; be specific about what's needed.
# Boost.system is sometimes needed; it's handled separately, below.
# There may be different minimum versions of boost for Windows and Unix
if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (Boost_components program_options date_time thread)
  set (Boost_minversion 1.44)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (Boost_components program_options)
  set (Boost_minversion 1.33)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

# Visual Studio 2010 requires boost 1.45 or better.
# The choice here is to fail demanding the user to update CMake to version N
# where Boost 1.45 is supported, or we can just accept some versions using
# the Additional_versions variable.
if (NOT DEFINED Boost_ADDITIONAL_VERSIONS)
  set (Boost_ADDITIONAL_VERSIONS
        "1.45" "1.45.0" "1.46" "1.46.0" "1.47" "1.47.0"
        "1.48" "1.48.0" "1.49" "1.49.0" "1.50" "1.50.0"
        "1.51" "1.51.0" "1.52" "1.52.0" "1.53" "1.53.0"
        "1.54" "1.54.0" "1.55" "1.55.0")
endif (NOT DEFINED Boost_ADDITIONAL_VERSIONS)

# Discover Boost version
find_package(Boost ${Boost_minversion} QUIET REQUIRED)

# Boost.system was introduced at Boost 1.35; it's needed secondarily by other
# Boost libs Qpid needs, so be sure it's there.
if (Boost_VERSION GREATER 103499)
  list(APPEND Boost_components system)
endif (Boost_VERSION GREATER 103499)

# Boost.chrono was introduced at Boost 1.47; it's needed secondarily by other
# Boost libs Qpid needs on Windows, so be sure it's there on Windows.
if (Boost_VERSION GREATER 104699 AND CMAKE_SYSTEM_NAME STREQUAL Windows)
  list(APPEND Boost_components chrono)
endif (Boost_VERSION GREATER 104699 AND CMAKE_SYSTEM_NAME STREQUAL Windows)

find_package(Boost ${Boost_minversion} REQUIRED COMPONENTS ${Boost_components})
if(NOT Boost_FOUND)
  message(FATAL_ERROR "Required Boost C++ libraries not found.  Please install or try setting BOOST_ROOT")
endif(NOT Boost_FOUND)

if (BUILD_TESTING)
  set (BUILD_TESTING_UNITTESTS ON)
  find_package(Boost ${Boost_minversion} QUIET COMPONENTS unit_test_framework)
  if(NOT Boost_UNIT_TEST_FRAMEWORK_LIBRARY)
    message(STATUS "Could not find unit testing library - will not build unit tests")
    set (BUILD_TESTING_UNITTESTS OFF)
  endif(NOT Boost_UNIT_TEST_FRAMEWORK_LIBRARY)
endif (BUILD_TESTING)

# The Windows install also wants the Boost DLLs, libs and headers that the
# release is built with. The DLLs enable everything to run, and the headers
# and libs ensure that users building Qpid C++ client programs can compile
# (the C++ API still exposes Boost headers, but hopefully this will be fixed
# in the future).
#
# On Windows you can pick whether the static or dynamic versions of the libs
# are used; allow this choice to the user. Since we also install the Boost
# DLLs that are needed for the Windows package, none are needed for the
# static link case; else drop them into the install. Do this all first, since
# Boost on Windows can use automatic linking to pick up the correct
# Boost libs based on compile-time touching of the headers. Since we don't
# really need to add them to the link lines, set the names to blanks.
option(QPID_LINK_BOOST_DYNAMIC "Link with dynamic Boost libs (OFF to link static)" ON)
mark_as_advanced(QPID_LINK_BOOST_DYNAMIC)

if (MSVC)
   if (QPID_LINK_BOOST_DYNAMIC)
      add_definitions( /D BOOST_ALL_DYN_LINK)
      string (REPLACE .lib .dll
              _boost_date_time_debug ${Boost_DATE_TIME_LIBRARY_DEBUG})
      string (REPLACE .lib .dll
              _boost_date_time_release ${Boost_DATE_TIME_LIBRARY_RELEASE})
      string (REPLACE .lib .dll
              _boost_program_options_debug ${Boost_PROGRAM_OPTIONS_LIBRARY_DEBUG})
      string (REPLACE .lib .dll
              _boost_program_options_release ${Boost_PROGRAM_OPTIONS_LIBRARY_RELEASE})
      string (REPLACE .lib .dll
              _boost_thread_debug ${Boost_THREAD_LIBRARY_DEBUG})
      string (REPLACE .lib .dll
              _boost_thread_release ${Boost_THREAD_LIBRARY_RELEASE})
      if (NOT Boost_VERSION LESS 103500)
        string (REPLACE .lib .dll
                _boost_system_debug ${Boost_SYSTEM_LIBRARY_DEBUG})
        string (REPLACE .lib .dll
                _boost_system_release ${Boost_SYSTEM_LIBRARY_RELEASE})
      endif (NOT Boost_VERSION LESS 103500)
      if (NOT Boost_VERSION LESS 104700)
        string (REPLACE .lib .dll
                _boost_chrono_debug ${Boost_CHRONO_LIBRARY_DEBUG})
        string (REPLACE .lib .dll
                _boost_chrono_release ${Boost_CHRONO_LIBRARY_RELEASE})
      endif (NOT Boost_VERSION LESS 104700)
      install (PROGRAMS
               ${_boost_date_time_debug} ${_boost_date_time_release}
               ${_boost_program_options_debug} ${_boost_program_options_release}
               ${_boost_thread_debug} ${_boost_thread_release}
               ${_boost_chrono_debug} ${_boost_chrono_release}
               ${_boost_system_debug} ${_boost_system_release}
               DESTINATION ${QPID_INSTALL_BINDIR}
               COMPONENT ${QPID_COMPONENT_COMMON})
   endif (QPID_LINK_BOOST_DYNAMIC)

   set(Boost_DATE_TIME_LIBRARY "")
   set(Boost_THREAD_LIBRARY "")
   set(Boost_PROGRAM_OPTIONS_LIBRARY "")
   set(Boost_UNIT_TEST_FRAMEWORK_LIBRARY "")
   set(Boost_SYSTEM_LIBRARY "")
   set(Boost_CHRONO_LIBRARY "")
   include_directories( ${CMAKE_CURRENT_SOURCE_DIR}/windows/resources )
endif (MSVC)

include_directories( ${Boost_INCLUDE_DIR} )

include_directories( ${CMAKE_CURRENT_SOURCE_DIR} )
include_directories( ${CMAKE_CURRENT_SOURCE_DIR}/../include )
include_directories( ${CMAKE_CURRENT_BINARY_DIR} )
include_directories( ${CMAKE_CURRENT_BINARY_DIR}/../include )

link_directories( ${Boost_LIBRARY_DIRS} )

CHECK_SYMBOL_EXISTS(uuid_generate "uuid/uuid.h" UUID_GENERATE_IN_LIBC)
if (UUID_GENERATE_IN_LIBC)
  set(uuid_SRC "")
  set(uuid_LIB "")
else (UUID_GENERATE_IN_LIBC)
  CHECK_LIBRARY_EXISTS (uuid uuid_generate "" UUID_GENERATE_IN_UUID)
  if (UUID_GENERATE_IN_UUID)
    set(uuid_SRC "")
    set(uuid_LIB uuid)
  else (UUID_GENERATE_IN_UUID)
    CHECK_SYMBOL_EXISTS(uuid_create "uuid.h" UUID_CREATE_IN_LIBC)
    if (UUID_CREATE_IN_LIBC)
      set(uuid_SRC qpid/sys/FreeBSD/uuid.cpp)
      set(uuid_LIB "")
    else (UUID_CREATE_IN_LIBC)
      CHECK_SYMBOL_EXISTS(UuidToString "rpc.h" WIN_UUID)
      if (WIN_UUID)
        set(uuid_SRC qpid/sys/windows/uuid.cpp)
        set(uuid_LIB rpcrt4)
      else (WIN_UUID)
        message(FATAL_ERROR "No Uuid API found")
      endif (WIN_UUID)
    endif (UUID_CREATE_IN_LIBC)
  endif (UUID_GENERATE_IN_UUID)
endif (UUID_GENERATE_IN_LIBC)

# These dependencies aren't found on windows
if (NOT CMAKE_SYSTEM_NAME STREQUAL Windows)
  # Ensure we have clock_gettime
  CHECK_FUNCTION_EXISTS (clock_gettime CLOCK_GETTIME_IN_LIBC)
  if (NOT CLOCK_GETTIME_IN_LIBC)
  CHECK_LIBRARY_EXISTS (rt clock_gettime "" CLOCK_GETTIME_IN_RT)
  if (CLOCK_GETTIME_IN_RT)
    set(clock_gettime_LIB "rt")
  else ()
    message(FATAL_ERROR "Cannot find clock_gettime()")
  endif (CLOCK_GETTIME_IN_RT)
  endif (NOT CLOCK_GETTIME_IN_LIBC)

  # Check for header file for dtrace static probes
  check_include_files(sys/sdt.h HAVE_SDT)
  if (HAVE_SDT)
    # Only enable by default on Linux
    if (CMAKE_SYSTEM_NAME STREQUAL Linux)
    set(probes_default ON)
    endif (CMAKE_SYSTEM_NAME STREQUAL Linux)
    option(BUILD_PROBES "Build with DTrace/systemtap static probes" "${probes_default}")
  endif (HAVE_SDT)
  if (BUILD_PROBES)
    set (HAVE_SYS_SDT_H 1)
  else (HAVE_SDT)
    set (HAVE_SYS_SDT_H 0)
  endif (BUILD_PROBES)

  # Check for poll/epoll header files
  check_include_files(sys/poll.h HAVE_POLL)
  check_include_files(sys/epoll.h HAVE_EPOLL)

  # Set default poller implementation (check from general to specific to allow overriding)
  if (HAVE_POLL)
  set(poller_default poll)
  endif (HAVE_POLL)
  if (HAVE_EPOLL)
  set(poller_default epoll)
  endif (HAVE_EPOLL)
  set(QPID_POLLER ${poller_default} CACHE STRING "Poller implementation (poll/epoll)")
  mark_as_advanced(QPID_POLLER)
endif (NOT CMAKE_SYSTEM_NAME STREQUAL Windows)

check_size_t_distinct (QPID_SIZE_T_DISTINCT)

option(BUILD_SASL "Build with Cyrus SASL support" ${SASL_FOUND})
if (BUILD_SASL)
  if (NOT SASL_FOUND)
    message(FATAL_ERROR "Cyrus SASL support requested but libsasl2 libraryor headers not found")
  endif (NOT SASL_FOUND)

  set(qpidcommon_sasl_source
      qpid/sys/cyrus/CyrusSecurityLayer.h
      qpid/sys/cyrus/CyrusSecurityLayer.cpp
     )
  set(sasl_LIB sasl2)
  set(HAVE_SASL 1)
else (BUILD_SASL)
  set(HAVE_SASL 0)
endif (BUILD_SASL)
set(QPID_BROKER_SASL_NAME "qpidd" CACHE STRING "SASL app name for the qpid broker")
mark_as_advanced(QPID_BROKER_SASL_NAME)

# Optional SSL/TLS support. Requires Netscape Portable Runtime on Linux.

# According to some cmake docs this is not a reliable way to detect
# pkg-configed libraries, but it's no worse than what we did under
# autotools
pkg_check_modules(NSS nss)

set (ssl_default ${ssl_force})
if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (ssl_default ON)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
  if (NSS_FOUND)
    set (ssl_default ON)
  endif (NSS_FOUND)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

option(BUILD_SSL "Build with support for SSL" ${ssl_default})

if (BUILD_SSL)
  if (CMAKE_SYSTEM_NAME STREQUAL Windows)
    set (sslcommon_SOURCES
         qpid/sys/windows/SslAsynchIO.cpp
         qpid/sys/windows/SslCredential.cpp
         qpid/sys/windows/SslCredential.h
         qpid/sys/windows/util.cpp
         qpid/sys/windows/util.h
    )

    set (ssl_SOURCES
         qpid/broker/windows/SslProtocolFactory.cpp
    )

    set (sslconnector_SOURCES
         qpid/client/windows/SslConnector.cpp
    )
    set (ssl_INCLUDES "")
    set (ssl_LIBDIRS "")
    set (ssl_LIBS Crypt32.lib Secur32.lib)
    set (ssl_server_LIBS Crypt32.lib Secur32.lib)
  else (CMAKE_SYSTEM_NAME STREQUAL Windows)
    if (NOT NSS_FOUND)
      message(FATAL_ERROR "nss/nspr not found, required for ssl support")
    endif (NOT NSS_FOUND)

    set (sslcommon_SOURCES
         qpid/sys/ssl/check.h
         qpid/sys/ssl/check.cpp
         qpid/sys/ssl/util.h
         qpid/sys/ssl/util.cpp
         qpid/sys/ssl/SslSocket.h
         qpid/sys/ssl/SslSocket.cpp
    )

    set (ssl_SOURCES
         qpid/sys/SslPlugin.cpp
    )

    set (sslconnector_SOURCES
         qpid/client/SslConnector.cpp
         qpid/messaging/amqp/SslTransport.cpp
    )

    set (ssl_INCLUDES "${NSS_INCLUDE_DIRS}")
    set (ssl_LIBDIRS "${NSS_LIBRARY_DIRS}")
    set (ssl_LIBS "${NSS_LIBRARIES}")
    set (ssl_server_LIBS "${NSS_LIBRARIES}")
  endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

  # Add include directories and link directories for NSS
  # unfortunately this doesn't get done automatically for
  # libraries detected by FindPkgConfig
  include_directories(${ssl_INCLUDES})
  link_directories(${ssl_LIBDIRS})
endif (BUILD_SSL)

# See if XML Exchange is desired and prerequisites are available
CHECK_LIBRARY_EXISTS (xerces-c _init "" HAVE_XERCES)
CHECK_INCLUDE_FILE_CXX (xercesc/framework/MemBufInputSource.hpp HAVE_XERCES_H)
CHECK_INCLUDE_FILE_CXX (xqilla/xqilla-simple.hpp HAVE_XQILLA_H)
CHECK_INCLUDE_FILE_CXX (xqilla/ast/XQEffectiveBooleanValue.hpp HAVE_XQ_EBV)

set (xml_default ${xml_force})
if (CMAKE_SYSTEM_NAME STREQUAL Windows)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
  if (HAVE_XERCES AND HAVE_XERCES_H)
    if (HAVE_XQILLA_H)
      set (xml_default ON)
    endif (HAVE_XQILLA_H)
  endif (HAVE_XERCES AND HAVE_XERCES_H)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

option(BUILD_XML "Build with XML Exchange" ${xml_default})

if (BUILD_XML)
  if (NOT HAVE_XERCES)
    message(FATAL_ERROR "XML Exchange support requested but xerces-c library not found")
  endif (NOT HAVE_XERCES)
  if (NOT HAVE_XERCES_H)
    message(FATAL_ERROR "XML Exchange support requested but Xerces-C headers not found")
  endif (NOT HAVE_XERCES_H)
  if (NOT HAVE_XQILLA_H)
    message(FATAL_ERROR "XML Exchange support requested but XQilla headers not found")
  endif (NOT HAVE_XQILLA_H)

  if (HAVE_XQ_EBV)
    add_definitions(-DXQ_EFFECTIVE_BOOLEAN_VALUE_HPP)
  endif (HAVE_XQ_EBV)

  add_library (xml MODULE
               qpid/xml/XmlExchange.cpp
               qpid/xml/XmlExchange.h
               qpid/xml/XmlExchangePlugin.cpp)
  target_link_libraries (xml xerces-c xqilla qpidbroker qpidcommon)
  set_target_properties (xml PROPERTIES
                         PREFIX ""
                         COMPILE_DEFINITIONS _IN_QPID_BROKER)
  install (TARGETS xml
           DESTINATION ${QPIDD_MODULE_DIR}
           COMPONENT ${QPID_COMPONENT_BROKER})

  set(xml_tests XmlClientSessionTest)

endif (BUILD_XML)

# Build the ACL plugin
set (acl_default ON)

option(BUILD_ACL "Build ACL enforcement broker plugin" ${acl_default})

if (BUILD_ACL)
  set (acl_SOURCES
       qpid/acl/Acl.cpp
       qpid/acl/Acl.h
       qpid/acl/AclConnectionCounter.cpp
       qpid/acl/AclConnectionCounter.h
       qpid/acl/AclData.cpp
       qpid/acl/AclData.h
       qpid/acl/AclLexer.cpp
       qpid/acl/AclLexer.h
       qpid/acl/AclPlugin.cpp
       qpid/acl/AclReader.cpp
       qpid/acl/AclReader.h
       qpid/acl/AclResourceCounter.cpp
       qpid/acl/AclResourceCounter.h
       qpid/acl/AclValidator.cpp
       qpid/acl/AclValidator.h
      )
endif (BUILD_ACL)

set (ha_default ON)

option(BUILD_HA "Build Active-Passive HA plugin" ${ha_default})

if (BUILD_HA)
    set (ha_SOURCES
        qpid/ha/AlternateExchangeSetter.h
        qpid/ha/Backup.cpp
        qpid/ha/Backup.h
        qpid/ha/BackupConnectionExcluder.h
        qpid/ha/BrokerInfo.cpp
        qpid/ha/BrokerInfo.h
        qpid/ha/BrokerReplicator.cpp
        qpid/ha/BrokerReplicator.h
        qpid/ha/ConnectionObserver.cpp
        qpid/ha/ConnectionObserver.h
        qpid/ha/Event.cpp
        qpid/ha/Event.h
        qpid/ha/FailoverExchange.cpp
        qpid/ha/FailoverExchange.h
        qpid/ha/HaBroker.cpp
        qpid/ha/HaBroker.h
        qpid/ha/HaPlugin.cpp
        qpid/ha/IdSetter.h
        qpid/ha/LogPrefix.cpp
        qpid/ha/LogPrefix.h
        qpid/ha/Membership.cpp
        qpid/ha/Membership.h
        qpid/ha/Primary.cpp
        qpid/ha/Primary.h
        qpid/ha/PrimaryQueueLimits.h
        qpid/ha/PrimaryTxObserver.cpp
        qpid/ha/PrimaryTxObserver.h
        qpid/ha/QueueGuard.cpp
        qpid/ha/QueueGuard.h
        qpid/ha/QueueReplicator.cpp
        qpid/ha/QueueReplicator.h
        qpid/ha/QueueSnapshot.h
        qpid/ha/QueueSnapshot.h
        qpid/ha/RemoteBackup.cpp
        qpid/ha/RemoteBackup.h
        qpid/ha/ReplicatingSubscription.cpp
        qpid/ha/ReplicatingSubscription.h
        qpid/ha/ReplicationTest.cpp
        qpid/ha/ReplicationTest.h
        qpid/ha/Role.h
        qpid/ha/Settings.h
        qpid/ha/StandAlone.h
        qpid/ha/StatusCheck.cpp
        qpid/ha/StatusCheck.h
        qpid/ha/TxReplicatingSubscription.cpp
        qpid/ha/TxReplicatingSubscription.h
        qpid/ha/TxReplicator.cpp
        qpid/ha/TxReplicator.h
        qpid/ha/types.cpp
        qpid/ha/types.h
    )

    add_library (ha MODULE ${ha_SOURCES})
    target_link_libraries (ha
                           qpidtypes qpidcommon qpidbroker qpidmessaging
                           ${Boost_PROGRAM_OPTIONS_LIBRARY})
    set_target_properties (ha PROPERTIES
                           PREFIX ""
                           COMPILE_DEFINITIONS _IN_QPID_BROKER)
    install (TARGETS ha
             DESTINATION ${QPIDD_MODULE_DIR}
             COMPONENT ${QPID_COMPONENT_BROKER})
endif (BUILD_HA)

# Check for optional RDMA support requirements
include (rdma.cmake)

# Check for optional AMQP 1.0 support requirements
include (amqp.cmake)

# Check for syslog capabilities not present on all systems
check_symbol_exists (LOG_AUTHPRIV "sys/syslog.h" HAVE_LOG_AUTHPRIV)
check_symbol_exists (LOG_FTP "sys/syslog.h" HAVE_LOG_FTP)

# Set default Memory Status module (Null implementation)
set (qpid_memstat_module
  qpid/sys/MemStat.cpp
)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)

  set (qpidcommon_platform_SOURCES
    qpid/log/windows/SinkOptions.cpp
    qpid/sys/windows/AsynchIO.cpp
    qpid/sys/windows/Path.cpp
    qpid/sys/windows/FileSysDir.cpp
    qpid/sys/windows/IocpPoller.cpp
    qpid/sys/windows/IOHandle.cpp
    qpid/sys/windows/LockFile.cpp
    qpid/sys/windows/MemoryMappedFile.cpp
    qpid/sys/windows/PipeHandle.cpp
    qpid/sys/windows/PollableCondition.cpp
    qpid/sys/windows/Shlib.cpp
    qpid/sys/windows/WinSocket.cpp
    qpid/sys/windows/SocketAddress.cpp
    qpid/sys/windows/StrError.cpp
    qpid/sys/windows/SystemInfo.cpp
    qpid/sys/windows/Thread.cpp
    qpid/sys/windows/Time.cpp
    qpid/client/windows/SaslFactory.cpp
  )

  set (qpidcommon_platform_LIBS
    ${Boost_THREAD_LIBRARY}
    ${Boost_DATE_TIME_LIBRARY}
    ${Boost_SYSTEM_LIBRARY}
    ws2_32
  )

  set (qpidbroker_platform_SOURCES
    qpid/broker/windows/BrokerDefaults.cpp
    qpid/broker/windows/SaslAuthenticator.cpp
  )

  set (qpidclient_platform_SOURCES
    qpid/client/windows/ClientDllMain.cpp
  )

  set (qpidd_platform_SOURCES
    windows/QpiddBroker.cpp
    windows/SCM.cpp
  )

else (CMAKE_SYSTEM_NAME STREQUAL Windows)

  # POSIX (Non-Windows) platforms have a lot of overlap in sources; the only
  # major difference is the poller module.
  if (QPID_POLLER STREQUAL poll)
    set (qpid_poller_module
      qpid/sys/posix/PosixPoller.cpp
    )
  elseif (QPID_POLLER STREQUAL epoll)
    set (qpid_poller_module
      qpid/sys/epoll/EpollPoller.cpp
    )
  endif (QPID_POLLER STREQUAL poll)

  # Set default System Info module
  set (qpid_system_module
    qpid/sys/posix/SystemInfo.cpp
  )

  if (CMAKE_SYSTEM_NAME STREQUAL Linux)
    # On Linux override memory status module
    set (qpid_memstat_module
      qpid/sys/posix/MemStat.cpp
    )
  endif (CMAKE_SYSTEM_NAME STREQUAL Linux)

  if (CMAKE_SYSTEM_NAME STREQUAL SunOS)
    # On Solaris override the system info module
    set (qpid_system_module
      qpid/sys/solaris/SystemInfo.cpp
    )
    # On Sun we want -lpthread -lthread as the 2nd last and last libs passed to linker
    set (qpidtypes_platform_LIBS ${qpidtypes_platform_LIBS}
      pthread
      thread
    )
  endif (CMAKE_SYSTEM_NAME STREQUAL SunOS)

  if (CMAKE_SYSTEM_NAME STREQUAL AIX)
    set (qpid_system_module
      qpid/sys/aix/SystemInfo.cpp
    )
  endif (CMAKE_SYSTEM_NAME STREQUAL AIX)

  if (CMAKE_CXX_COMPILER_ID STREQUAL SunPro)
    # -lmalloc needed for mallinfo.
    set (CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -lmalloc")
    set (CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -lmalloc")
  endif (CMAKE_CXX_COMPILER_ID STREQUAL SunPro)

  set (qpidcommon_platform_SOURCES
    qpid/sys/posix/AsynchIO.cpp
    qpid/sys/posix/Condition.cpp
    qpid/sys/posix/Fork.cpp
    qpid/sys/posix/Path.cpp
    qpid/sys/posix/FileSysDir.cpp
    qpid/sys/posix/IOHandle.cpp
    qpid/sys/posix/LockFile.cpp
    qpid/sys/posix/MemoryMappedFile.cpp
    qpid/sys/posix/Mutex.cpp
    qpid/sys/posix/PipeHandle.cpp
    qpid/sys/posix/PollableCondition.cpp
    qpid/sys/posix/Shlib.cpp
    qpid/log/posix/SinkOptions.cpp
    qpid/sys/posix/BSDSocket.cpp
    qpid/sys/posix/SocketAddress.cpp
    qpid/sys/posix/StrError.cpp
    qpid/sys/posix/Thread.cpp
    qpid/sys/posix/Time.cpp
    qpid/SaslFactory.cpp

    ${qpid_system_module}
    ${qpid_poller_module}
  )

  set (qpidcommon_platform_LIBS
    "${CMAKE_DL_LIBS}"
    "${clock_gettime_LIB}"
  )

  set (qpidbroker_platform_SOURCES
     qpid/broker/Daemon.cpp
     qpid/broker/SaslAuthenticator.cpp
     qpid/broker/SignalHandler.h
     qpid/broker/SignalHandler.cpp
     qpid/broker/posix/BrokerDefaults.cpp
     qpid/broker/posix/SocketFDPlugin.cpp
  )

  set (qpidclient_platform_SOURCES
  )

  set (qpidd_platform_SOURCES
    posix/QpiddBroker.cpp
  )
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

set (qpidcommon_SOURCES
     ${rgen_framing_srcs}
     ${qpidcommon_platform_SOURCES}
     ${qpidcommon_sasl_source}
     ${sslcommon_SOURCES}
     qpid/assert.cpp
     qpid/AclHost.cpp
     qpid/Address.cpp
     qpid/DataDir.cpp
     qpid/Exception.cpp
     qpid/Modules.cpp
     qpid/Options.cpp
     qpid/Plugin.cpp
     qpid/RefCountedBuffer.cpp
     qpid/SessionState.cpp
     qpid/SessionId.cpp
     qpid/StringUtils.cpp
     qpid/Url.cpp
     qpid/UrlArray.cpp
     qpid/NullSaslClient.cpp
     qpid/NullSaslServer.cpp
     qpid/amqp_0_10/SessionHandler.cpp
     qpid/framing/AccumulatedAck.cpp
     qpid/framing/AMQBody.cpp
     qpid/framing/AMQMethodBody.cpp
     qpid/framing/AMQContentBody.cpp
     qpid/framing/AMQFrame.cpp
     qpid/framing/AMQHeaderBody.cpp
     qpid/framing/AMQHeartbeatBody.cpp
     qpid/framing/Array.cpp
     qpid/framing/Buffer.cpp
     qpid/framing/Endian.cpp
     qpid/framing/FieldTable.cpp
     qpid/framing/FieldValue.cpp
     qpid/framing/FrameSet.cpp
     qpid/framing/FrameDecoder.cpp
     qpid/framing/List.cpp
     qpid/framing/ProtocolInitiation.cpp
     qpid/framing/ProtocolVersion.cpp
     qpid/framing/SendContent.cpp
     qpid/framing/SequenceNumber.cpp
     qpid/framing/SequenceNumberSet.cpp
     qpid/framing/SequenceSet.cpp
     qpid/framing/Proxy.cpp
     qpid/framing/Uuid.cpp
     qpid/framing/TransferContent.cpp
     qpid/log/Logger.cpp
     qpid/log/Options.cpp
     qpid/log/OstreamOutput.cpp
     qpid/log/Selector.cpp
     qpid/log/Statement.cpp
     qpid/management/Buffer.cpp
     qpid/management/ConnectionSettings.cpp
     qpid/management/Mutex.cpp
     qpid/management/Manageable.cpp
     qpid/management/ManagementObject.cpp
     qpid/sys/AggregateOutput.cpp
     qpid/sys/AsynchIOHandler.cpp
     qpid/sys/Dispatcher.cpp
     qpid/sys/DispatchHandle.cpp
     qpid/sys/Runnable.cpp
     qpid/sys/Shlib.cpp
     qpid/sys/Timer.cpp
     qpid/sys/TimerWarnings.cpp
     qpid/amqp_0_10/Codecs.cpp
     qpid/amqp/CharSequence.h
     qpid/amqp/CharSequence.cpp
     qpid/amqp/DataBuilder.h
     qpid/amqp/DataBuilder.cpp
     qpid/amqp/Decoder.h
     qpid/amqp/Decoder.cpp
     qpid/amqp/Descriptor.h
     qpid/amqp/Descriptor.cpp
     qpid/amqp/Encoder.h
     qpid/amqp/Encoder.cpp
     qpid/amqp/ListBuilder.h
     qpid/amqp/ListBuilder.cpp
     qpid/amqp/MapHandler.h
     qpid/amqp/MapEncoder.h
     qpid/amqp/MapEncoder.cpp
     qpid/amqp/MapSizeCalculator.h
     qpid/amqp/MapSizeCalculator.cpp
     qpid/amqp/MapBuilder.h
     qpid/amqp/MapBuilder.cpp
     qpid/amqp/MapReader.h
     qpid/amqp/MapReader.cpp
     qpid/amqp/MessageEncoder.h
     qpid/amqp/MessageEncoder.cpp
     qpid/amqp/MessageId.h
     qpid/amqp/MessageId.cpp
     qpid/amqp/MessageReader.h
     qpid/amqp/MessageReader.cpp
     qpid/amqp/Reader.h
     qpid/amqp/Sasl.h
     qpid/amqp/Sasl.cpp
     qpid/amqp/SaslClient.h
     qpid/amqp/SaslClient.cpp
     qpid/amqp/SaslServer.h
     qpid/amqp/SaslServer.cpp
     qpid/messaging/amqp/Transport.h
     qpid/messaging/amqp/Transport.cpp
     qpid/messaging/amqp/TransportContext.h
     ${qpid_memstat_module}
)
add_msvc_version (qpidcommon library dll)

add_library (qpidcommon SHARED ${qpidcommon_SOURCES})

target_link_libraries (qpidcommon qpidtypes
                       ${qpidcommon_platform_LIBS}
                       ${Boost_PROGRAM_OPTIONS_LIBRARY}
                       "${sasl_LIB}"
                       ${ssl_LIBS})

set_target_properties (qpidcommon PROPERTIES
                       VERSION ${qpidcommon_version}
                       SOVERSION ${qpidcommon_version_major})

install (TARGETS qpidcommon
         RUNTIME DESTINATION ${QPID_INSTALL_BINDIR} COMPONENT ${QPID_COMPONENT_COMMON}
         LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_COMMON}
         ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_COMMON})
install_pdb (qpidcommon ${QPID_COMPONENT_COMMON})

set(qpidtypes_SOURCES
  qpid/types/Exception.cpp
  qpid/types/Uuid.cpp
  qpid/types/Variant.cpp
  ${uuid_SRC}
)
set_source_files_properties(
     ${qpidtypes_SOURCES}
     PROPERTIES
     COMPILE_FLAGS "${HIDE_SYMBOL_FLAGS}")

add_msvc_version (qpidtypes library dll)
add_library(qpidtypes SHARED ${qpidtypes_SOURCES})
target_link_libraries(qpidtypes "${uuid_LIB}")
set_target_properties (qpidtypes PROPERTIES
                       LINK_FLAGS "${HIDE_SYMBOL_FLAGS} ${LINK_VERSION_SCRIPT_FLAG}"
                       VERSION ${qpidtypes_version}
                       SOVERSION ${qpidtypes_version_major})

install(TARGETS qpidtypes
        RUNTIME DESTINATION ${QPID_INSTALL_BINDIR} COMPONENT ${QPID_COMPONENT_COMMON}
        LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_COMMON}
        ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_COMMON})
install_pdb (qpidtypes ${QPID_COMPONENT_COMMON})

add_api_test(qpidtypes)

set (qpidclient_SOURCES
     ${rgen_client_srcs}
     ${qpidclient_platform_SOURCES}
     ${sslconnector_SOURCES}
     qpid/client/Bounds.cpp
     qpid/client/Completion.cpp
     qpid/client/CompletionImpl.cpp
     qpid/client/Connection.cpp
     qpid/client/ConnectionHandler.cpp
     qpid/client/ConnectionImpl.cpp
     qpid/client/ConnectionSettings.cpp
     qpid/client/Connector.cpp
     qpid/client/Demux.cpp
     qpid/client/Dispatcher.cpp
     qpid/client/FailoverManager.cpp
     qpid/client/FailoverListener.cpp
     qpid/client/Future.cpp
     qpid/client/FutureCompletion.cpp
     qpid/client/FutureResult.cpp
     qpid/client/LoadPlugins.cpp
     qpid/client/LocalQueue.cpp
     qpid/client/LocalQueueImpl.cpp
     qpid/client/Message.cpp
     qpid/client/MessageImpl.cpp
     qpid/client/MessageListener.cpp
     qpid/client/MessageReplayTracker.cpp
     qpid/client/QueueOptions.cpp
     qpid/client/Results.cpp
     qpid/client/SessionBase_0_10.cpp
     qpid/client/SessionBase_0_10Access.h
     qpid/client/ConnectionAccess.h
     qpid/client/SessionImpl.cpp
     qpid/client/StateManager.cpp
     qpid/client/Subscription.cpp
     qpid/client/SubscriptionImpl.cpp
     qpid/client/SubscriptionManager.cpp
     qpid/client/SubscriptionManagerImpl.cpp
     qpid/client/TCPConnector.cpp
)
add_msvc_version (qpidclient library dll)

add_library (qpidclient SHARED ${qpidclient_SOURCES})

target_link_libraries (qpidclient qpidcommon qpidtypes
                       ${ssl_LIBS})

set_target_properties (qpidclient PROPERTIES
                       VERSION ${qpidclient_version}
                       SOVERSION ${qpidclient_version_major})

install (TARGETS qpidclient
         RUNTIME DESTINATION ${QPID_INSTALL_BINDIR} COMPONENT ${QPID_COMPONENT_CLIENT}
         LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_CLIENT}
         ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_CLIENT})
install (DIRECTORY ../include/qpid
         DESTINATION ${QPID_INSTALL_INCLUDEDIR}
         COMPONENT ${QPID_COMPONENT_CLIENT_INCLUDE}
         PATTERN ".svn" EXCLUDE)
install_pdb (qpidclient ${QPID_COMPONENT_CLIENT})

set (qpidmessaging_SOURCES
     ${amqpc_SOURCES}
     qpid/messaging/AddressImpl.h
     qpid/messaging/ConnectionImpl.h
     qpid/messaging/ReceiverImpl.h
     qpid/messaging/SessionImpl.h
     qpid/messaging/SenderImpl.h
     qpid/client/amqp0_10/AcceptTracker.h
     qpid/client/amqp0_10/AcceptTracker.cpp
     qpid/client/amqp0_10/AddressResolution.h
     qpid/client/amqp0_10/AddressResolution.cpp
     qpid/client/amqp0_10/ConnectionImpl.h
     qpid/client/amqp0_10/ConnectionImpl.cpp
     qpid/client/amqp0_10/IncomingMessages.h
     qpid/client/amqp0_10/IncomingMessages.cpp
     qpid/client/amqp0_10/MessageSink.h
     qpid/client/amqp0_10/MessageSource.h
     qpid/client/amqp0_10/OutgoingMessage.h
     qpid/client/amqp0_10/OutgoingMessage.cpp
     qpid/client/amqp0_10/ReceiverImpl.h
     qpid/client/amqp0_10/ReceiverImpl.cpp
     qpid/client/amqp0_10/SessionImpl.h
     qpid/client/amqp0_10/SessionImpl.cpp
     qpid/client/amqp0_10/SenderImpl.h
     qpid/client/amqp0_10/SenderImpl.cpp
     qpid/messaging/Address.cpp
     qpid/messaging/AddressParser.h
     qpid/messaging/AddressParser.cpp # The functions in here are not in the public interface, but qmf uses them
     qpid/messaging/Connection.cpp
     qpid/messaging/Duration.cpp
     qpid/messaging/exceptions.cpp
     qpid/messaging/FailoverUpdates.cpp
     qpid/messaging/Logger.cpp
     qpid/messaging/Message.cpp
     qpid/messaging/Receiver.cpp
     qpid/messaging/Session.cpp
     qpid/messaging/Sender.cpp
     #functions from the following are not in the public interface but are used by the AMQP 1.0 client module
     qpid/messaging/ConnectionOptions.h
     qpid/messaging/ConnectionOptions.cpp
     qpid/messaging/MessageImpl.h
     qpid/messaging/MessageImpl.cpp
     qpid/messaging/Message_io.cpp
     qpid/messaging/ProtocolRegistry.cpp
     qpid/messaging/amqp/EncodedMessage.h
     qpid/messaging/amqp/EncodedMessage.cpp
)

add_msvc_version (qpidmessaging library dll)

add_library (qpidmessaging SHARED ${qpidmessaging_SOURCES})
target_link_libraries (qpidmessaging qpidtypes qpidclient qpidcommon ${Proton_LIBRARIES})
set_target_properties (qpidmessaging PROPERTIES
                       LINK_FLAGS "${HIDE_SYMBOL_FLAGS} ${LINK_VERSION_SCRIPT_FLAG}"
                       COMPILE_FLAGS "${HIDE_SYMBOL_FLAGS}"
                       VERSION ${qpidmessaging_version}
                       SOVERSION ${qpidmessaging_version_major})
install (TARGETS qpidmessaging
         RUNTIME DESTINATION ${QPID_INSTALL_BINDIR} COMPONENT ${QPID_COMPONENT_CLIENT}
         LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_CLIENT}
         ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_CLIENT})
install_pdb (qpidmessaging ${QPID_COMPONENT_CLIENT})

add_api_test(qpidmessaging)

if (MSVC)
    # Install the DtcPlugin project and call it qpidxarm.
    set(AMQP_WCF_DIR ${qpid-cpp_SOURCE_DIR}/../wcf)
    set(qpidxarm_SOURCES ${AMQP_WCF_DIR}/src/Apache/Qpid/DtcPlugin/DtcPlugin.cpp)
    if (EXISTS ${qpidxarm_SOURCES})
        add_msvc_version (qpidxarm library dll)
        add_library (qpidxarm SHARED ${qpidxarm_SOURCES})
        target_link_libraries (qpidxarm qpidclient qpidcommon)
        install (TARGETS qpidxarm
                 RUNTIME DESTINATION ${QPID_INSTALL_BINDIR} COMPONENT ${QPID_COMPONENT_CLIENT}
                 LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_CLIENT}
                 ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_CLIENT})
        install_pdb (qpidxarm ${QPID_COMPONENT_CLIENT})
    endif (EXISTS ${qpidxarm_SOURCES})
endif (MSVC)

set (qpidbroker_SOURCES
     ${mgen_broker_cpp}
     ${qpidbroker_platform_SOURCES}
     ${acl_SOURCES}
     ${ssl_SOURCES}
     qpid/amqp_0_10/Connection.h
     qpid/amqp_0_10/Connection.cpp
     qpid/broker/AsyncCommandCallback.h
     qpid/broker/AsyncCommandCallback.cpp
     qpid/broker/Broker.cpp
     qpid/broker/Credit.cpp
     qpid/broker/Exchange.cpp
     qpid/broker/Fairshare.cpp
     qpid/broker/MessageDeque.cpp
     qpid/broker/MessageMap.cpp
     qpid/broker/ObjectFactory.h
     qpid/broker/ObjectFactory.cpp
     qpid/broker/PriorityQueue.cpp
     qpid/broker/Protocol.cpp
     qpid/broker/Queue.cpp
     qpid/broker/QueueCleaner.cpp
     qpid/broker/QueueListeners.cpp
     qpid/broker/FifoDistributor.cpp
     qpid/broker/MessageGroupManager.cpp
     qpid/broker/PersistableMessage.cpp
     qpid/broker/PersistableObject.cpp
     qpid/broker/Bridge.cpp
     qpid/broker/amqp_0_10/Connection.cpp
     qpid/broker/ConnectionHandler.cpp
     qpid/broker/DeliverableMessage.cpp
     qpid/broker/DeliveryRecord.cpp
     qpid/broker/DirectExchange.cpp
     qpid/broker/DtxAck.cpp
     qpid/broker/DtxBuffer.cpp
     qpid/broker/DtxManager.cpp
     qpid/broker/DtxTimeout.cpp
     qpid/broker/DtxWorkRecord.cpp
     qpid/broker/ExchangeRegistry.cpp
     qpid/broker/FanOutExchange.cpp
     qpid/broker/HeadersExchange.cpp
     qpid/broker/IngressCompletion.cpp
     qpid/broker/Link.cpp
     qpid/broker/LinkRegistry.cpp
     qpid/broker/LossyLvq.cpp
     qpid/broker/LossyQueue.cpp
     qpid/broker/Lvq.cpp
     qpid/broker/Message.cpp
     qpid/broker/MessageAdapter.cpp
     qpid/broker/MessageBuilder.cpp
     qpid/broker/MessageStoreModule.cpp
     qpid/broker/NameGenerator.cpp
     qpid/broker/NullMessageStore.cpp
     qpid/broker/PagedQueue.cpp
     qpid/broker/QueueBindings.cpp
     qpid/broker/QueuedMessage.cpp
     qpid/broker/QueueCursor.cpp
     qpid/broker/QueueDepth.cpp
     qpid/broker/QueueFactory.cpp
     qpid/broker/QueueRegistry.cpp
     qpid/broker/QueueSettings.cpp
     qpid/broker/QueueFlowLimit.cpp
     qpid/broker/RecoveryManagerImpl.cpp
     qpid/broker/RecoveredEnqueue.cpp
     qpid/broker/RecoveredDequeue.cpp
     qpid/broker/RetryList.cpp
     qpid/broker/SecureConnection.cpp
     qpid/broker/Selector.h
     qpid/broker/Selector.cpp
     qpid/broker/SelectorExpression.h
     qpid/broker/SelectorExpression.cpp
     qpid/broker/SelectorToken.h
     qpid/broker/SelectorToken.cpp
     qpid/broker/SelectorValue.h
     qpid/broker/SelectorValue.cpp
     qpid/broker/SelfDestructQueue.cpp
     qpid/broker/SemanticState.h
     qpid/broker/SemanticState.cpp
     qpid/broker/SessionAdapter.cpp
     qpid/broker/SessionState.h
     qpid/broker/SessionState.cpp
     qpid/broker/SessionManager.h
     qpid/broker/SessionManager.cpp
     qpid/broker/SessionContext.h
     qpid/broker/SessionHandler.h
     qpid/broker/SessionHandler.cpp
     qpid/broker/System.cpp
     qpid/broker/ThresholdAlerts.cpp
     qpid/broker/TopicExchange.cpp
     qpid/broker/TxAccept.cpp
     qpid/broker/TxBuffer.cpp
     qpid/broker/TxDequeue.h
     qpid/broker/TxDequeue.cpp
     qpid/broker/Vhost.cpp
     qpid/broker/amqp_0_10/MessageTransfer.cpp
     qpid/management/ManagementAgent.cpp
     qpid/management/ManagementDirectExchange.cpp
     qpid/management/ManagementTopicExchange.cpp
     qpid/sys/SocketTransport.cpp
     qpid/sys/TCPIOPlugin.cpp
)
add_msvc_version (qpidbroker library dll)
add_library (qpidbroker SHARED ${qpidbroker_SOURCES})

target_link_libraries (qpidbroker qpidcommon qpidtypes
                       "${sasl_LIB}"
                       ${ssl_server_LIBS})

set_target_properties (qpidbroker PROPERTIES
                       VERSION ${qpidbroker_version}
                       SOVERSION ${qpidbroker_version_major}
                       COMPILE_DEFINITIONS _IN_QPID_BROKER)
if (CMAKE_CXX_COMPILER_ID MATCHES XL)
  set_target_properties (qpidbroker PROPERTIES LINK_FLAGS -Wl,-bbigtoc)
endif (CMAKE_CXX_COMPILER_ID MATCHES XL)

if (MSVC)
  set_target_properties (qpidbroker PROPERTIES COMPILE_FLAGS /wd4290)
endif (MSVC)
install (TARGETS qpidbroker
         RUNTIME DESTINATION ${QPID_INSTALL_BINDIR} COMPONENT ${QPID_COMPONENT_BROKER}
         LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_BROKER}
         ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_BROKER})
install_pdb (qpidbroker ${QPID_COMPONENT_BROKER})


set (qpidd_SOURCES
     ${qpidd_platform_SOURCES}
     qpidd.cpp
     qpidd.h
)
add_msvc_version (qpidd application exe)
add_executable (qpidd ${qpidd_SOURCES})
target_link_libraries (qpidd qpidbroker qpidcommon)
set_target_properties (qpidd PROPERTIES COMPILE_DEFINITIONS _IN_QPID_BROKER)
install (TARGETS qpidd
         RUNTIME DESTINATION ${QPID_INSTALL_SBINDIR} COMPONENT ${QPID_COMPONENT_BROKER}
         LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_BROKER}
         ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_BROKER})
if (CPACK_GENERATOR STREQUAL "NSIS")
    set (CPACK_NSIS_MENU_LINKS
         "qpidd" "Start Qpid Broker")
endif (CPACK_GENERATOR STREQUAL "NSIS")

if (NOT WIN32)
    set (qmf2_platform_headers
        ../include/qmf/posix/EventNotifier.h
    )
    set (qmf2_platform_sources
        qmf/PosixEventNotifier.cpp
        qmf/PosixEventNotifierImpl.cpp
    )
endif (NOT WIN32)

    set (qmf2_HEADERS
        ../include/qmf/AgentEvent.h
        ../include/qmf/Agent.h
        ../include/qmf/AgentSession.h
        ../include/qmf/ConsoleEvent.h
        ../include/qmf/ConsoleSession.h
        ../include/qmf/DataAddr.h
        ../include/qmf/Data.h
        ../include/qmf/exceptions.h
        ../include/qmf/Handle.h
        ../include/qmf/ImportExport.h
        ../include/qmf/Query.h
        ../include/qmf/Schema.h
        ../include/qmf/SchemaId.h
        ../include/qmf/SchemaMethod.h
        ../include/qmf/SchemaProperty.h
        ../include/qmf/SchemaTypes.h
        ../include/qmf/Subscription.h
        ${qmf2_platform_headers}
        )

    set (qmf2_SOURCES
        ${qmf2_HEADERS}
        qmf/agentCapability.h
        qmf/Agent.cpp
        qmf/AgentEvent.cpp
        qmf/AgentEventImpl.h
        qmf/AgentImpl.h
        qmf/AgentSession.cpp
        qmf/AgentSubscription.cpp
        qmf/AgentSubscription.h
        qmf/ConsoleEvent.cpp
        qmf/ConsoleEventImpl.h
        qmf/ConsoleSession.cpp
        qmf/ConsoleSessionImpl.h
        qmf/constants.cpp
        qmf/constants.h
        qmf/DataAddr.cpp
        qmf/DataAddrImpl.h
        qmf/Data.cpp
        qmf/DataImpl.h
        qmf/EventNotifierImpl.h
        qmf/EventNotifierImpl.cpp
        qmf/exceptions.cpp
        qmf/Expression.cpp
        qmf/Expression.h
        qmf/Hash.cpp
        qmf/Hash.h
        qmf/PrivateImplRef.h
        qmf/Query.cpp
        qmf/QueryImpl.h
        qmf/Schema.cpp
        qmf/SchemaCache.cpp
        qmf/SchemaCache.h
        qmf/SchemaId.cpp
        qmf/SchemaIdImpl.h
        qmf/SchemaImpl.h
        qmf/SchemaMethod.cpp
        qmf/SchemaMethodImpl.h
        qmf/SchemaProperty.cpp
        qmf/SchemaPropertyImpl.h
        qmf/Subscription.cpp
        qmf/SubscriptionImpl.h
        ${qmf2_platform_sources}
        )

    add_msvc_version (qmf2 library dll)
    add_library (qmf2 SHARED ${qmf2_SOURCES})
    target_link_libraries (qmf2 qpidmessaging qpidtypes qpidclient qpidcommon)
    set_target_properties (qmf2 PROPERTIES
                           VERSION ${qmf2_version}
                           SOVERSION ${qmf2_version_major})
    install (TARGETS qmf2 OPTIONAL
            RUNTIME DESTINATION ${QPID_INSTALL_BINDIR} COMPONENT ${QPID_COMPONENT_QMF}
            LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_QMF}
            ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR} COMPONENT ${QPID_COMPONENT_QMF})
    install (FILES ${qmf2_HEADERS}
            DESTINATION ${QPID_INSTALL_INCLUDEDIR}/qmf
            COMPONENT ${QPID_COMPONENT_QMF})
    install_pdb (qmf2 ${QPID_COMPONENT_QMF})

#
# Legacy store
#
include (legacystore.cmake)
#
# Linear store
#
include (linearstore.cmake)

# Now create the config file from all the info learned above.
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/config.h.cmake
               ${CMAKE_CURRENT_BINARY_DIR}/config.h)
add_subdirectory(qpid/store)
add_subdirectory(tests)
add_subdirectory(tests/legacystore)
add_subdirectory(tests/linearstore)

# Support for pkg-config

# Compatible variable names used in the pkg config files also for autoconf
set (prefix ${CMAKE_INSTALL_PREFIX})
set (exec_prefix ${CMAKE_INSTALL_PREFIX})
set_absolute_install_path (libdir ${QPID_INSTALL_LIBDIR})
set_absolute_install_path (includedir ${QPID_INSTALL_INCLUDEDIR})
set (VERSION ${QPID_VERSION_FULL})

#add_custom_target(pkgconfig ALL echo DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/qpid.pc)
#add_dependencies(pkgconfig ${CMAKE_CURRENT_BINARY_DIR}/qmf2.pc)
configure_file(qpid.pc.in ${CMAKE_CURRENT_BINARY_DIR}/qpid.pc @ONLY)
configure_file(qmf2.pc.in ${CMAKE_CURRENT_BINARY_DIR}/qmf2.pc @ONLY)
install (FILES ${CMAKE_CURRENT_BINARY_DIR}/qpid.pc ${CMAKE_CURRENT_BINARY_DIR}/qmf2.pc
         DESTINATION ${QPID_INSTALL_LIBDIR}/pkgconfig
         COMPONENT ${QPID_COMPONENT_COMMON})

if (DEFINED CMAKE_IMPORT_LIBRARY_PREFIX)
set(QPIDMSGLIB ${CMAKE_IMPORT_LIBRARY_PREFIX}qpidmessaging${CMAKE_IMPORT_LIBRARY_SUFFIX})
set(QPIDMSGLIBDEBUG ${CMAKE_IMPORT_LIBRARY_PREFIX}qpidmessaging${CMAKE_DEBUG_POSTFIX}${CMAKE_IMPORT_LIBRARY_SUFFIX})
set(QPIDTYPESLIB ${CMAKE_IMPORT_LIBRARY_PREFIX}qpidtypes${CMAKE_IMPORT_LIBRARY_SUFFIX})
set(QPIDTYPESLIBDEBUG ${CMAKE_IMPORT_LIBRARY_PREFIX}qpidtypes${CMAKE_DEBUG_POSTFIX}${CMAKE_IMPORT_LIBRARY_SUFFIX})
else ()
set(QPIDMSGLIB ${CMAKE_SHARED_LIBRARY_PREFIX}qpidmessaging${CMAKE_SHARED_LIBRARY_SUFFIX})
set(QPIDMSGLIBDEBUG ${CMAKE_SHARED_LIBRARY_PREFIX}qpidmessaging${CMAKE_DEBUG_POSTFIX}${CMAKE_SHARED_LIBRARY_SUFFIX})
set(QPIDTYPESLIB ${CMAKE_SHARED_LIBRARY_PREFIX}qpidtypes${CMAKE_SHARED_LIBRARY_SUFFIX})
set(QPIDTYPESLIBDEBUG ${CMAKE_SHARED_LIBRARY_PREFIX}qpidtypes${CMAKE_DEBUG_POSTFIX}${CMAKE_SHARED_LIBRARY_SUFFIX})
endif ()

configure_file(QpidConfig.cmake.in ${CMAKE_CURRENT_BINARY_DIR}/QpidConfig.cmake @ONLY)
configure_file(QpidConfigVersion.cmake.in ${CMAKE_CURRENT_BINARY_DIR}/QpidConfigVersion.cmake @ONLY)
install (FILES
         ${CMAKE_CURRENT_BINARY_DIR}/QpidConfig.cmake
         ${CMAKE_CURRENT_BINARY_DIR}/QpidConfigVersion.cmake
         DESTINATION ${QPID_INSTALL_LIBDIR}/cmake/Qpid
         COMPONENT ${QPID_COMPONENT_COMMON})

