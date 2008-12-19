# Settings to build the examples in automake
AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES = -I$(top_srcdir)/src -I$(top_srcdir)/src/gen -I$(top_builddir)/src -I$(top_builddir)/src/gen
CLIENT_LIB=$(top_builddir)/src/libqpidclient.la

# Generate a simple non-automake Makefile for distribution.
MAKEDIST=.libs/Makefile

$(MAKEDIST): Makefile
	mkdir -p .libs
	@$(ECHO) CXX=$(CXX)                     > $(MAKEDIST)
	@$(ECHO) CXXFLAGS=$(CXXFLAGS)           >> $(MAKEDIST)
	@$(ECHO) LDFLAGS=-lqpidclient           >> $(MAKEDIST)
	@$(ECHO)                                >> $(MAKEDIST)
	@$(ECHO) all: $(noinst_PROGRAMS)       >> $(MAKEDIST)
	@$(ECHO)                                >> $(MAKEDIST)
	@$(ECHO) clean:                         >> $(MAKEDIST)
	@$(ECHO) "	rm -f $(noinst_PROGRAMS)"  >> $(MAKEDIST)


