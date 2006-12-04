include gen-src.mk

BUILT_SOURCES = $(generated_sources)

# Distribute the generated sources, at least for now, since
# the generator code is in java.
EXTRA_DIST = $(BUILT_SOURCES)
MAINTAINERCLEANFILES = $(BUILT_SOURCES)

gentools_dir = $(srcdir)/../../gentools
spec_dir = $(srcdir)/../../specs
spec = $(spec_dir)/amqp-8.0.xml
gentools_srcdir = $(gentools_dir)/src/org/apache/qpid/gentools

timestamp: $(spec) $(java_sources) $(cxx_templates)
if BUILD_IN_MESSAGE_TREE
	rm -f $(generated_sources)
	(cd $(gentools_srcdir) && rm -f *.class && $(JAVAC) *.java);	\
	$(JAVA) -cp $(gentools_dir)/src org.apache.qpid.gentools.Main	\
	  -c -o . -t $(gentools_dir)/templ.cpp $(spec)
else
	echo "warning: failed to regenerate gen/*.{cpp,h}" 1>&2
endif
	touch timestamp

EXTRA_DIST += timestamp
$(generated_sources): timestamp

EXTRA_DIST += gen-src.mk
gen-src.mk: timestamp
	( echo 'generated_sources = \'					\
	  && ls *.cpp *.h | sort -u | sed 's/.*/  & \\/;$$s/ \\//'	\
	) > $@-t
if BUILD_IN_MESSAGE_TREE
	( echo 'java_sources = \'					\
	    && find $(gentools_srcdir) -name '*.java'			\
		| sort -u | sed 's/.*/  & \\/;$$s/ \\//'		\
	) >> $@-t
	( echo 'cxx_templates = \'					\
	    && find $(gentools_dir)/templ.cpp -name '*.tmpl'		\
		| sort -u | sed 's/.*/  & \\/;$$s/ \\//'		\
	) >> $@-t
endif
	mv $@-t $@
