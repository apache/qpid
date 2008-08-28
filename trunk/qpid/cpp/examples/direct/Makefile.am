examplesdir=$(pkgdatadir)/examples/direct

include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=direct_producer listener declare_queues
direct_producer_SOURCES=direct_producer.cpp
direct_producer_LDADD=$(CLIENT_LIB)

listener_SOURCES=listener.cpp
listener_LDADD=$(CLIENT_LIB)

declare_queues_SOURCES=declare_queues.cpp
declare_queues_LDADD=$(CLIENT_LIB)

examples_DATA=               \
	direct_producer.cpp  \
	listener.cpp         \
	declare_queues.cpp   \
        $(MAKEDIST)

EXTRA_DIST=	             \
	$(examples_DATA)     \
	verify               \
	verify.in            \
	verify_cpp_python    \
	verify_cpp_python.in \
	verify_python_cpp    \
	verify_python_cpp.in 

