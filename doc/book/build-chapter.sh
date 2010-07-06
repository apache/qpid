#!/bin/bash -ex

########################################################################
#
#  Build a PDF and HTML for a single chapter or section
#
#  Specify the name of the XML file on the command line, omitting
#  the file extension, e.g.:
#
#  $ ./build-chapter.sh src/High-Level-API
#
########################################################################

rm -rf build
mkdir -p build
mkdir -p pdf


# Create the .html
xsltproc --stringparam  section.autolabel 1 /usr/share/sgml/docbook/xsl-stylesheets/html/docbook.xsl src/$1.xml >build/$1.html

# Create the .fo
xsltproc --stringparam  section.autolabel 1 /usr/share/sgml/docbook/xsl-stylesheets/fo/docbook.xsl src/$1.xml >build/$1.fo

# Use Apache FOP to create the PDF
fop build/$1.fo pdf/$1.pdf
