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

AM_CXXFLAGS = $(WARNING_CFLAGS) -DBOOST_TEST_DYN_LINK -D_IN_QPID_BROKER
INCLUDES = -I$(top_srcdir)/include -I$(top_builddir)/include -I$(top_srcdir)/src -I$(top_builddir)/src
PUBLIC_INCLUDES = -I$(top_srcdir)/include -I$(top_builddir)/include # Use public API only
QMF_GEN=$(top_srcdir)/managementgen/qmf-gen

abs_builddir=@abs_builddir@
abs_srcdir=@abs_srcdir@

extra_libs = 
lib_client = $(abs_builddir)/../libqpidclient.la
lib_messaging = $(abs_builddir)/../libqpidmessaging.la
lib_types = $(abs_builddir)/../libqpidtypes.la
lib_common = $(abs_builddir)/../libqpidcommon.la
lib_broker = $(abs_builddir)/../libqpidbroker.la
lib_console = $(abs_builddir)/../libqmfconsole.la
lib_qmf2 = $(abs_builddir)/../libqmf2.la
# lib_amqp_0_10 = $(abs_builddir)/../libqpidamqp_0_10.la

#
# Initialize variables that are incremented with +=
# 
check_PROGRAMS=
check_LTLIBRARIES=
TESTS=
EXTRA_DIST=
CLEANFILES=
LONG_TESTS=
CLEAN_LOCAL=

#
# Destination for intalled programs and tests defined here
#
qpidexecdir = $(libexecdir)/qpid
qpidexec_PROGRAMS =
qpidexec_SCRIPTS =
qpidexectestdir = $(qpidexecdir)/tests
qpidexectest_PROGRAMS =
qpidexectest_SCRIPTS =
tmoduledir = $(libdir)/qpid/tests
tmodule_LTLIBRARIES=

#
# Unit test program
#
# Unit tests are built as a single program to reduce valgrind overhead
# when running the tests. If you want to build a subset of the tests do 
#   rm -f unit_test; make unit_test unit_test_OBJECTS="unit_test.o SelectedTest.o"
# 

TESTS+=unit_test
check_PROGRAMS+=unit_test
unit_test_LDADD=-lboost_unit_test_framework -lpthread \
	$(lib_messaging) $(lib_broker) $(lib_console) $(lib_qmf2)

unit_test_SOURCES= unit_test.cpp unit_test.h \
	MessagingSessionTests.cpp \
	MessagingThreadTests.cpp \
	MessagingFixture.h \
	ClientSessionTest.cpp \
	BrokerFixture.h \
	exception_test.cpp \
	RefCounted.cpp \
	SessionState.cpp logging.cpp \
	AsyncCompletion.cpp \
	Url.cpp Uuid.cpp \
	Shlib.cpp FieldValue.cpp FieldTable.cpp Array.cpp \
	QueueOptionsTest.cpp \
	InlineAllocator.cpp \
	InlineVector.cpp \
	SequenceSet.cpp \
	StringUtils.cpp \
	RangeSet.cpp \
	AtomicValue.cpp \
	QueueTest.cpp \
	AccumulatedAckTest.cpp \
	DtxWorkRecordTest.cpp \
	DeliveryRecordTest.cpp \
	ExchangeTest.cpp \
	HeadersExchangeTest.cpp \
	MessageTest.cpp \
	QueueDepth.cpp \
	QueueRegistryTest.cpp \
	QueuePolicyTest.cpp \
	QueueFlowLimitTest.cpp \
	FramingTest.cpp \
	HeaderTest.cpp \
	SequenceNumberTest.cpp \
	TimerTest.cpp \
	TopicExchangeTest.cpp \
	TxBufferTest.cpp \
	ConnectionOptions.h \
	ManagementTest.cpp \
	MessageReplayTracker.cpp \
	ConsoleTest.cpp \
	ProxyTest.cpp \
	RetryList.cpp \
	FrameDecoder.cpp \
	ClientMessageTest.cpp \
	PollableCondition.cpp \
	Variant.cpp \
	Address.cpp \
	ClientMessage.cpp \
	Qmf2.cpp \
	BrokerOptions.cpp \
	Selector.cpp \
	SystemInfo.cpp

