
if HAVE_PYTHON_DEVEL

INCLUDES = -I$(top_srcdir)/src/qmf -I$(top_srcdir)/src -I$(top_srcdir)/src/gen

EXTRA_DIST = python.i

generated_file_list = \
	qmfengine.cpp \
	qmfengine.py

$(generated_file_list): python.i ../qmfengine.i
	swig -python -c++ -Wall -I/usr/include $(INCLUDES) $(QPID_CXXFLAGS) -I$(top_srcdir)/src/qmf -o qmfengine.cpp python.i

AM_CPPFLAGS = $(QPID_CXXFLAGS) $(INCLUDES) -I$(srcdir)/qmf -I$(PYTHON_INC)
AM_CXXFLAGS = $(QPID_CXXFLAGS)

pylibdir = $(PYTHON_LIB)

lib_LTLIBRARIES = _qmfengine.la

_qmfengine_la_LDFLAGS = -avoid-version -module -shrext "$(PYTHON_SO)"
_qmfengine_la_LIBADD = $(PYTHON_LIBS) -L$(QPID_SRC)/.libs -lqpidclient $(top_srcdir)/src/libqmfcommon.la $(top_srcdir)/src/libqmfagent.la

_qmfengine_la_SOURCES = \
	qmfengine.cpp

endif  # HAVE_PYTHON_DEVEL

