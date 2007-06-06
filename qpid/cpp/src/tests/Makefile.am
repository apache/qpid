AM_CXXFLAGS = $(WARNING_CFLAGS) $(CPPUNIT_CXXFLAGS)   $(APR_CXXFLAGS)
INCLUDES =  -I$(srcdir)/.. -I$(srcdir)/../gen -I$(top_builddir)/src/gen


# Unit test programs
UNIT_TESTS=

UNIT_TESTS+=logging
logging_SOURCES=unit/logging.cpp unit/test_tools.h
logging_LDADD=-lboost_unit_test_framework -lboost_regex $(lib_common)

# NB: CppUnit test libraries below will be migrated to boost test programs.
# UNIT_TESTS+= ...

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
  HeaderTest

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

testprogs =		\
  client_test		\
  echo_service		\
  topic_listener	\
  topic_publisher

check_PROGRAMS = $(UNIT_TESTS) $(testprogs) interop_runner

# FIXME aconway 2007-05-30: TESTS_ENVIRONMENT should have ./run_test
# as below to run valgrind on all test programs.
# Disabled temporarily till leaks in client_test are fixed.
#
#TESTS_ENVIRONMENT = VALGRIND=$(VALGRIND) srcdir=$(srcdir) ./run_test
TESTS_ENVIRONMENT = VALGRIND=$(VALGRIND) srcdir=$(srcdir)

SYSTEM_TESTS = client_test quick_topictest
TESTS = $(UNIT_TESTS) run-unit-tests start_broker $(SYSTEM_TESTS) python_tests kill_broker daemon_test

EXTRA_DIST =								\
  test_env								\
  run-unit-tests start_broker python_tests kill_broker daemon_test	\
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
libdlclose_noop_la_LDFLAGS = -module -rpath /home/aconway/svn/qpid/cpp/tests
libdlclose_noop_la_SOURCES = dlclose_noop.c

abs_builddir = @abs_builddir@
extra_libs = $(CPPUNIT_LIBS)
lib_client = $(abs_builddir)/../libqpidclient.la
lib_common = $(abs_builddir)/../libqpidcommon.la
lib_broker = $(abs_builddir)/../libqpidbroker.la

gen.mk: Makefile.am
	(					\
	  for i in $(testprogs); do		\
	    echo $${i}_SOURCES = $$i.cpp;	\
	    echo $${i}_LDADD = '$$(lib_client) $$(lib_common) $$(extra_libs)'; \
	  done;					\
	  libs=;				\
	  for i in $(unit_tests); do		\
	    libs="$$libs $${i}.la";		\
	    echo $${i}_la_SOURCES = $$i.cpp;	\
	    echo $${i}_la_LIBADD = '$$(lib_common) $$(lib_client)';	\
	    echo $${i}_la_LIBADD += '$$(lib_broker) $$(extra_libs)';	\
	    echo $${i}_la_LDFLAGS = "-module -rpath `pwd`";	\
	  done;					\
	  echo "check_LTLIBRARIES =$$libs";	\
	)					\
	> $@-t
	mv $@-t $@

check: .valgrindrc .valgrind.supp

check-unit:
	$(MAKE) check TESTS=$(UNIT_TESTS) run-unit-tests

# Create a copy so that can be modified without risk of committing the changes.
.valgrindrc: .valgrindrc-default
	cp $^ $@
.valgrind.supp: .valgrind.supp-default
	cp $^ $@

# Tell GNU make not to build targets in this directory in parallel.
# This is necessary because with two or more identical and simultaneous
# ltmain invocations, one may corrupt the temporaries of the other.
.NOTPARALLEL:

CLEANFILES=valgrind.out qpidd.log .valgrindrc .valgrind.supp
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