if HAVE_XML
unit_test_SOURCES+= XmlClientSessionTest.cpp
endif

TESTLIBFLAGS = -module -rpath $(abs_builddir)

check_LTLIBRARIES += libshlibtest.la
libshlibtest_la_LDFLAGS = $(TESTLIBFLAGS)
libshlibtest_la_SOURCES = shlibtest.cpp

tmodule_LTLIBRARIES += test_store.la
test_store_la_SOURCES = test_store.cpp
test_store_la_LIBADD = $(lib_broker)
test_store_la_LDFLAGS = -module

include sasl.mk
if SSL
include ssl.mk
endif

# Test programs that are installed and therefore built as part of make, not make check

qpidexectest_SCRIPTS += qpid-cpp-benchmark qpid-cluster-benchmark install_env.sh
EXTRA_DIST += qpid-cpp-benchmark qpid-cluster-benchmark install_env.sh

qpidexectest_PROGRAMS += receiver
receiver_SOURCES = \
  receiver.cpp \
  TestOptions.h \
  ConnectionOptions.h
receiver_LDADD = $(lib_client) -lboost_program_options $(lib_common)

qpidexectest_PROGRAMS += sender
sender_SOURCES = \
  sender.cpp \
  TestOptions.h \
  ConnectionOptions.h \
  Statistics.cpp
sender_LDADD = $(lib_messaging) -lboost_program_options $(lib_common) $(lib_types) $(lib_client)

qpidexectest_PROGRAMS += qpid-receive
qpid_receive_SOURCES = \
  qpid-receive.cpp \
  TestOptions.h \
  ConnectionOptions.h \
  Statistics.h \
  Statistics.cpp
qpid_receive_LDADD = $(lib_messaging) -lboost_program_options $(lib_common) $(lib_types)

qpidexectest_PROGRAMS += qpid-send
qpid_send_SOURCES = \
  qpid-send.cpp \
  TestOptions.h \
  ConnectionOptions.h \
  Statistics.h \
  Statistics.cpp
qpid_send_LDADD = $(lib_messaging) -lboost_program_options $(lib_common) $(lib_types)

qpidexectest_PROGRAMS+=qpid-perftest
qpid_perftest_SOURCES=qpid-perftest.cpp test_tools.h TestOptions.h ConnectionOptions.h
qpid_perftest_INCLUDES=$(PUBLIC_INCLUDES)
qpid_perftest_LDADD=$(lib_client)  -lboost_program_options $(lib_common)

qpidexectest_PROGRAMS+=qpid-txtest
qpid_txtest_INCLUDES=$(PUBLIC_INCLUDES)
qpid_txtest_SOURCES=qpid-txtest.cpp  TestOptions.h ConnectionOptions.h
qpid_txtest_LDADD=$(lib_client)  -lboost_program_options $(lib_common)

qpidexectest_PROGRAMS+=qpid-latency-test
qpid_latency_test_INCLUDES=$(PUBLIC_INCLUDES)
qpid_latency_test_SOURCES=qpid-latency-test.cpp TestOptions.h ConnectionOptions.h
qpid_latency_test_LDADD=$(lib_client)  -lboost_program_options $(lib_common)

qpidexectest_PROGRAMS+=qpid-client-test
qpid_client_test_INCLUDES=$(PUBLIC_INCLUDES)
qpid_client_test_SOURCES=qpid-client-test.cpp TestOptions.h ConnectionOptions.h
qpid_client_test_LDADD=$(lib_client)  -lboost_program_options $(lib_common)

qpidexectest_PROGRAMS+=qpid-topic-listener
qpid_topic_listener_INCLUDES=$(PUBLIC_INCLUDES)
qpid_topic_listener_SOURCES=qpid-topic-listener.cpp TestOptions.h ConnectionOptions.h
qpid_topic_listener_LDADD=$(lib_client)  -lboost_program_options $(lib_common)

