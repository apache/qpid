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
project(qpidc_examples)
cmake_minimum_required(VERSION 2.4.0 FATAL_ERROR)
if(COMMAND cmake_policy)
  cmake_policy(SET CMP0003 NEW)
endif(COMMAND cmake_policy)

# This will probably need some fiddling to get right for installed kits.
get_directory_property(QPIDC_TOP PARENT_DIRECTORY)
include_directories(${QPIDC_TOP}/src ${QPIDC_TOP}/src/gen)
include_directories( ${Boost_INCLUDE_DIR} )
link_directories( ${Boost_LIBRARY_DIRS} )

# There are numerous duplicate names within the examples. Since all target
# names must be unique, define a macro to prepend a prefix and manage the
# actual names.
# There can be an optional arguments at the end: libs to include
macro(add_example subdir example)
  add_executable(${subdir}_${example} ${example}.cpp)
  set_target_properties(${subdir}_${example} PROPERTIES OUTPUT_NAME ${example})
  if (${ARGC} GREATER 2)
    target_link_libraries(${subdir}_${example} ${ARGN} qpidclient)
  else (${ARGC} GREATER 2)
    target_link_libraries(${subdir}_${example} qpidclient)
  endif (${ARGC} GREATER 2)
endmacro(add_example)

add_subdirectory(direct)
add_subdirectory(failover)
add_subdirectory(fanout)
add_subdirectory(pub-sub)
#add_subdirectory(qmf-agent)
add_subdirectory(qmf-console)
add_subdirectory(request-response)
add_subdirectory(tradedemo)
add_subdirectory(xml-exchange)
