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

if(BUILD_LEGACYSTORE)

message(STATUS "Building legacystore tests")

# Enable dashboard reporting.
include (CTest)

# Make sure that everything get built before the tests
# Need to create a var with all the necessary top level targets

# If we're linking Boost for DLLs, turn that on for the unit test too.
if (QPID_LINK_BOOST_DYNAMIC)
    add_definitions(-DBOOST_TEST_DYN_LINK)
endif (QPID_LINK_BOOST_DYNAMIC)

include_directories( ${CMAKE_CURRENT_SOURCE_DIR} )

include (FindPythonInterp)

# # Inherit environment from parent script
# set (abs_srcdir ${CMAKE_CURRENT_SOURCE_DIR})
# set (abs_builddir ${CMAKE_CURRENT_BINARY_DIR})
# set (abs_top_srcdir ${CMAKE_SOURCE_DIR})
# set (abs_top_builddir ${CMAKE_BINARY_DIR})
# set (builddir_lib_suffix "")

# If valgrind is selected in the configuration step, set up the path to it
# for CTest.
if (ENABLE_VALGRIND)
  set (MEMORYCHECK_COMMAND ${VALGRIND})
  set (MEMORYCHECK_COMMAND_OPTIONS "--gen-suppressions=all
--leak-check=full
--demangle=yes
--suppressions=${CMAKE_CURRENT_SOURCE_DIR}/.valgrind.supp
--num-callers=25
--log-file=ctest_valgrind.vglog")
endif (ENABLE_VALGRIND)

# Like this to work with cmake 2.4 on Unix
set (qpid_test_boost_libs
     ${Boost_UNIT_TEST_FRAMEWORK_LIBRARY} ${Boost_SYSTEM_LIBRARY})

#
# Unit test program
#
# Unit tests are built as a single program to reduce valgrind overhead
# when running the tests. If you want to build a subset of the tests run
# ccmake and set unit_tests_to_build to the set you want to build.
# HACK ALERT - Unit tests are built individually to resolve a conflict
# with running multiple brokers that connect to 0.0.0.0:5672 and that
# womp on each other's store directory.

#
# define_selftest
# macro to accept the name of a single source file and to create a
#  unit test executable that runs the source.
#
MACRO (define_selftest theSourceFile)
add_executable (legacystore_${theSourceFile}
            unit_test
            ${theSourceFile}
            ${platform_test_additions})
target_link_libraries (legacystore_${theSourceFile}
                       ${qpid_test_boost_libs}
                       qpidmessaging qpidbroker qmfconsole legacystore)
get_property(ls_include TARGET legacystore_${theSourceFile} PROPERTY INCLUDE_DIRECTORIES)
list(APPEND  ls_include ${abs_top_srcdir}/src/qpid/legacystore)
list(APPEND  ls_include ${abs_top_srcdir}/src/tests)
set_target_properties (legacystore_${theSourceFile} PROPERTIES
            INCLUDE_DIRECTORIES "${ls_include}"
            COMPILE_DEFINITIONS _IN_QPID_BROKER)
remember_location(legacystore_${theSourceFile})
set(test_wrap ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_test${test_script_suffix})

add_test (legacystore_${theSourceFile} ${test_wrap} ${legacystore_${theSourceFile}_LOCATION})
ENDMACRO (define_selftest)

# add_definitions(-H)

define_selftest (SimpleTest)
define_selftest (OrderingTest)
define_selftest (TransactionalTest)
define_selftest (TwoPhaseCommitTest)

#
# Other test programs
#

# This should ideally be done as part of the test run, but I don't know a way
# to get these arguments and the working directory set like Makefile.am does,
# and have that run during the test pass.
if (PYTHON_EXECUTABLE)
  set (python_bld ${CMAKE_CURRENT_BINARY_DIR}/python)
  execute_process(COMMAND ${PYTHON_EXECUTABLE} setup.py install --prefix=${pythoon_bld} --install-lib=${python_bld} --install-scripts=${python_bld}/commands
                  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}/../python)
endif (PYTHON_EXECUTABLE)

endif (BUILD_LEGACYSTORE)
