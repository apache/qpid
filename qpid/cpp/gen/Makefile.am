#
# Generate code from AMQP XML specification.
#
-include gen-src.mk

BUILT_SOURCES = gen-src.mk qpid/timestamp $(generated_cpp) $(generated_h)

# Distribute the generated sources, since the code generator depends
# on tools that may not be available in the build environment.
EXTRA_DIST = $(BUILT_SOURCES) 

# Install generated headers
nobase_include_HEADERS = $(generated_h)

# Don't attempt to run the code generator unless configure has set
# CAN_GENERATE_CODE, indicating that the amqp.xml and tools needed
# to run the code generator are available.
#
if CAN_GENERATE_CODE
gentools_dir = $(top_srcdir)/gentools
gentools_srcdir = $(gentools_dir)/src/org/apache/qpid/gentools
spec_dir = $(top_srcdir)/../specs
spec = $(spec_dir)/amqp.0-9.xml $(spec_dir)/amqp-errata.0-9.xml

qpid/timestamp $(generated_cpp) $(generated_h): $(spec) $(java_sources) $(cxx_templates) 
	mkdir -p qpid/framing
	cd $(gentools_srcdir) && rm -f *.class && $(JAVAC) *.java
	$(JAVA) -cp $(gentools_dir)/src org.apache.qpid.gentools.Main \
	        -c -o qpid/framing -t $(gentools_dir)/templ.cpp $(spec)
	touch qpid/timestamp

gen-src.mk: qpid gen-src-mk.sh
	$(srcdir)/gen-src-mk.sh $(gentools_dir) $(gentools_srcdir) > $@-t
	mv $@-t $@

endif

maintainer-clean-local:
	rm -rf qpid gen-src.mk $(gentools_srcdir)/*.class
