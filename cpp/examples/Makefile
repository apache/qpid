SUBDIRS=direct fanout pub-sub request-response xml-exchange
all:
	for d in $(SUBDIRS); do ( cd $$d; $(MAKE) $@; ) ; done
clean:
	for d in $(SUBDIRS); do ( cd $$d; $(MAKE) $@; ) ; done

