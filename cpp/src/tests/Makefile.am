AM_CXXFLAGS = $(WARNING_CFLAGS) $(CPPUNIT_CXXFLAGS)   $(APR_CXXFLAGS)
INCLUDES =  -I$(srcdir)/.. -I$(top_builddir)/gen

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

noinst_PROGRAMS = $(testprogs)

TESTS_ENVIRONMENT = VALGRIND=$(VALGRIND) srcdir=$(srcdir)

CLIENT_TESTS = client_test quick_topictest
TESTS = run-unit-tests start_broker $(CLIENT_TESTS) python_tests kill_broker

EXTRA_DIST =					\
  test_env					\
  topictest					\
  quick_topictest				\
  run-unit-tests				\
  start_broker					\
  python_tests					\
  kill_broker					\
  .valgrind.supp				\
  .valgrindrc-default				\
  InProcessBroker.h				\
  MockChannel.h					\
  MockConnectionInputHandler.h			\
  TxMocks.h					\
  qpid_test_plugin.h				\
  APRBaseTest.cpp

CLEANFILES=qpidd.log
MAINTAINERCLEANFILES=gen.mk

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

check: .valgrindrc ../qpidd

check-unit:
	$(MAKE) check TESTS=run-unit-tests

# Create a copy so that can be modified without risk of committing the changes.
.valgrindrc: .valgrindrc-default
	cp $(srcdir)/.valgrindrc-default .valgrindrc

# Tell GNU make not to build targets in this directory in parallel.
# This is necessary because with two or more identical and simultaneous
# ltmain invocations, one may corrupt the temporaries of the other.
.NOTPARALLEL:
