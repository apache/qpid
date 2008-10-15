dmodule_LTLIBRARIES += xml.la

xml_la_SOURCES =  \
	qpid/xml/XmlExchange.cpp \
	qpid/xml/XmlExchange.h \
	qpid/xml/XmlExchangePlugin.cpp

xml_la_LIBADD = -lxerces-c -lxqilla libqpidbroker.la

xml_la_LDFLAGS = $(PLUGINLDFLAGS)
