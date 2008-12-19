examplesdir=$(pkgdatadir)/examples/qmf-console

MAKELDFLAG = qmfconsole
include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=qmfc

qmfc_SOURCES=console.cpp
qmfc_LDADD=$(CONSOLE_LIB)

examples_DATA= \
	console.cpp \
	$(MAKEDIST)

