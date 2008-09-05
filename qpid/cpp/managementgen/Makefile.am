qmfdatadir = $(datadir)/qmf
qmfpythondir = $(pythondir)
dist_bin_SCRIPTS = \
	qmf-gen
nobase_qmfdata_DATA = \
	templates/Args.h \
	templates/Class.cpp \
	templates/Class.h \
	templates/Makefile.mk \
	templates/Package.cpp \
	templates/Package.h \
	management-types.xml
nobase_qmfpython_DATA = \
	qmf/__init__.py \
	qmf/generate.py \
	qmf/schema.py

EXTRA_DIST = $(nobase_qmfdata_DATA) $(nobase_qmfpython_DATA)