qpidexectest_PROGRAMS+=qpid-topic-publisher
qpid_topic_publisher_INCLUDES=$(PUBLIC_INCLUDES)
qpid_topic_publisher_SOURCES=qpid-topic-publisher.cpp TestOptions.h ConnectionOptions.h
qpid_topic_publisher_LDADD=$(lib_client)  -lboost_program_options $(lib_common)

qpidexectest_PROGRAMS+=qpid-ping
qpid_ping_INCLUDES=$(PUBLIC_INCLUDES)
qpid_ping_SOURCES=qpid-ping.cpp test_tools.h TestOptions.h ConnectionOptions.h
qpid_ping_LDADD=$(lib_client) -lboost_program_options $(lib_common)

#
# Other test programs
#

check_PROGRAMS+=echotest
echotest_INCLUDES=$(PUBLIC_INCLUDES)
echotest_SOURCES=echotest.cpp TestOptions.h ConnectionOptions.h
echotest_LDADD=$(lib_client)

check_PROGRAMS+=publish
publish_INCLUDES=$(PUBLIC_INCLUDES)
publish_SOURCES=publish.cpp TestOptions.h ConnectionOptions.h
publish_LDADD=$(lib_client) 

check_PROGRAMS+=consume
consume_INCLUDES=$(PUBLIC_INCLUDES)
consume_SOURCES=consume.cpp  TestOptions.h ConnectionOptions.h
consume_LDADD=$(lib_client) 

check_PROGRAMS+=header_test
header_test_INCLUDES=$(PUBLIC_INCLUDES)
header_test_SOURCES=header_test.cpp TestOptions.h ConnectionOptions.h
header_test_LDADD=$(lib_client) 

check_PROGRAMS+=declare_queues
declare_queues_INCLUDES=$(PUBLIC_INCLUDES)
declare_queues_SOURCES=declare_queues.cpp  
declare_queues_LDADD=$(lib_client) 

check_PROGRAMS+=replaying_sender
replaying_sender_INCLUDES=$(PUBLIC_INCLUDES)
replaying_sender_SOURCES=replaying_sender.cpp  
replaying_sender_LDADD=$(lib_client) 

check_PROGRAMS+=resuming_receiver
resuming_receiver_INCLUDES=$(PUBLIC_INCLUDES)
resuming_receiver_SOURCES=resuming_receiver.cpp  
resuming_receiver_LDADD=$(lib_client) 

check_PROGRAMS+=txshift
txshift_INCLUDES=$(PUBLIC_INCLUDES)
txshift_SOURCES=txshift.cpp  TestOptions.h ConnectionOptions.h
txshift_LDADD=$(lib_client) 

check_PROGRAMS+=txjob
txjob_INCLUDES=$(PUBLIC_INCLUDES)
txjob_SOURCES=txjob.cpp  TestOptions.h ConnectionOptions.h
txjob_LDADD=$(lib_client) 

check_PROGRAMS+=PollerTest
PollerTest_SOURCES=PollerTest.cpp
PollerTest_LDADD=$(lib_common) $(lib_client) $(SOCKLIBS)

check_PROGRAMS+=DispatcherTest
DispatcherTest_SOURCES=DispatcherTest.cpp
DispatcherTest_LDADD=$(lib_common) $(lib_client) $(SOCKLIBS)

check_PROGRAMS+=datagen
datagen_SOURCES=datagen.cpp
datagen_LDADD=$(lib_common) $(lib_client)

check_PROGRAMS+=qpid-stream
qpid_stream_INCLUDES=$(PUBLIC_INCLUDES)
qpid_stream_SOURCES=qpid-stream.cpp
qpid_stream_LDADD=$(lib_messaging)

check_PROGRAMS+=msg_group_test
msg_group_test_INCLUDES=$(PUBLIC_INCLUDES)
msg_group_test_SOURCES=msg_group_test.cpp
msg_group_test_LDADD=$(lib_messaging)

TESTS_ENVIRONMENT = \
    VALGRIND=$(VALGRIND) \
    LIBTOOL="$(LIBTOOL)" \
    QPID_DATA_DIR= \
    $(srcdir)/run_test 

