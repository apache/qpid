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

##------------------------------------------------------
## Use Swig to generate a literal binding to the C++ API
##------------------------------------------------------
set_source_files_properties(${CMAKE_CURRENT_SOURCE_DIR}/ruby.i PROPERTIES
    CPLUSPLUS ON
    SWIG_FLAGS "-I${qpid-cpp_SOURCE_DIR}/include;-I${qpid-cpp_SOURCE_DIR}/bindings")

if ((${CMAKE_MAJOR_VERSION} EQUAL 2) AND (${CMAKE_MINOR_VERSION} LESS 8))
  set (RUBY_INCLUDE_DIRS ${RUBY_INCLUDE_PATH})
endif ((${CMAKE_MAJOR_VERSION} EQUAL 2) AND (${CMAKE_MINOR_VERSION} LESS 8))

include_directories(${RUBY_INCLUDE_DIRS}
                    ${qpid-cpp_SOURCE_DIR}/include
                    ${qpid-cpp_SOURCE_DIR}/bindings)

list(APPEND SWIG_MODULE_cqmf2_ruby_EXTRA_DEPS
    ${CMAKE_SOURCE_DIR}/include/qmf/qmf2.i
    ${CMAKE_SOURCE_DIR}/include/qpid/swig_ruby_typemaps.i
)
swig_add_module(cqmf2_ruby ruby ${CMAKE_CURRENT_SOURCE_DIR}/ruby.i)
swig_link_libraries(cqmf2_ruby qmf2 qpidmessaging qpidtypes ${RUBY_LIBRARY})

##----------------------------------
## Install the complete Ruby binding
##----------------------------------
install(TARGETS ${SWIG_MODULE_cqmf2_ruby_REAL_NAME}
      RENAME cqmf2.so
      DESTINATION ${RUBY_PFX_ARCH_DIR}
      COMPONENT ${QPID_COMPONENT_CLIENT}
)

