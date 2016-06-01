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

include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Using the Boost DLLs triggers warning 4275 on Visual Studio (non
# dll-interface class used as base for dll-interface class).  This is
# ok, so suppress the warning.
#
# Also, boost lengthy names trigger warning 4503, decorated name
# length exceeded and using getenv() triggers insecure CRT warnings
# which we can silence in the test environment.
if (MSVC)
    add_definitions(/wd4275 /wd4503 /D_CRT_SECURE_NO_WARNINGS)
endif (MSVC)

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
add_executable(qpid-receive qpid-receive.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries(qpid-receive qpidmessaging qpidtypes qpidcommon)

add_executable(qpid-send qpid-send.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries(qpid-send qpidmessaging qpidtypes qpidcommon)

install(TARGETS qpid-receive qpid-send RUNTIME DESTINATION ${QPID_INSTALL_BINDIR})

add_executable(qpid-perftest qpid-perftest.cpp ${platform_test_additions})
target_link_libraries(qpid-perftest qpidclient qpidcommon ${Boost_PROGRAM_OPTIONS_LIBRARY})

add_executable(qpid-latency-test qpid-latency-test.cpp ${platform_test_additions})
target_link_libraries(qpid-latency-test qpidclient qpidcommon)

add_executable(qpid-client-test qpid-client-test.cpp ${platform_test_additions})
target_link_libraries(qpid-client-test qpidclient qpidcommon)

add_executable(qpid-ping qpid-ping.cpp ${platform_test_additions})
target_link_libraries(qpid-ping qpidmessaging qpidtypes qpidcommon)

add_executable(qpid-topic-listener qpid-topic-listener.cpp ${platform_test_additions})
target_link_libraries(qpid-topic-listener qpidclient qpidcommon)

add_executable(qpid-topic-publisher qpid-topic-publisher.cpp ${platform_test_additions})
target_link_libraries(qpid-topic-publisher qpidclient qpidcommon)

add_executable(receiver receiver.cpp ${platform_test_additions})
target_link_libraries(receiver qpidclient qpidcommon)

# This is bizarre - using both messaging and client libraries
add_executable(sender sender.cpp Statistics.cpp ${platform_test_additions})
target_link_libraries(sender qpidmessaging qpidtypes qpidclient qpidcommon)

add_executable(qpid-txtest qpid-txtest.cpp ${platform_test_additions})
target_link_libraries(qpid-txtest qpidclient qpidcommon qpidtypes)

add_executable(qpid-txtest2 qpid-txtest2.cpp ${platform_test_additions})
target_link_libraries(qpid-txtest2 qpidmessaging qpidtypes qpidcommon)

install(TARGETS
        qpid-perftest qpid-latency-test qpid-client-test
        qpid-ping
        qpid-topic-listener qpid-topic-publisher receiver sender
        qpid-txtest qpid-txtest2
        RUNTIME DESTINATION ${QPID_INSTALL_TESTDIR})

# Only build test code if testing is turned on
if (BUILD_TESTING)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
    # Windows
  
    set(ENV{OUTDIR} ${EXECUTABLE_OUTPUT_PATH})

    configure_file(${CMAKE_CURRENT_SOURCE_DIR}/env.ps1.in
                   ${CMAKE_CURRENT_BINARY_DIR}/env.ps1 @ONLY)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
    # Posix
  
    configure_file(${CMAKE_CURRENT_SOURCE_DIR}/env.sh.in
                   ${CMAKE_CURRENT_BINARY_DIR}/env.sh @ONLY)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

configure_file(${CMAKE_CURRENT_SOURCE_DIR}/check_dependencies.py.in
               ${CMAKE_CURRENT_BINARY_DIR}/check_dependencies.py @ONLY)
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/env.py.in
               ${CMAKE_CURRENT_BINARY_DIR}/env.py @ONLY)

file(COPY .
     DESTINATION .
     PATTERN "*.cmake" EXCLUDE
     PATTERN "*.in" EXCLUDE
     PATTERN "*.c" EXCLUDE
     PATTERN "*.h" EXCLUDE
     PATTERN "*.cpp" EXCLUDE)
       
if (BUILD_TESTING_UNITTESTS)

# Unit test program
#
# Unit tests are built as a single program to reduce valgrind overhead
# when running the tests. If you want to build a subset of the tests run
# ccmake and set unit_tests_to_build to the set you want to build.

# Like this to work with cmake 2.4 on Unix
set(qpid_test_boost_libs
    ${Boost_UNIT_TEST_FRAMEWORK_LIBRARY} ${Boost_SYSTEM_LIBRARY})

set(all_unit_tests
    AccumulatedAckTest
    Acl
    AclHost
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
    ${xml_tests})

set(unit_tests_to_build "" CACHE STRING "Which unit tests to build")
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

endif (BUILD_TESTING_UNITTESTS)

add_library(shlibtest MODULE shlibtest.cpp)

if (BUILD_SASL)
    add_custom_command(OUTPUT sasl_config/qpidd.conf sasl_config/qpidd.sasldb
                       COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/sasl_test_setup.sh)

    add_custom_target(sasl_config ALL
                      DEPENDS sasl_config/qpidd.conf sasl_config/qpidd.sasldb)
endif (BUILD_SASL)

# Other test programs

add_executable(echotest echotest.cpp ${platform_test_additions})
target_link_libraries(echotest qpidclient qpidcommon)

add_executable(publish publish.cpp ${platform_test_additions})
target_link_libraries(publish qpidclient qpidcommon)

add_executable(consume consume.cpp ${platform_test_additions})
target_link_libraries(consume qpidclient qpidcommon)

add_executable(header_test header_test.cpp ${platform_test_additions})
target_link_libraries(header_test qpidclient qpidcommon)

add_executable(declare_queues declare_queues.cpp ${platform_test_additions})
target_link_libraries(declare_queues qpidclient qpidcommon)

add_executable(replaying_sender replaying_sender.cpp ${platform_test_additions})
target_link_libraries(replaying_sender qpidclient qpidcommon)

add_executable(resuming_receiver resuming_receiver.cpp ${platform_test_additions})
target_link_libraries(resuming_receiver qpidclient qpidcommon)

add_executable(txshift txshift.cpp ${platform_test_additions})
target_link_libraries(txshift qpidclient qpidcommon)

add_executable(txjob txjob.cpp ${platform_test_additions})
target_link_libraries(txjob qpidclient qpidcommon)

add_executable(datagen datagen.cpp ${platform_test_additions})
target_link_libraries(datagen qpidclient qpidcommon)

add_executable(msg_group_test msg_group_test.cpp ${platform_test_additions})
target_link_libraries(msg_group_test qpidmessaging qpidtypes qpidcommon)

add_executable(ha_test_max_queues ha_test_max_queues.cpp ${platform_test_additions})
target_link_libraries(ha_test_max_queues qpidclient qpidcommon)

add_library(test_store MODULE test_store.cpp)
target_link_libraries(test_store qpidbroker qpidcommon)
set_target_properties(test_store PROPERTIES PREFIX "" COMPILE_DEFINITIONS _IN_QPID_BROKER)

add_library(dlclose_noop MODULE dlclose_noop.c)

if (BUILD_SASL)
    add_executable(sasl_version sasl_version.cpp ${platform_test_additions})
endif (BUILD_SASL)

# Cross-platform tests

add_test(NAME unit_tests COMMAND ${PYTHON_EXECUTABLE} run_unit_tests)

add_test(NAME acl_tests COMMAND ${PYTHON_EXECUTABLE} run_acl_tests)
add_test(NAME cli_tests COMMAND ${PYTHON_EXECUTABLE} run_cli_tests)
add_test(NAME client_tests COMMAND ${PYTHON_EXECUTABLE} run_client_tests)
add_test(NAME federation_tests COMMAND ${PYTHON_EXECUTABLE} run_federation_tests)
add_test(NAME flow_control_tests COMMAND ${PYTHON_EXECUTABLE} run_flow_control_tests)
add_test(NAME msg_group_tests COMMAND ${PYTHON_EXECUTABLE} run_msg_group_tests)
add_test(NAME performance_tests COMMAND ${PYTHON_EXECUTABLE} run_performance_tests)
add_test(NAME python_tests COMMAND ${PYTHON_EXECUTABLE} run_python_tests)
add_test(NAME queue_redirect_tests COMMAND ${PYTHON_EXECUTABLE} run_queue_redirect_tests)
add_test(NAME qmf_tests COMMAND ${PYTHON_EXECUTABLE} run_qmf_tests)
add_test(NAME transaction_tests COMMAND ${PYTHON_EXECUTABLE} run_transaction_tests)

if (BUILD_AMQP)
    add_test(NAME idle_timeout_tests COMMAND ${PYTHON_EXECUTABLE} run_idle_timeout_tests)
    add_test(NAME interlink_tests COMMAND ${PYTHON_EXECUTABLE} run_interlink_tests)
    add_test(NAME interop_tests COMMAND ${PYTHON_EXECUTABLE} run_interop_tests)
endif (BUILD_AMQP)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
    # Windows-only tests

    add_test(NAME windows_store_tests COMMAND ${PYTHON_EXECUTABLE} run_windows_store_tests)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
    # Posix-only tests

    add_test(NAME ha_tests COMMAND ${PYTHON_EXECUTABLE} run_ha_tests)
    
    add_test(NAME ipv6_tests COMMAND run_ipv6_tests) # Also pretty simple to convert
    add_test(NAME logging_tests COMMAND run_logging_tests) # Pretty simple to convert
    add_test(NAME paged_queue_tests COMMAND run_paged_queue_tests)
    add_test(NAME ring_queue_tests COMMAND run_ring_queue_tests)
    add_test(NAME topic_tests COMMAND run_topic_tests)

    if (BUILD_SASL)
        add_test(NAME sasl_tests COMMAND run_sasl_tests)

        if (BUILD_SSL)
            add_test(NAME ssl_tests COMMAND run_ssl_tests)
        endif (BUILD_SSL)
    endif (BUILD_SASL)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

endif (BUILD_TESTING)