system_tests = qpid-client-test quick_perftest quick_topictest run_header_test quick_txtest \
  run_msg_group_tests
TESTS += start_broker $(system_tests) python_tests stop_broker \
  run_ha_tests run_federation_tests run_federation_sys_tests \
  run_acl_tests run_cli_tests dynamic_log_level_test \
  dynamic_log_hires_timestamp run_queue_flow_limit_tests ipv6_test

EXTRA_DIST +=								\
  run_test vg_check							\
  run-unit-tests start_broker python_tests stop_broker 			\
  quick_topictest							\
  quick_perftest							\
  quick_txtest								\
  topictest								\
  run_header_test							\
  header_test.py							\
  ssl_test								\
  ping_broker								\
  config.null								\
  run_federation_tests							\
  run_federation_sys_tests                  \
  run_long_federation_sys_tests             \
  run_cli_tests								\
  run_acl_tests								\
  .valgrind.supp							\
  MessageUtils.h							\
  TestMessageStore.h							\
  TxMocks.h								\
  run_perftest								\
  ring_queue_test							\
  run_ring_queue_test							\
  dynamic_log_level_test						\
  dynamic_log_hires_timestamp                                           \
  qpid-ctrl								\
  CMakeLists.txt							\
  windows/DisableWin32ErrorWindows.cpp					\
  background.ps1							\
  find_prog.ps1								\
  python_tests.ps1							\
  quick_topictest.ps1							\
  run_federation_tests.ps1						\
  run_header_test.ps1							\
  run_test.ps1								\
  start_broker.ps1							\
  stop_broker.ps1							\
  topictest.ps1                                                         \
  run_queue_flow_limit_tests						\
  run_msg_group_tests							\
  ipv6_test								\
  run_ha_tests								\
  ha_test.py								\
  ha_tests.py								\
  brokertest.py								\
  ha_store_tests.py							\
  test_env.ps1.in

check_LTLIBRARIES += libdlclose_noop.la
libdlclose_noop_la_LDFLAGS = -module -rpath $(abs_builddir)
libdlclose_noop_la_SOURCES = dlclose_noop.c

CLEANFILES+=valgrind.out *.log *.vglog* dummy_test qpidd.port $(unit_wrappers)

# Longer running stability tests, not run by default check: target.
# Not run under valgrind, too slow

LONG_TESTS+=start_broker \
 fanout_perftest shared_perftest multiq_perftest topic_perftest run_ring_queue_test \
 run_msg_group_tests_soak \
 stop_broker \
 run_long_federation_sys_tests

EXTRA_DIST+=						\
	fanout_perftest					\
	shared_perftest					\
	multiq_perftest					\
	topic_perftest					\
	sasl_test_setup.sh                              \
	run_msg_group_tests_soak			\
	qpidd-empty.conf

check-long:
	$(MAKE) check TESTS="$(LONG_TESTS)" VALGRIND=

# Things that should be built before the check target runs.
check-am: python_prep test_env.sh install_env.sh sasl_config

PYTHON_SRC_DIR=$(abs_srcdir)/../../../python
PYTHON_BLD_DIR=$(abs_builddir)/python

# Generate python client as part of the all-am target so it gets built before tests.
all-am: python_prep

python_prep:
	if test -d $(PYTHON_SRC_DIR); \
	then cd $(PYTHON_SRC_DIR) && python $(PYTHON_SRC_DIR)/setup.py install \
		--prefix=$(PYTHON_BLD_DIR) --install-lib=$(PYTHON_BLD_DIR) \
		--install-scripts=$(PYTHON_BLD_DIR)/commands; \
	else echo "WARNING: python client not built, missing $(PYTHON_SRC_DIR)"; fi

sasl_config: sasl_test_setup.sh
	sh $(srcdir)/sasl_test_setup.sh
	touch sasl_config

CLEAN_LOCAL += sasl_config

clean-local:
	rm -rf $(CLEAN_LOCAL)

include testagent.mk
include brokermgmt.mk

