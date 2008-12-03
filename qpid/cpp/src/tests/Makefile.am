AM_CXXFLAGS = $(WARNING_CFLAGS) -DBOOST_TEST_DYN_LINK
INCLUDES =  -I$(srcdir)/.. -I$(srcdir)/../gen -I$(top_builddir)/src/gen

abs_builddir=@abs_builddir@
extra_libs = 
lib_client = $(abs_builddir)/../libqpidclient.la
lib_common = $(abs_builddir)/../libqpidcommon.la
lib_broker = $(abs_builddir)/../libqpidbroker.la
# lib_amqp_0_10 = $(abs_builddir)/../libqpidamqp_0_10.la

#
# Initialize variables that are incremented with +=
# 
check_PROGRAMS=
check_LTLIBRARIES=
TESTS=
EXTRA_DIST=
CLEANFILES=

#
# Unit test program
#
# Unit tests are built as a single program to reduce valgrind overhead
# when running the tests. If you want to build a subset of the tests do 
#   rm -f unit_test; make unit_test unit_test_OBJECTS="unit_test.o SelectedTest.o"
# 

TESTS+=unit_test
check_PROGRAMS+=unit_test
unit_test_LDADD=-lboost_unit_test_framework -lboost_regex  \
	$(lib_client) $(lib_broker)

unit_test_SOURCES= unit_test.cpp unit_test.h \
	BrokerFixture.h SocketProxy.h \
	exception_test.cpp \
	RefCounted.cpp \
	SessionState.cpp Blob.cpp logging.cpp \
	AsyncCompletion.cpp \
	Url.cpp Uuid.cpp \
	Shlib.cpp FieldValue.cpp FieldTable.cpp Array.cpp \
	QueueOptionsTest.cpp \
	InlineAllocator.cpp \
	InlineVector.cpp \
	ClientSessionTest.cpp \
	SequenceSet.cpp \
	StringUtils.cpp \
	IncompleteMessageList.cpp \
	RangeSet.cpp \
	AtomicValue.cpp \
	QueueTest.cpp \
	AccumulatedAckTest.cpp \
	DtxWorkRecordTest.cpp \
	DeliveryRecordTest.cpp \
	ExchangeTest.cpp \
	HeadersExchangeTest.cpp \
	MessageTest.cpp \
	QueueRegistryTest.cpp \
	QueuePolicyTest.cpp \
	FramingTest.cpp \
	HeaderTest.cpp \
	SequenceNumberTest.cpp \
	TimerTest.cpp \
	TopicExchangeTest.cpp \
	TxBufferTest.cpp \
	TxPublishTest.cpp \
	MessageBuilderTest.cpp \
	ConnectionOptions.h \
	ForkedBroker.h \
	ManagementTest.cpp \
	MessageReplayTracker.cpp

if HAVE_XML
unit_test_SOURCES+= XmlClientSessionTest.cpp
endif


# Disabled till we move to amqp_0_10 codec.
# 	amqp_0_10/serialize.cpp allSegmentTypes.h \
# 	amqp_0_10/ProxyTemplate.cpp \
# 	amqp_0_10/apply.cpp \
# 	amqp_0_10/Map.cpp \
# 	amqp_0_10/handlers.cpp 


check_LTLIBRARIES += libshlibtest.la
libshlibtest_la_LDFLAGS = -module -rpath $(abs_builddir)
libshlibtest_la_SOURCES = shlibtest.cpp

include cluster.mk
if SSL
include ssl.mk
endif

#
# Other test programs
#
check_PROGRAMS+=perftest
perftest_SOURCES=perftest.cpp test_tools.h TestOptions.h ConnectionOptions.h
perftest_LDADD=$(lib_client) 

check_PROGRAMS+=txtest
txtest_SOURCES=txtest.cpp  TestOptions.h ConnectionOptions.h
txtest_LDADD=$(lib_client) 

check_PROGRAMS+=latencytest
latencytest_SOURCES=latencytest.cpp TestOptions.h ConnectionOptions.h
latencytest_LDADD=$(lib_client) 

check_PROGRAMS+=echotest
echotest_SOURCES=echotest.cpp TestOptions.h ConnectionOptions.h
echotest_LDADD=$(lib_client) 

check_PROGRAMS+=client_test
client_test_SOURCES=client_test.cpp TestOptions.h ConnectionOptions.h
client_test_LDADD=$(lib_client) 

