# Makefile to build and install the selinux policies in this directory.
# Needs to be run as root.

POLICIES = qpidd.pp qpiddevel.pp
SOURCES = $(POLICIES:.pp=.te)

install: $(POLICIES)
	for p in $(POLICIES); do /usr/sbin/semodule -i $$p; done

$(POLICIES): $(SOURCES)
	make -f /usr/share/selinux/devel/Makefile

clean:
	rm -rf *~ *.pp *.fc *.if tmp
