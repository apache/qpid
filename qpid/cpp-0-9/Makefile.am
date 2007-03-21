AUTOMAKE_OPTIONS = 1.9.2 foreign
ACLOCAL_AMFLAGS = -I m4

EXTRA_DIST = \
  LICENSE NOTICE README \
  etc/qpidd \
  $(PACKAGE).spec \
  $(PACKAGE).spec.in

SUBDIRS = gen lib src docs/api docs/man rpm tests

# Update libtool, if needed.
libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status --recheck

# This cannot be done by AC_CONFIG_FILES, because automake's
# make distcheck target does not like auto-generated files
# being included in the distributed archive.
qpidc.spec: %: %.in
	sed 's/@''PACKAGE@/@PACKAGE@/;s/@''VERSION@/@VERSION@/' $< > $@-tmp
	mv $@-tmp $@
MAINTAINERCLEANFILES = $(PACKAGE).spec

.PHONY: rpm srpm
rpm srpm:
	cd rpm && $(MAKE) $(MAKEFLAGS_AM) $@
