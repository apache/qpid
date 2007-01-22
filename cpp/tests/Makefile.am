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

EXTRA_DIST =		\
  topictest		\
  qpid_test_plugin.h	\
  MockConnectionInputHandler.h


client_exe_tests =	\
  client_test		\
  echo_service		\
  topic_listener	\
  topic_publisher

broker_tests =		\
  AccumulatedAckTest	\
  ChannelTest		\
  ConfigurationTest	\
  ExchangeTest		\
  HeadersExchangeTest	\
  InMemoryContentTest	\
  LazyLoadedContentTest	\
  MessageBuilderTest	\
  MessageTest		\
  QueueRegistryTest	\
  QueueTest		\
  QueuePolicyTest	\
  TopicExchangeTest	\
  TxAckTest		\
  TxBufferTest		\
  TxPublishTest		\
  ValueTest		\
  MessageHandlerTest 

# Tests that use the in-process BrokerSingleton to test client-broker
# interaction. Not strictly unit tests.
round_trip_tests =		#  TODO aconway 2007-01-22: RequestResponseTest

framing_tests =		\
  FieldTableTest	\
  FramingTest		\
  HeaderTest

misc_tests =		\
  ExceptionTest

posix_tests =		\
  EventChannelTest	\
  EventChannelThreadsTest

unit_tests =		\
  $(broker_tests)	\
  $(framing_tests)	\
  $(misc_tests) 	\
  $(round_trip_tests)

noinst_PROGRAMS = $(client_exe_tests)

CLIENT_TESTS = client_test quick_topictest
TESTS = run-unit-tests start_broker $(CLIENT_TESTS) python_tests kill_broker
EXTRA_DIST += $(TESTS) topictest

include gen.mk

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
	    echo $${i}_la_LIBADD = '$$(lib_common)';			\
	    echo $${i}_la_LIBADD += '$$(lib_broker) $$(extra_libs)';	\
	    echo $${i}_la_LDFLAGS = "-module -rpath `pwd`";	\
	  done;					\
	  echo "check_LTLIBRARIES =$$libs";	\
	)					\
	> $@-t
	mv $@-t $@
