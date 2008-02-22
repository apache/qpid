AM_CXXFLAGS = $(WARNING_CFLAGS) $(CPPUNIT_CXXFLAGS)   $(APR_CXXFLAGS) -DBOOST_TEST_DYN_LINK
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
unit_test_LDADD=-lboost_unit_test_framework -lboost_regex \
	$(lib_client) $(lib_broker) 
unit_test_SOURCES= unit_test.cpp unit_test.h \
	BrokerFixture.h SocketProxy.h \
	exception_test.cpp \
	RefCounted.cpp \
	SessionState.cpp Blob.cpp logging.cpp \
	Url.cpp Uuid.cpp \
	Shlib.cpp FieldValue.cpp FieldTable.cpp Array.cpp \
	InlineVector.cpp \
	ISList.cpp IList.cpp \
	ClientSessionTest.cpp \
	serialize.cpp
# FIXME aconway 2008-02-20: removed RefCountedMap.cpp  due to valgrind error.

check_LTLIBRARIES += libshlibtest.la
libshlibtest_la_LDFLAGS = -module -rpath $(abs_builddir)
libshlibtest_la_SOURCES = shlibtest.cpp

include cluster.mk

#
# Other test programs
#
check_PROGRAMS+=perftest
perftest_SOURCES=perftest.cpp test_tools.h TestOptions.h
perftest_LDADD=$(lib_client) 

check_PROGRAMS+=txtest
txtest_SOURCES=txtest.cpp  TestOptions.h
txtest_LDADD=$(lib_client) 

check_PROGRAMS+=latencytest
latencytest_SOURCES=latencytest.cpp  TestOptions.h
latencytest_LDADD=$(lib_client) 

# NB: CppUnit test libraries below will be migrated to boost test programs.
#

# cppunit tests
broker_unit_tests =	\
  AccumulatedAckTest	\
  DtxWorkRecordTest     \
  DeliveryRecordTest    \
  ExchangeTest		\
  HeadersExchangeTest	\
  MessageTest		\
  QueueRegistryTest	\
  QueueTest		\
  QueuePolicyTest	\
  TimerTest		\
  TopicExchangeTest	\
  TxAckTest		\
  TxBufferTest		\
  TxPublishTest		\
  MessageBuilderTest

#client_unit_tests =	\
#  ClientChannelTest     

framing_unit_tests =	\
  FramingTest		\
  HeaderTest		\
  SequenceNumberTest

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
  topic_listener	\
  topic_publisher	
#  echo_service

check_PROGRAMS += $(testprogs) interop_runner

TESTS_ENVIRONMENT = VALGRIND=$(VALGRIND) srcdir=$(srcdir) QPID_DATA_DIR= $(srcdir)/run_test 

system_tests = client_test quick_perftest quick_topictest
TESTS += run-unit-tests start_broker $(system_tests) python_tests stop_broker 

EXTRA_DIST +=								\
  run_test vg_check							\
  run-unit-tests start_broker python_tests stop_broker 			\
  quick_topictest							\
  quick_perftest							\
  topictest								\
  .valgrind.supp							\
  .valgrindrc								\
  MessageUtils.h							\
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

CLEANFILES+=valgrind.out *.log *.vglog dummy_test $(unit_wrappers)
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

# Longer running stability tests, not run by default check: target.
# Not run under valgrind, too slow
LONG_TESTS=fanout_perftest shared_perftest multiq_perftest topic_perftest
EXTRA_DIST+=$(LONG_TESTS) run_perftest
check-long:
	$(MAKE) check TESTS="start_broker $(LONG_TESTS) stop_broker" VALGRIND=
