examplesdir=$(pkgdatadir)/examples/pub-sub

include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=topic_listener topic_publisher

topic_listener_SOURCES=topic_listener.cpp
topic_listener_LDADD=$(CLIENT_LIB)

topic_publisher_SOURCES=topic_publisher.cpp
topic_publisher_LDADD=$(CLIENT_LIB)

examples_DATA=               \
	topic_listener.cpp   \
	topic_publisher.cpp  \
	$(MAKEDIST)

EXTRA_DIST=                  \
	$(examples_DATA)     \
	verify               \
	verify.in            \
	verify_cpp_python    \
	verify_cpp_python.in \
	verify_python_cpp    \
	verify_python_cpp.in





