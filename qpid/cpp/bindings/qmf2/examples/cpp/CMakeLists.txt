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
project(qmf2_examples)
cmake_minimum_required(VERSION 2.8.7 FATAL_ERROR)

include_directories(${CMAKE_BINARY_DIR}/include)
include_directories(${CMAKE_SOURCE_DIR}/include)

# Shouldn't need this... but there are still client header inclusions
# of Boost. When building examples at an install site, the Boost files
# should be locatable aside from these settings.
# So set up to find the headers, find the libs at link time, but dynamically
# link them all and clear the CMake Boost library names to avoid adding them to
# the project files.
include_directories( ${Boost_INCLUDE_DIR} )
link_directories( ${Boost_LIBRARY_DIRS} )

# Visual Studio needs some Windows-specific simplifications.
if (MSVC)
  add_definitions( /D "NOMINMAX" /D "WIN32_LEAN_AND_MEAN" /D "BOOST_ALL_DYN_LINK" )
  # On Windows, prevent the accidental inclusion of Boost headers from
  # autolinking in the Boost libs. There should be no direct references to
  # Boost in the examples, and references via qpidclient/qpidcommon are
  # resolved in the Qpid libs.
  add_definitions( /D "BOOST_ALL_NO_LIB" )
endif (MSVC)

# There are numerous duplicate names within the examples. Since all target
# names must be unique, define a macro to prepend a prefix and manage the
# actual names.
# There can be an optional arguments at the end: libs to include
macro(add_example subdir example)
  add_executable(${subdir}_${example} ${example}.cpp)
  set_target_properties(${subdir}_${example} PROPERTIES OUTPUT_NAME ${example})
  if (${ARGC} GREATER 2)
    target_link_libraries(${subdir}_${example} ${ARGN} qpidmessaging qpidtypes
                          ${_boost_libs_needed})
  else (${ARGC} GREATER 2)
    target_link_libraries(${subdir}_${example} qpidmessaging qpidtypes
                          ${_boost_libs_needed})
  endif (${ARGC} GREATER 2)
endmacro(add_example)

macro(add_installed_example subdir example)
  add_example(${subdir} ${example} ${ARGN})

  # For installs, don't install the built example; that would be pointless.
  # Install the things a user needs to build the example on-site.
  install (FILES ${CMAKE_CURRENT_SOURCE_DIR}/${example}.cpp
           DESTINATION ${QPID_INSTALL_EXAMPLESDIR}/${subdir}
           COMPONENT ${QPID_COMPONENT_EXAMPLES})
endmacro(add_installed_example)

install (FILES ${CMAKE_CURRENT_SOURCE_DIR}/README.txt
         DESTINATION ${QPID_INSTALL_EXAMPLESDIR}
         COMPONENT ${QPID_COMPONENT_EXAMPLES})

add_installed_example(qmf2 agent qmf2)
if (NOT WIN32)
  # uses posix select()
  add_installed_example(qmf2 event_driven_list_agents qmf2)
endif (NOT WIN32)
add_installed_example(qmf2 list_agents qmf2)
add_installed_example(qmf2 print_events qmf2)

