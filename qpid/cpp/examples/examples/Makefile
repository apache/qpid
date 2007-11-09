SUBDIRS=direct fanout pub-sub request-response
all:
	for d in $(SUBDIRS); do ( cd $$d; $(MAKE) $@; ) ; done
clean:
	for d in $(SUBDIRS); do ( cd $$d; $(MAKE) $@; ) ; done

