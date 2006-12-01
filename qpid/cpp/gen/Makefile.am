# TODO aconway 2006-11-30: nasty hack, should be done by automake?
abs_srcdir = @abs_srcdir@

include gen-src.mk

BUILT_SOURCES = $(generated_sources)

# Distribute the generated sources, at least for now, since
# the generator code is in java.
EXTRA_DIST = $(BUILT_SOURCES)
MAINTAINERCLEANFILES = $(BUILT_SOURCES)

gentools_dir = $(abs_srcdir)/../../gentools
spec_dir = $(abs_srcdir)/../../specs
spec = $(spec_dir)/amqp-8.0.xml

# FIXME: add dependencies?
timestamp: $(spec)
	if test -d $(gentools_dir); then			\
	  rm -f $(generated_sources);				\
	  (cd $(gentools_dir)/src/org/apache/qpid/gentools &&	\
	   rm -f *.class && javac *.java);			\
	  java -cp $(gentools_dir)/src org.apache.qpid.gentools.Main \
	    -c -o . -t $(gentools_dir)/templ.cpp $(spec);	\
	else							\
	  echo "warning: failed to regenerate gen/*.{cpp,h}" 1>&2;	\
	fi
	touch timestamp

EXTRA_DIST += timestamp
$(generated_sources): timestamp

EXTRA_DIST += gen-src.mk
gen-src.mk: timestamp
	( echo 'generated_sources = \'					\
	  && ls *.cpp *.h | sort -u | sed 's/.*/  & \\/;$$s/ \\//'	\
	) > $@-t
	mv $@-t $@
