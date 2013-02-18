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

# Create the environment scripts for tests
set (abs_srcdir ${CMAKE_CURRENT_SOURCE_DIR})
set (abs_builddir ${CMAKE_CURRENT_BINARY_DIR})
set (abs_top_srcdir ${CMAKE_SOURCE_DIR})
set (abs_top_builddir ${CMAKE_BINARY_DIR})
set (builddir_lib_suffix "")

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  configure_file (${CMAKE_CURRENT_SOURCE_DIR}/test_env.ps1.in
    ${CMAKE_CURRENT_BINARY_DIR}/test_env.ps1)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
  configure_file (${CMAKE_CURRENT_SOURCE_DIR}/test_env.sh.in
    ${CMAKE_CURRENT_BINARY_DIR}/test_env.sh)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)


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

# Using the Boost DLLs triggers warning 4275 on Visual Studio
# (non dll-interface class used as base for dll-interface class).
# This is ok, so suppress the warning.
# Also, boost lengthy names trigger warning 4503, decorated name length exceeded
# and using getenv() triggers insecure CRT warnings which we can silence in the
# test environment.
if (MSVC)
  add_definitions( /wd4275 /wd4503 /D_CRT_SECURE_NO_WARNINGS)
endif (MSVC)

# Like this to work with cmake 2.4 on Unix
set (qpid_test_boost_libs 
     ${Boost_UNIT_TEST_FRAMEWORK_LIBRARY} ${Boost_SYSTEM_LIBRARY})

# Macro to make it easier to remember where the tests are built
macro(remember_location testname)
  set (${testname}_LOCATION ${CMAKE_CURRENT_BINARY_DIR}/${testname}${CMAKE_EXECUTABLE_SUFFIX})
endmacro(remember_location)

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

#
# Unit test program
#
# Unit tests are built as a single program to reduce valgrind overhead
# when running the tests. If you want to build a subset of the tests run
# ccmake and set unit_tests_to_build to the set you want to build.

set(all_unit_tests
    AccumulatedAckTest
    Array
    AsyncCompletion
    AtomicValue
    ClientMessage
    ClientMessageTest
    ClientSessionTest
    ConsoleTest
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
    SequenceNumberTest
    SequenceSet
    SessionState
    Shlib
    StringUtils
    SystemInfo
    TimerTest
    TopicExchangeTest
    TxBufferTest
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
                       qpidmessaging qpidbroker qmfconsole)
set_target_properties (unit_test PROPERTIES COMPILE_DEFINITIONS _IN_QPID_BROKER)
remember_location(unit_test)

add_library (shlibtest MODULE shlibtest.cpp)

# FIXME aconway 2009-11-30: enable SSL
#if SSL
#include ssl.mk
#endif

#
# Other test programs
#
add_executable (qpid-perftest qpid-perftest.cpp ${platform_test_additions})
target_link_libraries (qpid-perftest qpidclient)
#qpid_perftest_SOURCES=qpid-perftest.cpp test_tools.h TestOptions.h ConnectionOptions.h
remember_location(qpid-perftest)

add_executable (qpid-txtest qpid-txtest.cpp ${platform_test_additions})
target_link_libraries (qpid-txtest qpidclient)
#qpid_txtest_SOURCES=qpid-txtest.cpp  TestOptions.h ConnectionOptions.h
remember_location(qpid-txtest)

add_executable (qpid-latency-test qpid-latency-test.cpp ${platform_test_additions})
target_link_libraries (qpid-latency-test qpidclient)
#qpid_latencytest_SOURCES=qpid-latency-test.cpp TestOptions.h ConnectionOptions.h
remember_location(qpid-latency-test)

add_executable (echotest echotest.cpp ${platform_test_additions})
target_link_libraries (echotest qpidclient)
#echotest_SOURCES=echotest.cpp TestOptions.h ConnectionOptions.h
remember_location(echotest)

