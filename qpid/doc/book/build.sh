#!/bin/bash -ex

########################################################################
#
#  Build a PDF from Docbook XML
#
#  This will be replaced by an ANT file soon.
#
########################################################################

rm -rf build
mkdir -p build
mkdir -p pdf

# Assemble all documents using XInclude
xmllint --xinclude src/Book.xml >build/book.xml

# Create the .fo
xsltproc /usr/share/sgml/docbook/xsl-stylesheets-1.75.2/fo/docbook.xsl build/book.xml >build/book.fo

# Use Apache FOP to create the PDF
fop build/book.fo pdf/qpid-book.pdf
