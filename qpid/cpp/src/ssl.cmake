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
# SSL/TLS CMake fragment, to be included in CMakeLists.txt
# 

# Optional SSL/TLS support. Requires Netscape Portable Runtime on Linux.

include(FindPkgConfig)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (ssl_default OFF)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (ssl_default ON)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

option(BUILD_SSL "Build with support for SSL" ${ssl_default})
if (BUILD_SSL)
  find_program (NSPR_CONFIG nspr-config)
  if (NSPR_CONFIG STREQUAL NSPR_CONFIG-NOTFOUND)
    message(FATAL_ERROR "libnspr not found, required for SSL support")
  endif (NSPR_CONFIG STREQUAL NSPR_CONFIG-NOTFOUND)
  find_program (NSS_CONFIG nss-config)
  if (NSS_CONFIG STREQUAL NSS_CONFIG-NOTFOUND)
    message(FATAL_ERROR "libnss not found, required for SSL support")
  endif (NSS_CONFIG STREQUAL NSS_CONFIG-NOTFOUND)
  # Output from nss/snpr-config ends with newline, so strip it
  execute_process (COMMAND ${NSPR_CONFIG} --cflags
                   OUTPUT_VARIABLE get_flags)
  string (STRIP ${get_flags} NSPR_CFLAGS)
  execute_process (COMMAND ${NSPR_CONFIG} --libs
                   OUTPUT_VARIABLE get_flags)
  string (STRIP ${get_flags} NSPR_LIBS)
  execute_process (COMMAND ${NSS_CONFIG} --cflags
                   OUTPUT_VARIABLE get_flags)
  string (STRIP ${get_flags} NSS_CFLAGS)
  execute_process (COMMAND ${NSS_CONFIG} --libs
                   OUTPUT_VARIABLE get_flags)
  string (STRIP ${get_flags} NSS_LIBS)

  set (sslcommon_SOURCES
       qpid/sys/ssl/check.h
       qpid/sys/ssl/check.cpp
       qpid/sys/ssl/util.h
       qpid/sys/ssl/util.cpp
       qpid/sys/ssl/SslSocket.h
       qpid/sys/ssl/SslSocket.cpp
       qpid/sys/ssl/SslIo.h
       qpid/sys/ssl/SslIo.cpp
      )

  add_library (sslcommon SHARED ${sslcommon_SOURCES})
  target_link_libraries (sslcommon qpidcommon nss3 ssl3 nspr4)
  set_target_properties (sslcommon PROPERTIES
                         VERSION ${qpidc_version}
                         COMPILE_FLAGS "${NSPR_CFLAGS} ${NSS_CFLAGS}")

  set (ssl_SOURCES
       qpid/sys/SslPlugin.cpp
       qpid/sys/ssl/SslHandler.h
       qpid/sys/ssl/SslHandler.cpp
      )
  add_library (ssl SHARED ${ssl_SOURCES})
  target_link_libraries (ssl qpidbroker sslcommon)
  set_target_properties (ssl PROPERTIES
                         VERSION ${qpidc_version}
                         COMPILE_FLAGS "${NSPR_CFLAGS} ${NSS_CFLAGS}")
  if (CMAKE_COMPILER_IS_GNUCXX)
    set_target_properties(ssl PROPERTIES
                          LINK_FLAGS -no-undefined)
  endif (CMAKE_COMPILER_IS_GNUCXX)

  add_library (sslconnector SHARED qpid/client/SslConnector.cpp)
  target_link_libraries (sslconnector qpidclient sslcommon)
  set_target_properties (sslconnector PROPERTIES VERSION ${qpidc_version})
  if (CMAKE_COMPILER_IS_GNUCXX)
    set_target_properties(sslconnector PROPERTIES
                          LINK_FLAGS -no-undefined)
  endif (CMAKE_COMPILER_IS_GNUCXX)

endif (BUILD_SSL)
