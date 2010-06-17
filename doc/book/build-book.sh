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

rm -rf build/$1
mkdir -p build/$1
mkdir -p build/$1/html-single
mkdir -p build/$1/html
mkdir -p build/$1/pdf
cp -r src/images build/$1/html-single
cp -r src/images build/$1/html

# Create single-page .html
xsltproc --xinclude --stringparam  section.autolabel 1  --stringparam  callout.graphics 0  --stringparam  callout.unicode 0 --stringparam section.label.includes.component.label 1 /usr/share/sgml/docbook/xsl-stylesheets-1.75.2/html/docbook.xsl src/$1.xml >build/$1/html-single/$1.html

# Create chunked .html
INFILE=$(readlink -f src/$1.xml)
pushd build/$1/html
xsltproc --xinclude --stringparam  chunk.section.depth 1  --stringparam  section.autolabel 1  --stringparam  callout.graphics 0  --stringparam  callout.unicode 0 --stringparam section.label.includes.component.label 1 /usr/share/sgml/docbook/xsl-stylesheets-1.75.2/html/chunk.xsl $INFILE
popd

# Create the .fo
xsltproc --xinclude --stringparam  section.autolabel 1  --stringparam  callout.graphics 0  --stringparam  callout.unicode 0 --stringparam section.label.includes.component.label 1 /usr/share/sgml/docbook/xsl-stylesheets-1.75.2/fo/docbook.xsl src/$1.xml >build/$1/pdf/$1.fo

# Use Apache FOP to create the PDF
fop build/$1/pdf/$1.fo build/$1/pdf/$1.pdf
