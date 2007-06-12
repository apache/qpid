noinst_PROGRAMS=create_queue topic_listener topic_publisher
INCLUDES=-I$(top_srcdir)/src -I$(top_srcdir)/src/gen -I$(top_builddir)src/gen
lib_client = $(top_builddir)/src/libqpidclient.la


create_queue_LDADD=$(lib_client)
create_queue_SOURCES=create_queue.cpp

topic_listener_LDADD=$(lib_client)
topic_listener_SOURCES=topic_listener.cpp

topic_publisher_LDADD=$(lib_client)
topic_publisher_SOURCES=topic_publisher.cpp

