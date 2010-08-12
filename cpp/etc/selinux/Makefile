# Makefile to build and install the selinux policies in this directory.
# Needs to be run as root.

POLICIES = qpidd.pp qpiddevel.pp
SOURCES = $(POLICIES:.pp=.te)

all: $(POLICIES)

$(POLICIES): $(SOURCES)
	make -f /usr/share/selinux/devel/Makefile

install: $(POLICIES)
	for p in $(POLICIES); do /usr/sbin/semodule -i $$p; done

clean:
	rm -rf *~ *.pp *.fc *.if tmp
