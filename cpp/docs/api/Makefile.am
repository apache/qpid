
html: doxygen.tstamp

dist-hook: html

EXTRA_DIST=html

SOURCES = \
  $(wildcard $(top_srcdir)/gen/*.h) \
  $(wildcard $(top_srcdir)/lib/common/*.h) \
  $(wildcard $(top_srcdir)/lib/common/sys/*.h) \
  $(wildcard $(top_srcdir)/lib/common/framing/*.h) \
  $(wildcard $(top_srcdir)/lib/client/*.h) 

doxygen.tstamp: user.doxygen $(SOURCES)
	doxygen user.doxygen
	touch $@

clean-local:
	rm -rf docs.tstamp html man latex doxygen.tstamp xml