add_executable (qpid-client-test qpid-client-test.cpp ${platform_test_additions})
target_link_libraries (qpid-client-test qpidclient)
#qpid_client_test_SOURCES=qpid-client-test.cpp TestOptions.h ConnectionOptions.h
remember_location(qpid-client-test)

add_executable (qpid-topic-listener qpid-topic-listener.cpp ${platform_test_additions})
target_link_libraries (qpid-topic-listener qpidclient)
#qpid_topic_listener_SOURCES=qpid-topic-listener.cpp TestOptions.h ConnectionOptions.h
remember_location(qpid-topic-listener)

add_executable (qpid-topic-publisher qpid-topic-publisher.cpp ${platform_test_additions})
target_link_libraries (qpid-topic-publisher qpidclient)
#qpid_topic_publisher_SOURCES=qpid-topic-publisher.cpp TestOptions.h ConnectionOptions.h
remember_location(qpid-topic-publisher)

add_executable (publish publish.cpp ${platform_test_additions})
target_link_libraries (publish qpidclient)
#publish_SOURCES=publish.cpp TestOptions.h ConnectionOptions.h
remember_location(publish)

add_executable (consume consume.cpp ${platform_test_additions})
target_link_libraries (consume qpidclient)
#consume_SOURCES=consume.cpp  TestOptions.h ConnectionOptions.h
remember_location(consume)

add_executable (header_test header_test.cpp ${platform_test_additions})
target_link_libraries (header_test qpidclient)
#header_test_SOURCES=header_test.cpp TestOptions.h ConnectionOptions.h
remember_location(header_test)

add_executable (declare_queues declare_queues.cpp ${platform_test_additions})
target_link_libraries (declare_queues qpidclient)
remember_location(declare_queues)

add_executable (replaying_sender replaying_sender.cpp ${platform_test_additions})
target_link_libraries (replaying_sender qpidclient)
remember_location(replaying_sender)

add_executable (resuming_receiver resuming_receiver.cpp ${platform_test_additions})
target_link_libraries (resuming_receiver qpidclient)
remember_location(resuming_receiver)

add_executable (txshift txshift.cpp ${platform_test_additions})
target_link_libraries (txshift qpidclient)
#txshift_SOURCES=txshift.cpp  TestOptions.h ConnectionOptions.h
remember_location(txshift)

add_executable (txjob txjob.cpp ${platform_test_additions})
target_link_libraries (txjob qpidclient)
#txjob_SOURCES=txjob.cpp  TestOptions.h ConnectionOptions.h
remember_location(txjob)

add_executable (receiver receiver.cpp ${platform_test_additions})
target_link_libraries (receiver qpidclient)
#receiver_SOURCES=receiver.cpp  TestOptions.h ConnectionOptions.h
remember_location(receiver)

