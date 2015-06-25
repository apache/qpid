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
set_source_files_properties(${CMAKE_CURRENT_SOURCE_DIR}/perl.i PROPERTIES CPLUSPLUS ON)
set_source_files_properties(${CMAKE_CURRENT_SOURCE_DIR}/perl.i
  PROPERTIES SWIG_FLAGS "-I${qpid-cpp_SOURCE_DIR}/include;-I${qpid-cpp_SOURCE_DIR}/include;-I${qpid-cpp_SOURCE_DIR}/bindings")

list(APPEND SWIG_MODULE_cqpid_perl_EXTRA_DEPS
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../include/qpid/qpid.i
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../include/qpid/swig_perl_typemaps.i
)
swig_add_module(cqpid_perl perl ${CMAKE_CURRENT_SOURCE_DIR}/perl.i)
swig_link_libraries(cqpid_perl qpidmessaging qpidtypes ${PERL_LIBRARY})
set_source_files_properties(${swig_generated_file_fullname} PROPERTIES COMPILE_FLAGS "${NOSTRICT_ALIASING}")

include_directories(${PERL_INCLUDE_PATH}
                    ${qpid-cpp_SOURCE_DIR}/include
                    ${qpid-cpp_SOURCE_DIR}/bindings)

##----------------------------------
## Install the complete Perl binding
##----------------------------------
## install the Perl library separate, since we need to rename the film if
# it's coming from a version of Cmake < 2.8
install(TARGETS ${SWIG_MODULE_cqpid_perl_REAL_NAME}
        RENAME cqpid_perl.so
        DESTINATION ${PERL_PFX_ARCHLIB}
        COMPONENT ${QPID_COMPONENT_CLIENT}
)

install(FILES ${CMAKE_CURRENT_BINARY_DIR}/cqpid_perl.pm
              ${CMAKE_CURRENT_SOURCE_DIR}/LICENSE
              ${CMAKE_CURRENT_SOURCE_DIR}/Makefile.PL
        DESTINATION ${PERL_PFX_ARCHLIB}
        COMPONENT ${QPID_COMPONENT_CLIENT}
        )
