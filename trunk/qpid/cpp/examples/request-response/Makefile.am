examplesdir=$(pkgdatadir)/examples/request-response

include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=client server

client_SOURCES=client.cpp
client_LDADD=$(CLIENT_LIB)

server_SOURCES=server.cpp
server_LDADD=$(CLIENT_LIB)

examples_DATA=               \
	server.cpp           \
	client.cpp           \
	$(MAKEDIST)

EXTRA_DIST=                  \
	$(examples_DATA)     \
	verify               \
	verify.in            \
	verify_cpp_python    \
	verify_cpp_python.in \
	verify_python_cpp    \
	verify_python_cpp.in





