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
  .vg-supp		\
  setup			\
  env			\
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

abs_builddir = @abs_builddir@
abs_srcdir = @abs_srcdir@

TESTS_ENVIRONMENT =			\
  VALGRIND=$(VALGRIND)			\
  abs_builddir='$(abs_builddir)'	\
  PATH="$(abs_srcdir)$(PATH_SEPARATOR)$(abs_builddir)/../src$(PATH_SEPARATOR)$$PATH" \
  abs_srcdir='$(abs_srcdir)'

TESTS = run-unit-tests run-python-tests daemon_test
EXTRA_DIST += $(TESTS)

CLEANFILES=qpidd.log
DISTCLEANFILES=gen.mk

include gen.mk

extra_libs = $(CPPUNIT_LIBS)
lib_client = $(abs_builddir)/../lib/client/libqpidclient.la
lib_common = $(abs_builddir)/../lib/common/libqpidcommon.la
lib_broker = $(abs_builddir)/../lib/broker/libqpidbroker.la

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
