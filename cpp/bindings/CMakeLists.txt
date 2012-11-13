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


include(FindSWIG)
include(UseSWIG)
include(FindRuby)
include(FindPythonLibs)
include(FindPerlLibs)

set (SWIG_MINIMUM_VERSION "1.3.32")

if (${SWIG_VERSION} VERSION_LESS ${SWIG_MINIMUM_VERSION})
  message("Found Swig < ${SWIG_MINIMUM_VERSION} - skipping language bindings")
  set(SWIG_SUFFICIENT_VERSION FALSE)
else()
  set(SWIG_SUFFICIENT_VERSION TRUE)
endif()

if (SWIG_FOUND AND SWIG_SUFFICIENT_VERSION)

    set(CMAKE_SWIG_FLAGS "-w361,362,401,467,503")

    if (PYTHONLIBS_FOUND)
        execute_process(COMMAND ${PYTHON_EXECUTABLE}
                        -c "from distutils.sysconfig import get_python_lib; print get_python_lib(True, prefix='${CMAKE_INSTALL_PREFIX}')"
                        OUTPUT_VARIABLE PYTHON_SITEARCH_PACKAGES
                        OUTPUT_STRIP_TRAILING_WHITESPACE)

        add_subdirectory(qpid/python)
        add_subdirectory(qmf2/python)
        add_subdirectory(qmf/python)
    endif (PYTHONLIBS_FOUND)

    if (RUBY_FOUND)
        execute_process(COMMAND ${RUBY_EXECUTABLE} -r rbconfig -e "puts RbConfig::CONFIG['prefix']"
                        OUTPUT_VARIABLE RUBY_PREFIX
                        OUTPUT_STRIP_TRAILING_WHITESPACE)
        string(REPLACE ${RUBY_PREFIX} ${CMAKE_INSTALL_PREFIX} RUBY_PFX_ARCH_DIR ${RUBY_ARCH_DIR})

        add_subdirectory(qpid/ruby)
        add_subdirectory(qmf2/ruby)
        add_subdirectory(qmf/ruby)
    endif (RUBY_FOUND)

    if (PERLLIBS_FOUND)
        execute_process(COMMAND ${PERL_EXECUTABLE} "-V::prefix:"
                        OUTPUT_VARIABLE QPERL_PREFIX
                        OUTPUT_STRIP_TRAILING_WHITESPACE)
        string(REGEX REPLACE "'(.*)'" "\\1" PERL_PREFIX ${QPERL_PREFIX})
        string(REPLACE ${PERL_PREFIX} ${CMAKE_INSTALL_PREFIX} PERL_PFX_ARCHLIB ${PERL_ARCHLIB})

        add_subdirectory(qpid/perl)
    endif (PERLLIBS_FOUND)
endif (SWIG_FOUND AND SWIG_SUFFICIENT_VERSION)
