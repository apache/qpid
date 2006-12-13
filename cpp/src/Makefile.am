AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES = \
  -I$(top_srcdir)/gen \
  -I$(top_srcdir)/lib/broker \
  -I$(top_srcdir)/lib/common \
  -I$(top_srcdir)/lib/common/framing \
  -I$(top_srcdir)/lib/common/sys

LDADD =				\
  ../lib/broker/libqpidbroker.la	\
  ../lib/common/libqpidcommon.la

sbin_PROGRAMS = qpidd
qpidd_SOURCES = qpidd.cpp

# Force build of qpidd during dist phase so help2man will work.
dist-hook: $(sbin_PROGRAMS)