add_executable (sender sender.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries (sender qpidmessaging)
#sender_SOURCES=sender.cpp  TestOptions.h ConnectionOptions.h
remember_location(sender)

add_executable (qpid-receive qpid-receive.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries (qpid-receive qpidmessaging)
remember_location(qpid-receive)

add_executable (qpid-send qpid-send.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries (qpid-send qpidmessaging)
remember_location(qpid-send)

add_executable (qpid-ping qpid-ping.cpp ${platform_test_additions})
target_link_libraries (qpid-ping qpidclient)
remember_location(qpid-ping)

add_executable (datagen datagen.cpp ${platform_test_additions})
target_link_libraries (datagen qpidclient)
remember_location(datagen)

add_executable (msg_group_test msg_group_test.cpp ${platform_test_additions})
target_link_libraries (msg_group_test qpidmessaging)
remember_location(msg_group_test)


# qpid-perftest and qpid-latency-test are generally useful so install them
install (TARGETS
         qpid-perftest qpid-latency-test qpid-client-test
         qpid-ping
         qpid-receive qpid-send qpid-topic-listener qpid-topic-publisher receiver sender
         qpid-txtest
         RUNTIME DESTINATION ${QPID_INSTALL_TESTDIR})

# This should ideally be done as part of the test run, but I don't know a way
# to get these arguments and the working directory set like Makefile.am does,
# and have that run during the test pass.
if (PYTHON_EXECUTABLE)
  set (python_bld ${CMAKE_CURRENT_BINARY_DIR}/python)
  execute_process(COMMAND ${PYTHON_EXECUTABLE} setup.py install --prefix=${pythoon_bld} --install-lib=${python_bld} --install-scripts=${python_bld}/commands
                  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}/../python)
endif (PYTHON_EXECUTABLE)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (ENV{OUTDIR} ${EXECUTABLE_OUTPUT_PATH})
  set (test_script_suffix ".ps1")
  set (shell "powershell")
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

set(test_wrap ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_test${test_script_suffix})

add_test (unit_test ${test_wrap} ${unit_test_LOCATION})
add_test (start_broker ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/start_broker${test_script_suffix})
add_test (qpid-client-test ${test_wrap} ${qpid-client-test_LOCATION})
add_test (quick_perftest ${test_wrap} ${qpid-perftest_LOCATION} --summary --count 100)
add_test (quick_topictest ${test_wrap} ${CMAKE_CURRENT_SOURCE_DIR}/quick_topictest${test_script_suffix})
add_test (quick_txtest ${test_wrap} ${qpid-txtest_LOCATION} --queues 4 --tx-count 10 --quiet)
if (PYTHON_EXECUTABLE)
  add_test (run_header_test ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_header_test${test_script_suffix})
  add_test (python_tests ${test_wrap} ${CMAKE_CURRENT_SOURCE_DIR}/python_tests${test_script_suffix})
endif (PYTHON_EXECUTABLE)
add_test (stop_broker ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/stop_broker${test_script_suffix})
if (PYTHON_EXECUTABLE)
  add_test (ha_tests ${test_wrap} ${PYTHON_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/ha_tests.py)
  add_test (ipv6_test ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/ipv6_test${test_script_suffix})
  add_test (federation_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_federation_tests${test_script_suffix})
  add_test (federation_sys_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_federation_sys_tests${test_script_suffix})
if (BUILD_ACL)
  add_test (acl_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_acl_tests${test_script_suffix})
endif (BUILD_ACL)
add_test (dynamic_log_level_test ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/dynamic_log_level_test${test_script_suffix})
add_test (dynamic_log_hires_timestamp ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/dynamic_log_hires_timestamp${test_script_suffix})
if (BUILD_MSSQL)
  add_test (store_tests ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_store_tests${test_script_suffix} MSSQL)
endif (BUILD_MSSQL)
if (BUILD_MSCLFS)
  add_test (store_tests_clfs ${shell} ${CMAKE_CURRENT_SOURCE_DIR}/run_store_tests${test_script_suffix} MSSQL-CLFS)
endif (BUILD_MSCLFS)
endif (PYTHON_EXECUTABLE)

add_library(test_store MODULE test_store.cpp)
target_link_libraries (test_store qpidbroker qpidcommon)
set_target_properties (test_store PROPERTIES
                       COMPILE_DEFINITIONS _IN_QPID_BROKER
                       PREFIX "")


add_library (dlclose_noop MODULE dlclose_noop.c)
#libdlclose_noop_la_LDFLAGS = -module -rpath $(abs_builddir)

#CLEANFILES+=valgrind.out *.log *.vglog* dummy_test $(unit_wrappers)
#
## Longer running stability tests, not run by default check: target.
## Not run under valgrind, too slow
#LONG_TESTS=fanout_perftest shared_perftest multiq_perftest topic_perftest
#EXTRA_DIST+=$(LONG_TESTS) run_perftest
#check-long:
#	$(MAKE) check TESTS="start_broker $(LONG_TESTS) stop_broker" VALGRIND=

#
# legacystore
#
add_subdirectory(legacystore)
