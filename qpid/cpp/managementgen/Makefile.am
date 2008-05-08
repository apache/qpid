managementgendir = $(datadir)/managementgen
dist_managementgen_SCRIPTS = \
	main.py
nobase_managementgen_DATA = \
	schema.py generate.py \
	templates/Args.h \
	templates/Class.cpp \
	templates/Class.h \
	templates/Makefile.mk \
	templates/Package.cpp \
	templates/Package.h \
	management-types.xml

dist_bin_SCRIPTS = managementgen

EXTRA_DIST = $(nobase_managementgen_DATA)

# This should depend on ../../specs/management-types.xml, but can't
# because it won't exist in a dist. This rule means that dist-gzip
# (and rpmbuild) cannot be run purely on the cpp/ directory, the
# cpp/../specs/ directory must exist, which is not a new dependency
# but this is an additional instance of the dependency.
#
# WARNING: Because this target does not have a proper dependency
# changes to the specs/management-types.xml will not be picked up!
management-types.xml:
	cp ../../specs/management-types.xml .
