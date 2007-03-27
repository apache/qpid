AM_CXXFLAGS = $(WARNING_CFLAGS) $(CPPUNIT_CXXFLAGS)
INCLUDES =				\
  -I$(top_srcdir)/gen			\
  -I$(top_srcdir)/lib			\
  -I$(top_srcdir)/lib/client		\
  -I$(top_srcdir)/lib/broker		\
  -I$(top_srcdir)/lib/common		\
  -I$(top_srcdir)/lib/common/sys	\
  -I$(top_srcdir)/lib/common/framing	\
  $(APR_CXXFLAGS)

# Unit tests
broker_tests =		\
  AccumulatedAckTest	\
  BrokerChannelTest 	\
  ConfigurationTest	\
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

client_tests =		\
  ClientChannelTest

framing_tests =		\
  FieldTableTest	\
  FramingTest		\
  HeaderTest

misc_tests =		\
  ExceptionTest		\
  ProducerConsumerTest

posix_tests =		\
  EventChannelTest	\
  EventChannelThreadsTest

unit_tests =		\
  $(broker_tests)	\
  $(client_tests)	\
  $(framing_tests)	\
  $(misc_tests) 	

# Executable client tests

client_exe_tests =	\
  client_test		\
  echo_service		\
  topic_listener	\
  topic_publisher

noinst_PROGRAMS = $(client_exe_tests)

TESTS_ENVIRONMENT =			\
  VALGRIND=$(VALGRIND)			\
  abs_builddir='$(abs_builddir)'	\
  PATH="$(abs_builddir)/../src$(PATH_SEPARATOR)$$PATH" \
  abs_srcdir='$(abs_srcdir)'

CLIENT_TESTS = client_test quick_topictest
TESTS = run-unit-tests start_broker $(CLIENT_TESTS) python_tests kill_broker

EXTRA_DIST =		\
  $(TESTS) 		\
  .vg-supp		\
  InProcessBroker.h	\
  MockChannel.h		\
  MockConnectionInputHandler.h \
  qpid_test_plugin.h	\
  setup			\
  topicall		\
  topictest		\
  APRBaseTest.cpp

CLEANFILES=qpidd.log
DISTCLEANFILES=gen.mk

include gen.mk

check_LTLIBRARIES += libdlclose_noop.la
libdlclose_noop_la_LDFLAGS = -module -rpath /home/aconway/svn/qpid/cpp/tests
libdlclose_noop_la_SOURCES = dlclose_noop.c


abs_builddir = @abs_builddir@
extra_libs = $(CPPUNIT_LIBS)
lib_client = $(abs_builddir)/../lib/client/libqpidclient.la
lib_common = $(abs_builddir)/../lib/common/libqpidcommon.la
lib_broker = $(abs_builddir)/../lib/broker/libqpidbroker.la

gen.mk: Makefile.am
	(					\
	  for i in $(client_exe_tests); do		\
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

check: $(check_LTLIBRARIES) $(lib_common) $(lib_client) $(lib_broker)
