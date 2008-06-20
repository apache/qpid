examplesdir=$(pkgdatadir)/examples/xml-exchange

include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=declare_queues xml_producer listener

declare_queues_SOURCES=declare_queues.cpp
declare_queues_LDADD=$(CLIENT_LIB)

xml_producer_SOURCES=xml_producer.cpp
xml_producer_LDADD=$(CLIENT_LIB)

listener_SOURCES=listener.cpp
listener_LDADD=$(CLIENT_LIB)

EXTRA_DIST=                \
	README

examples_DATA=             \
	$(EXTRA_DIST)      \
	declare_queues.cpp \
	listener.cpp       \
	xml_producer.cpp   \
	$(MAKEDIST)





