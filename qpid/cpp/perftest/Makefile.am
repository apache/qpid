noinst_PROGRAMS=topic_listener topic_publisher
INCLUDES=-I$(top_srcdir)/src -I$(top_srcdir)/src/gen -I$(top_builddir)src/gen
lib_client = $(top_builddir)/src/libqpidclient.la


topic_listener_LDADD=$(lib_client)
topic_listener_SOURCES=topic_listener.cpp

topic_publisher_LDADD=$(lib_client)
topic_publisher_SOURCES=topic_publisher.cpp

