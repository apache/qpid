AUTOMAKE_OPTIONS = 1.9.2 foreign
ACLOCAL_AMFLAGS = -I m4

EXTRA_DIST = \
  LICENSE NOTICE README RELEASE_NOTES DESIGN\
  xml/cluster.xml

SUBDIRS = managementgen etc src docs/api docs/man examples

# Update libtool, if needed.
libtool: $(LIBTOOL_DEPS)
	$(SHELL) ./config.status --recheck



