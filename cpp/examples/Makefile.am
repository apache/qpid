# Note: we don't use normal automake SUBDIRS because the example
# makefiles are don't understand all automake targets.

EXAMPLE_DIRS=direct fanout pub-sub request-response
src=../$(top_srcdir)/src
bld=../$(top_builddir)/src
EXAMPLE_FLAGS=-I$(src) -I$(src)/gen -I$(bld)/gen -L$(bld)/.libs

all:
	for d in $(EXAMPLE_DIRS); do ( cd $$d; make CXX="$(CXX)" CXXFLAGS="$(EXAMPLE_FLAGS)" $@; ) ; done

clean:
	for d in $(EXAMPLE_DIRS); do ( cd $$d; make $@; ) ; done

dist-hook: clean
	cp --parents `find $(EXAMPLE_DIRS) -name Makefile -o -name '*.cpp'` $(distdir)


