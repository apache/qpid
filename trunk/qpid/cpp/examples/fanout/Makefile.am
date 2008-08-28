examplesdir=$(pkgdatadir)/examples/fanout

include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=fanout_producer listener
fanout_producer_SOURCES=fanout_producer.cpp
fanout_producer_LDADD=$(CLIENT_LIB)

listener_SOURCES=listener.cpp
listener_LDADD=$(CLIENT_LIB)

examples_DATA=               \
	fanout_producer.cpp  \
	listener.cpp         \
	$(MAKEDIST)

EXTRA_DIST=                  \
	$(examples_DATA)     \
	verify               \
	verify.in            \
	verify_cpp_python    \
	verify_cpp_python.in \
	verify_python_cpp    \
	verify_python_cpp.in 





