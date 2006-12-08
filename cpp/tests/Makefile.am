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

# FIXME: have e.g., topicall, run as part of "make check"?
EXTRA_DIST =		\
  env			\
  broker		\
  topicall		\
  topictest		\
  qpid_test_plugin.h	\
  APRBaseTest.cpp

client_tests =		\
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
  ValueTest

framing_tests =		\
  BodyHandlerTest	\
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
  $(misc_tests)


noinst_PROGRAMS = $(client_tests)

check: run-unit-tests run-python-tests

.PHONY: run-unit-tests
run-unit-tests: $(check_LTLIBRARIES)
	DllPlugInTester -c -b .libs/*.so

run-python-tests: $(check_LTLIBRARIES) ../src/qpidd
	../src/qpidd > qpidd.log 2>&1 & pid=$$!;	\
	trap 'status=$$?; kill $$pid; exit $$status' 0;	\
	trap '(exit $$?); exit $$?' 1 2 13 15;		\
	cd ../../python ; ./run-tests -v -I cpp_failing.txt

include gen.mk

abs_builddir = @abs_builddir@
extra_libs = $(CPPUNIT_LIBS)
lib_client = $(abs_builddir)/../lib/client/libclient.la
lib_common = $(abs_builddir)/../lib/common/libcommon.la
lib_broker = $(abs_builddir)/../lib/broker/libbroker.la

gen.mk: Makefile.am
	(					\
	  for i in $(client_tests); do		\
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
