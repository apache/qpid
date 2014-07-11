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

# Make sure that everything get built before the tests
# Need to create a var with all the necessary top level targets

# If we're linking Boost for DLLs, turn that on for the unit test too.
if (QPID_LINK_BOOST_DYNAMIC)
    add_definitions(-DBOOST_TEST_DYN_LINK)
endif (QPID_LINK_BOOST_DYNAMIC)

include_directories( ${CMAKE_CURRENT_SOURCE_DIR} )

# Using the Boost DLLs triggers warning 4275 on Visual Studio
# (non dll-interface class used as base for dll-interface class).
# This is ok, so suppress the warning.
# Also, boost lengthy names trigger warning 4503, decorated name length exceeded
# and using getenv() triggers insecure CRT warnings which we can silence in the
# test environment.
if (MSVC)
  add_definitions( /wd4275 /wd4503 /D_CRT_SECURE_NO_WARNINGS)
endif (MSVC)

# Macro to make it easier to remember where the tests are built
macro(remember_location testname)
  set (${testname}_LOCATION ${CMAKE_CURRENT_BINARY_DIR}/${testname}${CMAKE_EXECUTABLE_SUFFIX})
endmacro(remember_location)

# If we're using GCC allow variadic macros (even though they're c99 not c++01)
if (CMAKE_COMPILER_IS_GNUCXX)
  add_definitions(-Wno-variadic-macros)
endif (CMAKE_COMPILER_IS_GNUCXX)

# Windows uses some process-startup calls to ensure that errors, etc. don't
# result in error boxes being thrown up. Since it's expected that most test
# runs will be in scripts, the default is to force these outputs to stderr
# instead of windows. If you want to remove this code, build without the
# QPID_WINDOWS_DEFAULT_TEST_OUTPUTS ON.
if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  option(QPID_WINDOWS_DEFAULT_TEST_OUTPUTS "Use default error-handling on Windows tests" OFF)
  if (NOT QPID_WINDOWS_DEFAULT_TEST_OUTPUTS)
    set(platform_test_additions windows/DisableWin32ErrorWindows.cpp)
  endif (NOT QPID_WINDOWS_DEFAULT_TEST_OUTPUTS)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

