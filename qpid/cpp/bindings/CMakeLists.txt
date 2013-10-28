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


find_package(SWIG)

if (SWIG_FOUND)

  include(UseSWIG)
  find_package(Ruby)
  find_package(PythonLibs)
  find_package(PerlLibs)

  if ((${CMAKE_MAJOR_VERSION} EQUAL 2) AND (${CMAKE_MINOR_VERSION} LESS 8))

    if (RUBY_INCLUDE_PATH)
        set(RUBY_FOUND "TRUE")
    else()
        set(RUBY_FOUND "FALSE")
    endif (RUBY_INCLUDE_PATH)

    if (PERL_FOUND)
        # taken from Cmake 2.8 FindPerlLibs.cmake
        execute_process(
        COMMAND
            ${PERL_EXECUTABLE} -V:installarchlib
            OUTPUT_VARIABLE PERL_ARCHLIB_OUTPUT_VARIABLE
            RESULT_VARIABLE PERL_ARCHLIB_RESULT_VARIABLE
        )
        if (NOT PERL_ARCHLIB_RESULT_VARIABLE)
            string(REGEX REPLACE "install[a-z]+='([^']+)'.*" "\\1" PERL_ARCHLIB ${PERL_ARCHLIB_OUTPUT_VARIABLE})
            file(TO_CMAKE_PATH "${PERL_ARCHLIB}" PERL_ARCHLIB)
        endif ()

        IF ( PERL_INCLUDE_PATH MATCHES .*-NOTFOUND )
            EXECUTE_PROCESS ( COMMAND ${PERL_EXECUTABLE}
                              -MConfig -e "print \$Config{archlibexp}"
                              OUTPUT_VARIABLE PERL_OUTPUT
                              RESULT_VARIABLE PERL_RETURN_VALUE )
            IF ( NOT PERL_RETURN_VALUE )
                FIND_PATH ( PERL_INCLUDE_PATH perl.h ${PERL_OUTPUT}/CORE )
            ENDIF ( NOT PERL_RETURN_VALUE )
        ENDIF ( PERL_INCLUDE_PATH MATCHES .*-NOTFOUND )

        ## Try to fix failure in PERL_LIBRARY
        IF ( PERL_LIBRARY MATCHES .*-NOTFOUND )
            EXECUTE_PROCESS ( COMMAND ${PERL_EXECUTABLE}
                              -MConfig -e "print \$Config{libperl}"
                              OUTPUT_VARIABLE PERL_OUTPUT
                              RESULT_VARIABLE PERL_RETURN_VALUE )
            IF ( NOT PERL_RETURN_VALUE )
                FIND_LIBRARY ( PERL_LIBRARY NAMES ${PERL_OUTPUT}
                                            PATHS ${PERL_INCLUDE_PATH} )
            ENDIF ( NOT PERL_RETURN_VALUE )
        ENDIF ( PERL_LIBRARY MATCHES .*-NOTFOUND )

    endif (PERL_FOUND)
  endif ((${CMAKE_MAJOR_VERSION} EQUAL 2) AND (${CMAKE_MINOR_VERSION} LESS 8))

  set(CMAKE_SWIG_FLAGS "-w361,362,401,467,503")

  if (PYTHONLIBS_FOUND)
    option(BUILD_BINDING_PYTHON "Build Python bindings" ON)
  endif (PYTHONLIBS_FOUND)

  if (RUBY_FOUND)
    option(BUILD_BINDING_RUBY "Build Ruby bindings" ON)
  endif (RUBY_FOUND)

  if (PERL_FOUND)
    option(BUILD_BINDING_PERL "Build Perl bindings" ON)
  endif (PERL_FOUND)

  if (BUILD_BINDING_PYTHON)
      message(STATUS "Building Python bindings")
      execute_process(COMMAND ${PYTHON_EXECUTABLE}
                      -c "from distutils.sysconfig import get_python_lib; print get_python_lib(True, prefix='${CMAKE_INSTALL_PREFIX}')"
                      OUTPUT_VARIABLE PYTHON_SITEARCH_PACKAGES
                      OUTPUT_STRIP_TRAILING_WHITESPACE)

      add_subdirectory(qpid/python)
      add_subdirectory(qmf2/python)
  endif (BUILD_BINDING_PYTHON)

  if (BUILD_BINDING_RUBY)
      message(STATUS "Building Ruby bindings")
      execute_process(COMMAND ${RUBY_EXECUTABLE} -r rbconfig -e "puts RbConfig::CONFIG['prefix']"
                      OUTPUT_VARIABLE RUBY_PREFIX
                      OUTPUT_STRIP_TRAILING_WHITESPACE)
      string(REPLACE ${RUBY_PREFIX} ${CMAKE_INSTALL_PREFIX} RUBY_PFX_ARCH_DIR ${RUBY_SITEARCH_DIR})
#     string(REPLACE ${RUBY_PREFIX} ${CMAKE_INSTALL_PREFIX} RUBY_PFX_ARCH_DIR ${RUBY_ARCH_DIR})
      add_subdirectory(qpid/ruby)
      add_subdirectory(qmf2/ruby)
  endif (BUILD_BINDING_RUBY)

  if (BUILD_BINDING_PERL)
      message(STATUS "Building Perl bindings")
      execute_process(COMMAND ${PERL_EXECUTABLE} "-V::prefix:"
                      OUTPUT_VARIABLE QPERL_PREFIX
                      OUTPUT_STRIP_TRAILING_WHITESPACE)
      string(REGEX REPLACE "'(.*)'" "\\1" PERL_PREFIX ${QPERL_PREFIX})
      string(REPLACE ${PERL_PREFIX} ${CMAKE_INSTALL_PREFIX} PERL_PFX_ARCHLIB ${PERL_ARCHLIB})

      add_subdirectory(qpid/perl)
  endif (BUILD_BINDING_PERL)
endif (SWIG_FOUND)

add_subdirectory(qmf2/examples/cpp)
