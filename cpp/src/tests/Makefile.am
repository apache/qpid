AM_CXXFLAGS = $(WARNING_CFLAGS) $(CPPUNIT_CXXFLAGS)   $(APR_CXXFLAGS)
INCLUDES =  -I$(srcdir)/.. -I$(srcdir)/../gen -I$(top_builddir)/src/gen

abs_builddir=@abs_builddir@
extra_libs = $(CPPUNIT_LIBS)
lib_client = $(abs_builddir)/../libqpidclient.la
lib_common = $(abs_builddir)/../libqpidcommon.la
lib_broker = $(abs_builddir)/../libqpidbroker.la

#
# Initialize variables that are incremented with +=
# 
check_PROGRAMS=
check_LTLIBRARIES=
TESTS=
EXTRA_DIST=

#
# Unit test programs.
# 
TESTS+=logging
check_PROGRAMS+=logging
logging_SOURCES=logging.cpp test_tools.h
logging_LDADD=-lboost_unit_test_framework -lboost_regex $(lib_common) 

TESTS+=Url
check_PROGRAMS+=Url
Url_SOURCES=Url.cpp test_tools.h
Url_LDADD=-lboost_unit_test_framework $(lib_common) 

TESTS+=Uuid
check_PROGRAMS+=Uuid
Uuid_SOURCES=Uuid.cpp test_tools.h
Uuid_LDADD=-lboost_unit_test_framework $(lib_common) 

check_LTLIBRARIES += libshlibtest.la
libshlibtest_la_LDFLAGS = -module -rpath $(abs_builddir)
libshlibtest_la_SOURCES = shlibtest.cpp

TESTS+=Shlib
check_PROGRAMS+=Shlib
Shlib_SOURCES=Shlib.cpp
Shlib_LDADD=-lboost_unit_test_framework $(lib_common)

check_PROGRAMS+=ConcurrentQueue
ConcurrentQueue_SOURCES=ConcurrentQueue.cpp
ConcurrentQueue_LDADD=-lboost_test_exec_monitor $(lib_common)

check_PROGRAMS+=Serializer
Serializer_SOURCES=Serializer.cpp
Serializer_LDADD=-lboost_unit_test_framework $(lib_common)

include cluster.mk

# NB: CppUnit test libraries below will be migrated to boost test programs.
#

# Unit tests
broker_unit_tests =	\
  AccumulatedAckTest	\
  BrokerChannelTest 	\
  DtxWorkRecordTest     \
  ExchangeTest		\
  HeadersExchangeTest	\
  InMemoryContentTest	\
  LazyLoadedContentTest	\
  MessageBuilderTest	\
  MessageTest		\
  ReferenceTest         \
  QueueRegistryTest	\
  QueueTest		\
  QueuePolicyTest	\
  TimerTest		\
  TopicExchangeTest	\
  TxAckTest		\
  TxBufferTest		\
  TxPublishTest		\
  ValueTest		\
  MessageHandlerTest

#client_unit_tests =	\
  ClientChannelTest

framing_unit_tests =	\
  FieldTableTest	\
  FramingTest		\
  HeaderTest		\
  SequenceNumberTest

misc_unit_tests =	\
  ProducerConsumerTest

posix_unit_tests =	\
  EventChannelTest	\
  EventChannelThreadsTest

unit_tests =		\
  $(broker_unit_tests)	\
  $(client_unit_tests)	\
  $(framing_unit_tests)	\
  $(misc_unit_tests)

# Executables for client tests

testprogs=		\
  client_test		\
  exception_test	\
  echo_service		\
  topic_listener	\
  topic_publisher	

check_PROGRAMS += $(testprogs) interop_runner

TESTS_ENVIRONMENT = VALGRIND=$(VALGRIND) srcdir=$(srcdir) $(srcdir)/run_test

system_tests = client_test exception_test quick_topictest
TESTS += run-unit-tests start_broker $(system_tests) python_tests stop_broker 

EXTRA_DIST +=								\
  test_env run_test							\
  run-unit-tests start_broker python_tests stop_broker 			\
  quick_topictest							\
  topictest								\
  .valgrind.supp-default						\
  .valgrindrc-default							\
  InProcessBroker.h							\
  MockChannel.h								\
  MockConnectionInputHandler.h						\
  TxMocks.h								\
  qpid_test_plugin.h

include gen.mk

check_LTLIBRARIES += libdlclose_noop.la
libdlclose_noop_la_LDFLAGS = -module -rpath $(abs_builddir)
libdlclose_noop_la_SOURCES = dlclose_noop.c

gen.mk: Makefile.am
	(									\
	  for i in $(testprogs); do						\
	    echo $${i}_SOURCES = $$i.cpp;					\
	    echo $${i}_LDADD = '$$(lib_client) $$(lib_common) $$(extra_libs)';	\
	  done;									\
	  libs=;								\
	  for i in $(unit_tests); do						\
	    echo "check_LTLIBRARIES +=$${i}.la";				\
	    echo $${i}_la_SOURCES = $$i.cpp;					\
	    echo $${i}_la_LIBADD = '$$(lib_common) $$(lib_client)';		\
	    echo $${i}_la_LIBADD += '$$(lib_broker) $$(extra_libs)';		\
	    echo $${i}_la_LDFLAGS = "-module -rpath `pwd`";			\
	  done;									\
	)					\
	> $@-t
	mv $@-t $@

check-unit:
	$(MAKE) check TESTS=$(UNIT_TESTS) run-unit-tests

# Make sure valgrind files are generated.
all-am: .valgrind.supp .valgrindrc

# Create a copy so that can be modified without risk of committing the changes.
.valgrindrc: .valgrindrc-default
	cp $^ $@
.valgrind.supp: .valgrind.supp-default
	cp $^ $@

# Tell GNU make not to build targets in this directory in parallel.
# This is necessary because with two or more identical and simultaneous
# ltmain invocations, one may corrupt the temporaries of the other.
.NOTPARALLEL:

CLEANFILES=valgrind.out qpidd.log .valgrindrc .valgrind.supp dummy_test $(unit_wrappers)
MAINTAINERCLEANFILES=gen.mk

interop_runner_SOURCES = 	\
  interop_runner.cpp	 	\
  SimpleTestCaseBase.cpp	\
  BasicP2PTest.cpp		\
  BasicPubSubTest.cpp		\
  SimpleTestCaseBase.h		\
  BasicP2PTest.h		\
  BasicPubSubTest.h		\
  TestCase.h			\
  TestOptions.h
interop_runner_LDADD = $(lib_client) $(lib_common) $(extra_libs)
