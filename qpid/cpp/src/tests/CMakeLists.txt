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

add_definitions(-DBOOST_TEST_DYN_LINK)
include_directories( ${CMAKE_CURRENT_SOURCE_DIR} )

#lib_client = $(abs_builddir)/../libqpidclient.la
#lib_common = $(abs_builddir)/../libqpidcommon.la
#lib_broker = $(abs_builddir)/../libqpidbroker.la
#lib_console = $(abs_builddir)/../libqmfconsole.la
## lib_amqp_0_10 = $(abs_builddir)/../libqpidamqp_0_10.la
#
##
## Initialize variables that are incremented with +=
## 
#check_PROGRAMS=
#check_LTLIBRARIES=
#TESTS=
#EXTRA_DIST=
#CLEANFILES=
#
#
# Unit test program
#
# Unit tests are built as a single program to reduce valgrind overhead
# when running the tests. If you want to build a subset of the tests do 
#   rm -f unit_test; make unit_test unit_test_OBJECTS="unit_test.o SelectedTest.o"
# 
set (qpid_test_boost_libs boost_unit_test_framework boost_regex)
#TESTS+=unit_test
#check_PROGRAMS+=unit_test
#unit_test_LDADD=-lboost_unit_test_framework -lboost_regex  \
#	$(lib_client) $(lib_broker) $(lib_console)
#
set(unit_tests_to_build
    unit_test
    exception_test
    RefCounted
    SessionState
    logging
    AsyncCompletion
    Url
    Uuid
    Shlib
    FieldValue
    FieldTable
    Array
    QueueOptionsTest
    InlineAllocator
    InlineVector
    ClientSessionTest
    SequenceSet
    StringUtils
    IncompleteMessageList
    RangeSet
    AtomicValue
    QueueTest
    AccumulatedAckTest
    DtxWorkRecordTest
    DeliveryRecordTest
    ExchangeTest
    HeadersExchangeTest
    MessageTest
    QueueRegistryTest
    QueuePolicyTest
    FramingTest
    HeaderTest
    SequenceNumberTest
    TimerTest
    TopicExchangeTest
    TxBufferTest
    TxPublishTest
    MessageBuilderTest
    ManagementTest
    MessageReplayTracker
    ConsoleTest
    QueueEvents
    ProxyTest
    RetryList
    RateFlowcontrolTest
    FrameDecoder
    ReplicationTest
    ClientMessageTest
    PollableCondition
    CACHE STRING "Which unit tests to build"
   )
if (QPID_HAS_XML)
  set(unit_tests_to_build ${unit_tests_to_build} XmlClientSessionTest)
endif (QPID_HAS_XML)
mark_as_advanced(unit_tests_to_build)

#message(DEBUG "unit_tests_to_build: ${unit_tests_to_build}")
#string (REPLACE ";" ".cpp;"
#        unit_test_SOURCES
#        ${unit_tests_to_build})

# Disabled till we move to amqp_0_10 codec.
# 	amqp_0_10/serialize.cpp allSegmentTypes.h \
# 	amqp_0_10/ProxyTemplate.cpp \
# 	amqp_0_10/apply.cpp \
# 	amqp_0_10/Map.cpp \
# 	amqp_0_10/handlers.cpp 

add_executable (unit_test ${unit_tests_to_build})
# The generally recommended way to add macro settings is to use
# COMPILE_DEFINITIONS, but it's a rough go to add more than one definition
# with a value; in this case, assuming that -D works everywhere is easier.
set_source_files_properties (ReplicationTest.cpp Shlib.cpp
                             PROPERTIES
                             COMPILE_FLAGS
                             "-DQPID_MODULE_SUFFIX=\\\"${CMAKE_SHARED_MODULE_SUFFIX}\\\" -DQPID_MODULE_PREFIX=\\\"${CMAKE_SHARED_MODULE_PREFIX}\\\"")
target_link_libraries (unit_test
                       ${qpid_test_boost_libs}
                       qpidclient qpidbroker qmfconsole)
add_test (unit_test unit_test)
add_dependencies (check unit_test)

add_library (shlibtest MODULE shlibtest.cpp)
#libshlibtest_la_LDFLAGS = -module -rpath $(abs_builddir)

#include cluster.mk
#if SSL
#include ssl.mk
#endif

#
# Other test programs
#
add_executable (perftest perftest.cpp)
target_link_libraries (perftest qpidclient)
#perftest_SOURCES=perftest.cpp test_tools.h TestOptions.h ConnectionOptions.h
add_test (perftest perftest)
add_dependencies (check perftest)

add_executable (txtest txtest.cpp)
target_link_libraries (txtest qpidclient)
#txtest_SOURCES=txtest.cpp  TestOptions.h ConnectionOptions.h
add_test (txtest txtest)
add_dependencies (check txtest)

add_executable (latencytest latencytest.cpp)
target_link_libraries (latencytest qpidclient)
#latencytest_SOURCES=latencytest.cpp TestOptions.h ConnectionOptions.h
add_test (latencytest latencytest)
add_dependencies (check latencytest)

add_executable (echotest echotest.cpp)
target_link_libraries (echotest qpidclient)
#echotest_SOURCES=echotest.cpp TestOptions.h ConnectionOptions.h
add_test (echotest echotest)
add_dependencies (check echotest)

add_executable (client_test client_test.cpp)
target_link_libraries (client_test qpidclient)
#client_test_SOURCES=client_test.cpp TestOptions.h ConnectionOptions.h
add_test (client_test client_test)
add_dependencies (check client_test)

