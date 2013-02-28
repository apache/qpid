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

##--------------------------------------------------
## Properties used for generating the Ruby bindings.
##--------------------------------------------------
set(GEM_BINDINGS_SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ext/cqpid)
set(GEM_BINDINGS_SOURCE_FILE ${GEM_BINDINGS_SOURCE_DIR}/cqpid.cpp)
set(GEM_OUTPUT_PATH ${CMAKE_CURRENT_BINARY_DIR})
set(GEM_OUTPUT_FILE ${GEM_OUTPUT_PATH}/pkg/qpid-${qpidc_version}.0.gem)


##------------------------------------------------------
## Use Swig to generate a literal binding to the C++ API
##------------------------------------------------------
set_source_files_properties(${CMAKE_CURRENT_SOURCE_DIR}/ruby.i PROPERTIES CPLUSPLUS ON)

include_directories(${RUBY_INCLUDE_DIRS}
                    ${qpid-cpp_SOURCE_DIR}/include
                    ${qpid-cpp_SOURCE_DIR}/bindings)

swig_add_module(cqpid_ruby ruby ${CMAKE_CURRENT_SOURCE_DIR}/ruby.i)
swig_link_libraries(cqpid_ruby qpidmessaging qpidtypes qmf2 ${RUBY_LIBRARY})

set_source_files_properties(${swig_generated_file_fullname} PROPERTIES COMPILE_FLAGS "-fno-strict-aliasing")

##----------------------------------
## Install the complete Ruby binding
##----------------------------------
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/libcqpid_ruby.so
        RENAME cqpid.so
        DESTINATION ${RUBY_PFX_ARCH_DIR}
        COMPONENT ${QPID_COMPONENT_CLIENT}
        )

add_custom_command(OUTPUT ${GEM_BINDINGS_SOURCE_FILE}
                   COMMAND cp ${swig_generated_file_fullname} ${GEM_BINDINGS_SOURCE_FILE}
                   DEPENDS ${swig_generated_file_fullname}
                   )

add_custom_command(OUTPUT ${GEM_OUTPUT_FILE}
                   COMMAND OUTPUT_DIR=${GEM_OUTPUT_PATH} rake clean clobber package
                   WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
                   DEPENDS ${swig_generated_file_fullname} ${GEM_BINDINGS_SOURCE_FILE}
                   )

add_custom_target(gemfile
                  DEPENDS ${GEM_OUTPUT_FILE}
                  )