check_PROGRAMS+=topic_listener
topic_listener_SOURCES=topic_listener.cpp TestOptions.h ConnectionOptions.h
topic_listener_LDADD=$(lib_client) 

check_PROGRAMS+=topic_publisher
topic_publisher_SOURCES=topic_publisher.cpp TestOptions.h ConnectionOptions.h
topic_publisher_LDADD=$(lib_client) 

check_PROGRAMS+=publish
publish_SOURCES=publish.cpp TestOptions.h ConnectionOptions.h
publish_LDADD=$(lib_client) 

check_PROGRAMS+=consume
consume_SOURCES=consume.cpp  TestOptions.h ConnectionOptions.h
consume_LDADD=$(lib_client) 

check_PROGRAMS+=header_test
header_test_SOURCES=header_test.cpp TestOptions.h ConnectionOptions.h
header_test_LDADD=$(lib_client) 

check_PROGRAMS+=failover_soak
failover_soak_SOURCES=failover_soak.cpp  ForkedBroker.h
failover_soak_LDADD=$(lib_client) 

check_PROGRAMS+=declare_queues
declare_queues_SOURCES=declare_queues.cpp  
declare_queues_LDADD=$(lib_client) 

check_PROGRAMS+=replaying_sender
replaying_sender_SOURCES=replaying_sender.cpp  
replaying_sender_LDADD=$(lib_client) 

check_PROGRAMS+=resuming_receiver
resuming_receiver_SOURCES=resuming_receiver.cpp  
resuming_receiver_LDADD=$(lib_client) 

check_PROGRAMS+=txshift
txshift_SOURCES=txshift.cpp  TestOptions.h ConnectionOptions.h
txshift_LDADD=$(lib_client) 

check_PROGRAMS+=txjob
txjob_SOURCES=txjob.cpp  TestOptions.h ConnectionOptions.h
txjob_LDADD=$(lib_client) 

check_PROGRAMS+=receiver
receiver_SOURCES=receiver.cpp  TestOptions.h ConnectionOptions.h
receiver_LDADD=$(lib_client) 

check_PROGRAMS+=sender
sender_SOURCES=sender.cpp  TestOptions.h ConnectionOptions.h
sender_LDADD=$(lib_client) 


TESTS_ENVIRONMENT = VALGRIND=$(VALGRIND) srcdir=$(srcdir) QPID_DATA_DIR= BOOST_TEST_SHOW_PROGRESS=yes $(srcdir)/run_test 

system_tests = client_test quick_perftest quick_topictest run_header_test quick_txtest
TESTS += start_broker $(system_tests) python_tests stop_broker run_federation_tests run_acl_tests 

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
  config.null								\
  ais_check								\
  run_federation_tests							\
  run_acl_tests								\
  .valgrind.supp							\
  MessageUtils.h							\
  TestMessageStore.h							\
  TxMocks.h								\
  start_cluster stop_cluster restart_cluster

check_LTLIBRARIES += libdlclose_noop.la
libdlclose_noop_la_LDFLAGS = -module -rpath $(abs_builddir)
libdlclose_noop_la_SOURCES = dlclose_noop.c

CLEANFILES+=valgrind.out *.log *.vglog* dummy_test $(unit_wrappers)

# FIXME aconway 2008-05-23: Disabled interop_runner because it uses
# the obsolete Channel class.  Convert to Session and re-enable.
# 
# check_PROGRAMS += interop_runner

# interop_runner_SOURCES = 	\
#   interop_runner.cpp	 	\
#   SimpleTestCaseBase.cpp	\
#   BasicP2PTest.cpp		\
#   BasicPubSubTest.cpp		\
#   SimpleTestCaseBase.h		\
#   BasicP2PTest.h		\
#   BasicPubSubTest.h		\
#   TestCase.h			\
#   TestOptions.h ConnectionOptions.h
# interop_runner_LDADD = $(lib_client) $(lib_common) $(extra_libs)


# Longer running stability tests, not run by default check: target.
# Not run under valgrind, too slow
LONG_TESTS=fanout_perftest shared_perftest multiq_perftest topic_perftest run_failover_soak
EXTRA_DIST+=$(LONG_TESTS) run_perftest
check-long:
	$(MAKE) check TESTS="start_broker $(LONG_TESTS) stop_broker" VALGRIND=