add_executable (topic_listener topic_listener.cpp)
target_link_libraries (topic_listener qpidclient)
#topic_listener_SOURCES=topic_listener.cpp TestOptions.h ConnectionOptions.h
add_test (topic_listener topic_listener)
add_dependencies (check topic_listener)

add_executable (topic_publisher topic_publisher.cpp)
target_link_libraries (topic_publisher qpidclient)
#topic_publisher_SOURCES=topic_publisher.cpp TestOptions.h ConnectionOptions.h
add_test (topic_publisher topic_publisher)
add_dependencies (check topic_publisher)

add_executable (publish publish.cpp)
target_link_libraries (publish qpidclient)
#publish_SOURCES=publish.cpp TestOptions.h ConnectionOptions.h
add_test (publish publish)
add_dependencies (check publish)

add_executable (consume consume.cpp)
target_link_libraries (consume qpidclient)
#consume_SOURCES=consume.cpp  TestOptions.h ConnectionOptions.h
add_test (consume consume)
add_dependencies (check consume)

add_executable (header_test header_test.cpp)
target_link_libraries (header_test qpidclient)
#header_test_SOURCES=header_test.cpp TestOptions.h ConnectionOptions.h
add_test (header_test header_test)
add_dependencies (check header_test)

add_executable (failover_soak failover_soak.cpp ForkedBroker.cpp)
target_link_libraries (failover_soak qpidclient)
#failover_soak_SOURCES=failover_soak.cpp  ForkedBroker.h
add_test (failover_soak failover_soak)
add_dependencies (check failover_soak)

add_executable (declare_queues declare_queues.cpp)
target_link_libraries (declare_queues qpidclient)
add_test (declare_queues declare_queues)
add_dependencies (check declare_queues)

add_executable (replaying_sender replaying_sender.cpp)
target_link_libraries (replaying_sender qpidclient)
add_test (replaying_sender replaying_sender)
add_dependencies (check replaying_sender)

add_executable (resuming_receiver resuming_receiver.cpp)
target_link_libraries (resuming_receiver qpidclient)
add_test (resuming_receiver resuming_receiver)
add_dependencies (check resuming_receiver)

add_executable (txshift txshift.cpp)
target_link_libraries (txshift qpidclient)
#txshift_SOURCES=txshift.cpp  TestOptions.h ConnectionOptions.h
add_test (txshift txshift)
add_dependencies (check txshift)

add_executable (txjob txjob.cpp)
target_link_libraries (txjob qpidclient)
#txjob_SOURCES=txjob.cpp  TestOptions.h ConnectionOptions.h
add_test (txjob txjob)
add_dependencies (check txjob)

add_executable (receiver receiver.cpp)
target_link_libraries (receiver qpidclient)
#receiver_SOURCES=receiver.cpp  TestOptions.h ConnectionOptions.h
add_test (receiver receiver)
add_dependencies (check receiver)

add_executable (sender sender.cpp)
target_link_libraries (sender qpidclient)
#sender_SOURCES=sender.cpp  TestOptions.h ConnectionOptions.h
add_test (sender sender)
add_dependencies (check sender)


#TESTS_ENVIRONMENT = VALGRIND=$(VALGRIND) srcdir=$(srcdir) QPID_DATA_DIR= BOOST_TEST_SHOW_PROGRESS=yes $(srcdir)/run_test 
#
#system_tests = client_test quick_perftest quick_topictest run_header_test quick_txtest
#TESTS += start_broker $(system_tests) python_tests stop_broker run_federation_tests run_acl_tests 
#
#EXTRA_DIST +=								\
#  run_test vg_check							\
#  run-unit-tests start_broker python_tests stop_broker 			\
#  quick_topictest							\
#  quick_perftest							\
#  quick_txtest								\
#  topictest								\
#  run_header_test							\
#  header_test.py							\
#  ssl_test								\
#  config.null								\
#  ais_check								\
#  run_federation_tests							\
#  run_acl_tests								\
#  .valgrind.supp							\
#  MessageUtils.h							\
#  TestMessageStore.h							\
#  TxMocks.h								\
#  start_cluster stop_cluster restart_cluster

add_library (dlclose_noop MODULE dlclose_noop.c)
#libdlclose_noop_la_LDFLAGS = -module -rpath $(abs_builddir)

#CLEANFILES+=valgrind.out *.log *.vglog* dummy_test $(unit_wrappers)
#
## FIXME aconway 2008-05-23: Disabled interop_runner because it uses
## the obsolete Channel class.  Convert to Session and re-enable.
## 
## check_PROGRAMS += interop_runner
#
## interop_runner_SOURCES = 	\
##   interop_runner.cpp	 	\
##   SimpleTestCaseBase.cpp	\
##   BasicP2PTest.cpp		\
##   BasicPubSubTest.cpp		\
##   SimpleTestCaseBase.h		\
##   BasicP2PTest.h		\
##   BasicPubSubTest.h		\
##   TestCase.h			\
##   TestOptions.h ConnectionOptions.h
## interop_runner_LDADD = $(lib_client) $(lib_common) $(extra_libs)
#
#
## Longer running stability tests, not run by default check: target.
## Not run under valgrind, too slow
#LONG_TESTS=fanout_perftest shared_perftest multiq_perftest topic_perftest run_failover_soak
#EXTRA_DIST+=$(LONG_TESTS) run_perftest
#check-long:
#	$(MAKE) check TESTS="start_broker $(LONG_TESTS) stop_broker" VALGRIND=
