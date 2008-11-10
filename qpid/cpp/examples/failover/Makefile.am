examplesdir=$(pkgdatadir)/examples/failover

include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=declare_queues resuming_receiver replaying_sender

declare_queues_SOURCES=declare_queues.cpp
declare_queues_LDADD=$(CLIENT_LIB)

resuming_receiver_SOURCES=resuming_receiver.cpp
resuming_receiver_LDADD=$(CLIENT_LIB)

replaying_sender_SOURCES=replaying_sender.cpp
replaying_sender_LDADD=$(CLIENT_LIB)

examples_DATA=                \
	declare_queues.cpp    \
	resuming_receiver.cpp \
	replaying_sender.cpp  \
        $(MAKEDIST)

# FIXME aconway 2008-10-10: add verify scripts.
