# Settings to build the examples in automake
AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES = -I$(top_srcdir)/src -I$(top_srcdir)/src/gen -I$(top_builddir)/src -I$(top_builddir)/src/gen
CLIENT_LIB=$(top_builddir)/src/libqpidclient.la
CONSOLE_LIB=$(top_builddir)/src/libqmfconsole.la
CLIENTFLAGS=-lqpidclient
CONSOLEFLAGS=-lqmfconsole

# Generate a simple non-automake Makefile for distribution.
MAKEDIST=.libs/Makefile

$(MAKEDIST): Makefile
	mkdir -p .libs
	@(echo CXX=$(CXX)                   ; \
	echo CXXFLAGS=$(CXXFLAGS)           ; \
	echo LDFLAGS=$(MAKELDFLAGS)         ; \
	echo                                ; \
	echo all: $(noinst_PROGRAMS)        ; \
	echo                                ; \
	echo clean:                         ; \
	echo "	rm -f $(noinst_PROGRAMS)"   ; \
	) > $(MAKEDIST)

