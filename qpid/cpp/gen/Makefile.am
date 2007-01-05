include gen-src.mk

BUILT_SOURCES = $(generated_sources) $(generated_headers)
pkginclude_HEADERS=$(generated_headers)

# Distribute the generated sources, at least for now, since
# the generator code is in java.
EXTRA_DIST = $(BUILT_SOURCES)
MAINTAINERCLEANFILES = $(BUILT_SOURCES)

# Don't attempt to run the code generator unless configure has set
# CAN_GENERATE_CODE, indicating that the amqp.xml and tools needed
# to run the code generator are available.
#
if CAN_GENERATE_CODE

gentools_dir = $(srcdir)/../../gentools
spec_dir = $(srcdir)/../../specs
spec = $(spec_dir)/amqp.0-8.xml
gentools_srcdir = $(gentools_dir)/src/org/apache/qpid/gentools

$(BUILT_SOURCES) timestamp: $(spec) $(java_sources) $(cxx_templates)
	rm -f $(generated_sources)
	cd $(gentools_srcdir) && rm -f *.class && $(JAVAC) *.java
	$(JAVA) -cp $(gentools_dir)/src org.apache.qpid.gentools.Main \
	        -c -o . -t $(gentools_dir)/templ.cpp $(spec)
	touch timestamp

DISTCLEANFILES = gen-src.mk
gen-src.mk: timestamp
	( echo 'generated_sources = '\\				\
	  && ls *.cpp | sort -u | sed 's/.*/  & \\/;$$s/ \\//';	\
	  echo 'generated_headers = '\\				\
	  && ls *.h | sort -u | sed 's/.*/  & \\/;$$s/ \\//';	\
	) > $@-t
	( echo if CAN_GENERATE_CODE;					\
	  echo 'java_sources = '\\					\
	    && find $(gentools_srcdir) -name '*.java'			\
		| sort -u | sed 's/.*/  & \\/;$$s/ \\//';		\
	  echo 'cxx_templates = '\\					\
	    && find $(gentools_dir)/templ.cpp -name '*.tmpl'		\
		| sort -u | sed 's/.*/  & \\/;$$s/ \\//';		\
	  echo '# Empty rules, in case any of these files is removed,';	\
	  echo '# renamed, or no longer generated.';			\
	  echo '$$(spec):';						\
	  echo '$$(java_sources):';					\
	  echo '$$(cxx_templates):';					\
	  echo endif							\
	) >> $@-t
	mv $@-t $@
endif
