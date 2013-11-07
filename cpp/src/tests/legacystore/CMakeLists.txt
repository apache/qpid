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

if(BUILD_LEGACYSTORE AND BUILD_TESTING)

message(STATUS "Building legacystore tests")

# If we're linking Boost for DLLs, turn that on for the tests too.
if (QPID_LINK_BOOST_DYNAMIC)
    add_definitions(-DBOOST_TEST_DYN_LINK)
endif (QPID_LINK_BOOST_DYNAMIC)

include_directories( ${CMAKE_CURRENT_SOURCE_DIR} )

set(test_wrap ${shell} ${CMAKE_SOURCE_DIR}/src/tests/run_test${test_script_suffix} --build-dir=${CMAKE_BINARY_DIR})

if (BUILD_TESTING_UNITTESTS)

# Like this to work with cmake 2.4 on Unix
set (qpid_test_boost_libs
     ${Boost_UNIT_TEST_FRAMEWORK_LIBRARY} ${Boost_SYSTEM_LIBRARY})

#
# define_legacystore_test
# macro to accept the name of a single source file and to create a
#  unit test executable that runs the source.
#
MACRO (define_legacystore_test theSourceFile)
add_executable (legacystore_${theSourceFile}
            ${theSourceFile}
            unit_test
            ${platform_test_additions})
target_link_libraries (legacystore_${theSourceFile}
                       ${qpid_test_boost_libs}
                       qpidmessaging qpidtypes qpidbroker qpidcommon legacystore_shared)
set_target_properties (legacystore_${theSourceFile} PROPERTIES COMPILE_DEFINITIONS _IN_QPID_BROKER)
remember_location(legacystore_${theSourceFile})

add_test (legacystore_${theSourceFile} ${test_wrap} -- ${legacystore_${theSourceFile}_LOCATION})
ENDMACRO (define_legacystore_test)

define_legacystore_test (SimpleTest)
define_legacystore_test (OrderingTest)
define_legacystore_test (TransactionalTest)
define_legacystore_test (TwoPhaseCommitTest)

# Journal tests
MACRO (define_journal_test mainSourceFile)
if ("${ARGV1}" STREQUAL "LONG")
  set (testname "journal_long_${mainSourceFile}")
else ()
  set (testname "journal_${mainSourceFile}")
endif ()
add_executable (${testname}
                jrnl/${mainSourceFile}
                unit_test
                ${platform_test_additions})
target_link_libraries (${testname}
                       ${qpid_test_boost_libs}
                       ${clock_gettime_LIB} legacystore_shared)
if ("${ARGV1}" STREQUAL "LONG")
  set_target_properties(${testname} PROPERTIES COMPILE_DEFINITIONS LONG_TEST)
endif ()
remember_location(${testname})
add_test (${testname} ${test_wrap} -- ${${testname}_LOCATION})
unset (testname)
ENDMACRO (define_journal_test)

define_journal_test (_ut_time_ns)
define_journal_test (_ut_jexception)
define_journal_test (_ut_jerrno)
define_journal_test (_ut_rec_hdr)
define_journal_test (_ut_jinf)
define_journal_test (_ut_jdir)
define_journal_test (_ut_enq_map)
define_journal_test (_ut_txn_map)
define_journal_test (_ut_lpmgr)
define_journal_test (_st_basic)
define_journal_test (_st_basic_txn)
define_journal_test (_st_read)
define_journal_test (_st_read_txn)
define_journal_test (_st_auto_expand)
define_journal_test (_ut_lpmgr LONG)
define_journal_test (_st_basic LONG)
define_journal_test (_st_read LONG)

add_executable (jtt__ut
    jrnl/jtt/_ut_data_src.cpp
    jrnl/jtt/_ut_jrnl_init_params.cpp
    jrnl/jtt/_ut_read_arg.cpp
    jrnl/jtt/_ut_jrnl_instance.cpp
    jrnl/jtt/_ut_test_case.cpp
    jrnl/jtt/_ut_test_case_result.cpp
    jrnl/jtt/_ut_test_case_result_agregation.cpp
    jrnl/jtt/_ut_test_case_set.cpp
    jrnl/jtt/args.cpp
    jrnl/jtt/data_src.cpp
    jrnl/jtt/jrnl_init_params.cpp
    jrnl/jtt/jrnl_instance.cpp
    jrnl/jtt/read_arg.cpp
    jrnl/jtt/test_case.cpp
    jrnl/jtt/test_case_set.cpp
    jrnl/jtt/test_case_result.cpp
    jrnl/jtt/test_case_result_agregation.cpp
    unit_test.cpp)

target_link_libraries (jtt__ut
                       ${qpid_test_boost_libs}
                       ${Boost_PROGRAM_OPTIONS_LIBRARY}
                       ${clock_gettime_LIB} legacystore_shared)

add_test(journal_jtt_ut ${test_wrap} --working-dir=${CMAKE_CURRENT_SOURCE_DIR}/jrnl/jtt -- ${CMAKE_CURRENT_BINARY_DIR}/jtt__ut)

endif (BUILD_TESTING_UNITTESTS)

#
# Other test programs
#

add_executable(jtt
    jrnl/jtt/args.cpp
    jrnl/jtt/data_src.cpp
    jrnl/jtt/jrnl_init_params.cpp
    jrnl/jtt/jrnl_instance.cpp
    jrnl/jtt/main.cpp
    jrnl/jtt/read_arg.cpp
    jrnl/jtt/test_case.cpp
    jrnl/jtt/test_case_result.cpp
    jrnl/jtt/test_case_result_agregation.cpp
    jrnl/jtt/test_case_set.cpp
    jrnl/jtt/test_mgr.cpp)

target_link_libraries (jtt
                       ${Boost_PROGRAM_OPTIONS_LIBRARY}
                       ${clock_gettime_LIB} legacystore_shared)

add_test(journal_jtt ${CMAKE_CURRENT_BINARY_DIR}/jtt -c ${CMAKE_CURRENT_SOURCE_DIR}/jrnl/jtt/jtt.csv)

add_test (legacystore_python_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_python_tests${test_script_suffix})

endif (BUILD_LEGACYSTORE AND BUILD_TESTING)
