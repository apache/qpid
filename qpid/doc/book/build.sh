#!/bin/bash -ex

########################################################################
#
#  Build a PDF from Docbook XML
#
#  The Makefile is cleaner ....
#
########################################################################

rm -rf build
mkdir -p build
mkdir -p pdf

# Assemble all documents using XInclude
xmllint --xinclude src/Book.xml >build/qpid-book.xml

# Create the .fo
xsltproc /usr/share/sgml/docbook/xsl-stylesheets-1.75.2/fo/docbook.xsl build/qpid-book.xml >build/qpid-book.fo

# Use Apache FOP to create the PDF
fop build/qpid-book.fo pdf/qpid-book.pdf
