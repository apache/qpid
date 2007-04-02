include gen-src.mk

BUILT_SOURCES = $(generated_sources) $(generated_headers)
pkginclude_HEADERS=$(generated_headers)

# Distribute the generated sources, at least for now, since
# the generator code is in java.
EXTRA_DIST = $(BUILT_SOURCES)
DISTCLEANFILES = $(BUILT_SOURCES) timestamp gen-src.mk

# Don't attempt to run the code generator unless configure has set
# CAN_GENERATE_CODE, indicating that the amqp.xml and tools needed
# to run the code generator are available.
#
if CAN_GENERATE_CODE

gentools_dir = $(srcdir)/../../gentools
spec_dir = $(srcdir)/../../../specs

# FIXME aconway 2007-01-04: Enabling Basic class until
# new messaging class is ready to replace it.
# spec = $(spec_dir)/amqp.0-9.xml $(spec_dir)/amqp-errata.0-9.xml $(spec_dir)/amqp-nogen.0-9.xml
spec = $(spec_dir)/amqp.0-9.xml $(spec_dir)/amqp-errata.0-9.xml

gentools_srcdir = $(gentools_dir)/src/org/apache/qpid/gentools

$(BUILT_SOURCES) timestamp: $(spec) $(java_sources) $(cxx_templates) Makefile.am
	rm -f $(generated_sources)
	cd $(gentools_srcdir) && rm -f *.class && $(JAVAC) *.java
	$(JAVA) -cp $(gentools_dir)/src org.apache.qpid.gentools.Main \
	        -c -o . -t $(gentools_dir)/templ.cpp $(spec)
	touch timestamp

gen-src.mk: timestamp
	./make-gen-src-mk.sh $(gentools_dir) $(gentools_srcdir) > $@-t
	mv $@-t $@
endif
