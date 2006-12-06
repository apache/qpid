AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES = \
  -I$(top_srcdir)/gen \
  -I$(top_srcdir)/lib/broker \
  -I$(top_srcdir)/lib/common \
  -I$(top_srcdir)/lib/common/framing \
  -I$(top_srcdir)/lib/common/sys

LDADD =				\
  ../lib/broker/libbroker.la	\
  ../lib/common/libcommon.la

bin_PROGRAMS = qpidd
qpidd_SOURCES = qpidd.cpp
