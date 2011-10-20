#!/bin/bash -ex
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

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

# DOCBOOK XSL STYLESHEET LOCATION
# Fedora, RHEL:
DOCBOOK_XSL=/usr/share/sgml/docbook/xsl-stylesheets
# Ubuntu:
# DOCBOOK_XSL=/usr/share/sgml/docbook/stylesheet/xsl/nwalsh

rm -rf build/$1
mkdir -p build/$1
mkdir -p build/$1/html-single
mkdir -p build/$1/html
mkdir -p build/$1/pdf
cp -r src/images build/$1/html-single
cp -r src/images build/$1/html

# Create single-page .html
xsltproc --xinclude --stringparam  section.autolabel 1  --stringparam  callout.graphics 0  --stringparam  callout.unicode 0 --stringparam section.label.includes.component.label 1 ${DOCBOOK_XSL}/html/docbook.xsl src/$1.xml >build/$1/html-single/$1.html

# Create chunked .html
INFILE=$(readlink -f src/$1.xml)
pushd build/$1/html
xsltproc --xinclude --stringparam  chunk.section.depth 1  --stringparam  section.autolabel 1  --stringparam  callout.graphics 0  --stringparam  callout.unicode 0 --stringparam section.label.includes.component.label 1 ${DOCBOOK_XSL}/html/chunk.xsl $INFILE
popd

# Create the .fo
xsltproc --xinclude --stringparam  section.autolabel 1  --stringparam  callout.graphics 0  --stringparam  callout.unicode 0 --stringparam section.label.includes.component.label 1 ${DOCBOOK_XSL}/fo/docbook.xsl src/$1.xml >build/$1/pdf/$1.fo

# Use Apache FOP to create the PDF
fop build/$1/pdf/$1.fo build/$1/pdf/$1.pdf
