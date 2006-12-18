
html: doxygen.tstamp

dist-hook: html

nobase_dist_doc_DATA=$(wildcard html/*)

SOURCES = \
  $(wildcard $(topsrcdir)/gen/*.h) \
  $(wildcard $(topsrcdir)/lib/common/*.h) \
  $(wildcard $(topsrcdir)/lib/common/sys/*.h) \
  $(wildcard $(topsrcdir)/lib/common/framing/*.h) \
  $(wildcard $(topsrcdir)/lib/client/*.h) 

doxygen.tstamp: user.doxygen $(SOURCES)
	doxygen user.doxygen
	touch $@

clean-local:
	rm -rf docs.tstamp html man latex doxygen.tstamp xml


