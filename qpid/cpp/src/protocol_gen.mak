# This nmake file generates the Qpid protocol files from the AMQP XML spec
# and the management sources from the management XML specs.
#
# The Visual Studio projects assume the existence of the generated files.
# The generated files are valid in Apache-released kits but must be generated
# using this makefile for Windows developers working from the source
# repository.

specdir = ..\..\specs
# To add cluster support, add ..\xml\cluster.xml to specs, then add sources
# to project files.
specs = $(specdir)\amqp.0-10-qpid-errata.xml

rgen_dir=..\rubygen

mgen_dir=..\managementgen
mgmt_specs=$(specdir)\management-schema.xml .\qpid\acl\management-schema.xml
# To add cluser management, add the next line to mgen_specs:
#	.\qpid\cluster\management-schema.xml

all: rubygen.mk managementgen.mk qpid\framing\MaxMethodBodySize.h

rubygen.mk gen\generate_MaxMethodBodySize_h.cpp: $(specs)
  ruby -I $(rgen_dir) $(rgen_dir)\generate gen $(specs) all rubygen.mk

CPPFLAGS = /Od /I. /Igen /I"$(BOOST_ROOT)" /I"$(BOOST_ROOT)/include" /DWIN32 /D_CONSOLE /D_CRT_NONSTDC_NO_WARNINGS /DNOMINMAX /FD /EHsc /RTC1 /MTd /W3 /Zi /TP

generate_MaxMethodBodySize_h.exe: gen/generate_MaxMethodBodySize_h.cpp
  $(CPP) $(CPPFLAGS) gen/generate_MaxMethodBodySize_h.cpp

qpid\framing\MaxMethodBodySize.h: generate_MaxMethodBodySize_h.exe
  .\generate_MaxMethodBodySize_h

# Management code generation... uses Python

managementgen.mk:  $(mgmt_specs)
  python $(mgen_dir)\qmf-gen -m managementgen.mk -o gen\qmf $(mgmt_specs)