# Some generally useful utilities that just happen to be built in the test area
add_executable (qpid-receive qpid-receive.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries (qpid-receive qpidmessaging qpidtypes qpidcommon)
remember_location(qpid-receive)

add_executable (qpid-send qpid-send.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries (qpid-send qpidmessaging qpidtypes qpidcommon)
remember_location(qpid-send)

install (TARGETS
         qpid-receive qpid-send
         RUNTIME DESTINATION ${QPID_INSTALL_BINDIR})

add_executable (qpid-perftest qpid-perftest.cpp ${platform_test_additions})
target_link_libraries (qpid-perftest qpidclient qpidcommon ${Boost_PROGRAM_OPTIONS_LIBRARY})
remember_location(qpid-perftest)

add_executable (qpid-latency-test qpid-latency-test.cpp ${platform_test_additions})
target_link_libraries (qpid-latency-test qpidclient qpidcommon)
remember_location(qpid-latency-test)

add_executable (qpid-client-test qpid-client-test.cpp ${platform_test_additions})
target_link_libraries (qpid-client-test qpidclient qpidcommon)
remember_location(qpid-client-test)

add_executable (qpid-ping qpid-ping.cpp ${platform_test_additions})
target_link_libraries (qpid-ping qpidmessaging qpidtypes qpidcommon)
remember_location(qpid-ping)

add_executable (qpid-topic-listener qpid-topic-listener.cpp ${platform_test_additions})
target_link_libraries (qpid-topic-listener qpidclient qpidcommon)
remember_location(qpid-topic-listener)

add_executable (qpid-topic-publisher qpid-topic-publisher.cpp ${platform_test_additions})
target_link_libraries (qpid-topic-publisher qpidclient qpidcommon)
remember_location(qpid-topic-publisher)

add_executable (receiver receiver.cpp ${platform_test_additions})
target_link_libraries (receiver qpidclient qpidcommon)
remember_location(receiver)

# This is bizarre - using both messaging and client libraries
add_executable (sender sender.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries (sender qpidmessaging qpidtypes qpidclient qpidcommon)
remember_location(sender)

add_executable (qpid-txtest qpid-txtest.cpp ${platform_test_additions})
target_link_libraries (qpid-txtest qpidclient qpidcommon qpidtypes)
#qpid_txtest_SOURCES=qpid-txtest.cpp  TestOptions.h ConnectionOptions.h
remember_location(qpid-txtest)

add_executable (qpid-txtest2 qpid-txtest2.cpp ${platform_test_additions})
target_link_libraries (qpid-txtest2 qpidmessaging qpidtypes qpidcommon)
remember_location(qpid-txtest2)

install (TARGETS
         qpid-perftest qpid-latency-test qpid-client-test
         qpid-ping
         qpid-topic-listener qpid-topic-publisher receiver sender
         qpid-txtest qpid-txtest2
         RUNTIME DESTINATION ${QPID_INSTALL_TESTDIR})

# Only build test code if testing is turned on
if (BUILD_TESTING)

# Create the environment scripts for tests
set (abs_srcdir ${CMAKE_CURRENT_SOURCE_DIR})
set (abs_builddir ${CMAKE_CURRENT_BINARY_DIR})
set (abs_top_srcdir ${CMAKE_SOURCE_DIR})
set (abs_top_builddir ${CMAKE_BINARY_DIR})
set (builddir_lib_suffix "")

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  configure_file (${CMAKE_CURRENT_SOURCE_DIR}/test_env.ps1.in
    ${CMAKE_CURRENT_BINARY_DIR}/test_env.ps1 @ONLY)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
  configure_file (${CMAKE_CURRENT_SOURCE_DIR}/test_env.sh.in
    ${CMAKE_CURRENT_BINARY_DIR}/test_env.sh @ONLY)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

# Copy qpidd-p0 script to build directory so tests can find it.
configure_file (${CMAKE_CURRENT_SOURCE_DIR}/qpidd-p0 ${CMAKE_CURRENT_BINARY_DIR} COPYONLY)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (ENV{OUTDIR} ${EXECUTABLE_OUTPUT_PATH})
  set (test_script_suffix ".ps1")
  set (shell "powershell")
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

set(test_wrap ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_test${test_script_suffix} --build-dir=${CMAKE_BINARY_DIR})
set(python_wrap ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_test${test_script_suffix} --build-dir=${CMAKE_BINARY_DIR} --python)

if (BUILD_TESTING_UNITTESTS)

#
# Unit test program
#
# Unit tests are built as a single program to reduce valgrind overhead
# when running the tests. If you want to build a subset of the tests run
# ccmake and set unit_tests_to_build to the set you want to build.

# Like this to work with cmake 2.4 on Unix
set (qpid_test_boost_libs
     ${Boost_UNIT_TEST_FRAMEWORK_LIBRARY} ${Boost_SYSTEM_LIBRARY})

set(all_unit_tests
    AccumulatedAckTest
    Array
    AsyncCompletion
    AtomicValue
    ClientMessage
    ClientMessageTest
    ClientSessionTest
    DeliveryRecordTest
    DtxWorkRecordTest
    exception_test
    ExchangeTest
    FieldTable
    FieldValue
    FrameDecoder
    FramingTest
    HeadersExchangeTest
    HeaderTest
    InlineAllocator
    InlineVector
    logging
    ManagementTest
    MessageReplayTracker
    MessageTest
    MessagingLogger
    MessagingSessionTests
    PollableCondition
    ProxyTest
    QueueDepth
    QueueFlowLimitTest
    QueueOptionsTest
    QueuePolicyTest
    QueueRegistryTest
    QueueTest
    RangeSet
    RefCounted
    RetryList
    Selector
    SequenceNumberTest
    SequenceSet
    SessionState
    Shlib
    StringUtils
    SystemInfo
    TimerTest
    TopicExchangeTest
    TxBufferTest
    TransactionObserverTest
    Url
    Uuid
    Variant
    ${xml_tests}
   )

set(unit_tests_to_build
    ""
    CACHE STRING "Which unit tests to build"
   )

mark_as_advanced(unit_tests_to_build)

# If no unit_test specifically set then use all unit tests
if (unit_tests_to_build)
set(actual_unit_tests ${unit_tests_to_build})
else()
set(actual_unit_tests ${all_unit_tests})
endif()

add_executable (unit_test unit_test
                ${actual_unit_tests} ${platform_test_additions})
target_link_libraries (unit_test
                       ${qpid_test_boost_libs}
                       qpidmessaging qpidtypes qpidbroker qpidclient qpidcommon)
set_target_properties (unit_test PROPERTIES COMPILE_DEFINITIONS _IN_QPID_BROKER)
remember_location(unit_test)

add_test (unit_test ${test_wrap} --boost-test -- ${unit_test_LOCATION})

endif (BUILD_TESTING_UNITTESTS)

add_library (shlibtest MODULE shlibtest.cpp)

if (BUILD_SASL)
    add_custom_command(
        OUTPUT sasl_config/qpidd.conf sasl_config/qpidd.sasldb
        COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/sasl_test_setup.sh)

    add_custom_target(
        sasl_config ALL
        DEPENDS sasl_config/qpidd.conf sasl_config/qpidd.sasldb)
endif (BUILD_SASL)

#
# Other test programs
#
add_executable (echotest echotest.cpp ${platform_test_additions})
target_link_libraries (echotest qpidclient qpidcommon)
remember_location(echotest)

add_executable (publish publish.cpp ${platform_test_additions})
target_link_libraries (publish qpidclient qpidcommon)
remember_location(publish)

add_executable (consume consume.cpp ${platform_test_additions})
target_link_libraries (consume qpidclient qpidcommon)
remember_location(consume)

add_executable (header_test header_test.cpp ${platform_test_additions})
target_link_libraries (header_test qpidclient qpidcommon)
remember_location(header_test)

add_executable (declare_queues declare_queues.cpp ${platform_test_additions})
target_link_libraries (declare_queues qpidclient qpidcommon)
remember_location(declare_queues)

add_executable (replaying_sender replaying_sender.cpp ${platform_test_additions})
target_link_libraries (replaying_sender qpidclient qpidcommon)
remember_location(replaying_sender)

add_executable (resuming_receiver resuming_receiver.cpp ${platform_test_additions})
target_link_libraries (resuming_receiver qpidclient qpidcommon)
remember_location(resuming_receiver)

add_executable (txshift txshift.cpp ${platform_test_additions})
target_link_libraries (txshift qpidclient qpidcommon)
remember_location(txshift)

add_executable (txjob txjob.cpp ${platform_test_additions})
target_link_libraries (txjob qpidclient qpidcommon)
remember_location(txjob)

add_executable (datagen datagen.cpp ${platform_test_additions})
target_link_libraries (datagen qpidclient qpidcommon)
remember_location(datagen)

add_executable (msg_group_test msg_group_test.cpp ${platform_test_additions})
target_link_libraries (msg_group_test qpidmessaging qpidtypes qpidcommon)
remember_location(msg_group_test)

add_executable (ha_test_max_queues ha_test_max_queues.cpp ${platform_test_additions})
target_link_libraries (ha_test_max_queues qpidclient qpidcommon)
remember_location(ha_test_max_queues)

if (BUILD_SASL)
    add_executable (sasl_version sasl_version.cpp ${platform_test_additions})
    remember_location(sasl_version)
endif (BUILD_SASL)

set (python_src ${CMAKE_SOURCE_DIR}/../python)
if (EXISTS ${python_src})
    set (python_bld ${CMAKE_CURRENT_BINARY_DIR}/python)
    # This will not pick up added or deleted python files
    # In that case you need to rerun CMake
    file(GLOB_RECURSE python_files ${python_src}/*.py)

    add_custom_command(
	OUTPUT ${python_bld}
	DEPENDS ${python_files}
	COMMAND ${PYTHON_EXECUTABLE}
	    setup.py
	    build --build-base=${python_bld}/build
	    install --prefix=${python_bld} --install-lib=${python_bld} --install-scripts=${python_bld}/commands
	WORKING_DIRECTORY ${python_src}
    )

    add_custom_target(
	python_bld ALL
	DEPENDS ${python_bld}
    )
endif (EXISTS ${python_src})

if (BUILD_SASL)
    add_test (sasl_fed ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/sasl_fed${test_script_suffix})
    add_test (sasl_fed_ex_dynamic ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/sasl_fed_ex${test_script_suffix} dynamic)
    add_test (sasl_fed_ex_link ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/sasl_fed_ex${test_script_suffix} link)
    add_test (sasl_fed_ex_queue ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/sasl_fed_ex${test_script_suffix} queue)
    add_test (sasl_fed_ex_route ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/sasl_fed_ex${test_script_suffix} route)
    add_test (sasl_no_dir ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/sasl_no_dir${test_script_suffix})
    if (BUILD_SSL)
        add_test(ssl_test ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/ssl_test${test_script_suffix})
    endif (BUILD_SSL)
endif (BUILD_SASL)
add_test (qpid-client-test ${test_wrap} --start-broker -- ${qpid-client-test_LOCATION})
add_test (quick_perftest ${test_wrap} --start-broker -- ${qpid-perftest_LOCATION} --summary --count 100)
add_test (quick_topictest ${test_wrap} --start-broker -- ${CMAKE_CURRENT_SOURCE_DIR}/quick_topictest${test_script_suffix})
add_test (quick_txtest ${test_wrap} --start-broker -- ${qpid-txtest_LOCATION} --queues 4 --tx-count 10 --quiet)
add_test (msg_group_tests ${test_wrap} --start-broker -- ${CMAKE_CURRENT_SOURCE_DIR}/run_msg_group_tests${test_script_suffix})
add_test (run_header_test ${test_wrap} --start-broker -- ${CMAKE_CURRENT_SOURCE_DIR}/run_header_test${test_script_suffix})
add_test (python_tests ${test_wrap} --start-broker -- ${CMAKE_CURRENT_SOURCE_DIR}/python_tests${test_script_suffix})
if (NOT CMAKE_SYSTEM_NAME STREQUAL Windows)
  # paged queue not yet implemented for windows
  add_test (paged_queue_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_paged_queue_tests${test_script_suffix})
endif (NOT CMAKE_SYSTEM_NAME STREQUAL Windows)
add_test (ha_tests ${python_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/ha_tests.py)
add_test (qpidd_qmfv2_tests ${python_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/qpidd_qmfv2_tests.py)
if (BUILD_AMQP)
  add_test (interlink_tests ${python_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/interlink_tests.py)
endif (BUILD_AMQP)
add_test (swig_python_tests ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/swig_python_tests${test_script_suffix})
add_test (ipv6_test ${test_wrap} -- ${CMAKE_CURRENT_SOURCE_DIR}/ipv6_test${test_script_suffix})
add_test (federation_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_federation_tests${test_script_suffix})
add_test (federation_sys_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_federation_sys_tests${test_script_suffix})
add_test (queue_flow_limit_tests
          ${test_wrap}
          --start-broker "--broker-options=--default-flow-stop-threshold=80 --default-flow-resume-threshold=70"
          -- ${CMAKE_CURRENT_SOURCE_DIR}/run_queue_flow_limit_tests${test_script_suffix})
if (BUILD_ACL)
  add_test (acl_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_acl_tests${test_script_suffix})
endif (BUILD_ACL)
add_test (cli_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_cli_tests${test_script_suffix})
add_test (dynamic_log_level_test ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/dynamic_log_level_test${test_script_suffix})
add_test (dynamic_log_hires_timestamp ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/dynamic_log_hires_timestamp${test_script_suffix})
if (BUILD_MSSQL)
  add_test (store_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_store_tests${test_script_suffix} MSSQL)
endif (BUILD_MSSQL)
if (BUILD_MSCLFS)
  add_test (store_tests_clfs ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_store_tests${test_script_suffix} MSSQL-CLFS)
endif (BUILD_MSCLFS)
add_test (queue_redirect ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_queue_redirect${test_script_suffix})

add_library(test_store MODULE test_store.cpp)
target_link_libraries (test_store qpidbroker qpidcommon)
set_target_properties (test_store PROPERTIES PREFIX "" COMPILE_DEFINITIONS _IN_QPID_BROKER)

add_library (dlclose_noop MODULE dlclose_noop.c)

endif (BUILD_TESTING)
