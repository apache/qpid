# This nmake file generates the Qpid protocol files from the AMQP XML spec
# and the management sources from the management XML specs.
#
# The Visual Studio projects assume the existence of the generated files.
# The generated files are valid in Apache-released kits but must be generated
# using this makefile for Windows developers working from the source
# repository.

specdir = ..\..\specs
specs = $(specdir)\amqp.0-10-qpid-errata.xml ..\xml\cluster.xml

rgen_dir=..\rubygen

mgen_dir=..\managementgen
mgmt_specs=$(specdir)\management-schema.xml \
           .\qpid\acl\management-schema.xml \
           .\qpid\cluster\management-schema.xml

all: rubygen.mk managementgen.mk

rubygen.mk: $(specs)
  ruby -I $(rgen_dir) $(rgen_dir)\generate gen $(specs) all rubygen.mk

# Management code generation... uses Python

managementgen.mk:  $(mgmt_specs)
  python $(mgen_dir)\qmf-gen -m managementgen.mk -o gen\qmf $(mgmt_specs)

