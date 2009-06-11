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

enable_testing()
include (CTest)

set (qpidc_version 0.5)

# set(CMAKE_INCLUDE_CURRENT_DIR ON)

# Emulate "make check" behavior of the autoconf world.
add_custom_target(check ctest -V)

add_subdirectory(managementgen)
# add_subdirectory(etc)
add_subdirectory(src)
# add_subdirectory(docs/api)
# add_subdirectory(docs/man)

# if (WIN32)
#    do something Microsoft specific
# endif (WIN32)


install(FILES  LICENSE NOTICE README SSL RELEASE_NOTES DESIGN
               xml/cluster.xml INSTALL-WINDOWS
        DESTINATION .)

add_subdirectory(examples)
