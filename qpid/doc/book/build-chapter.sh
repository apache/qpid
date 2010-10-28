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

rm -rf build
mkdir -p build
mkdir -p pdf


# Create the .html
xsltproc --stringparam  section.autolabel 1 /usr/share/sgml/docbook/xsl-stylesheets/html/docbook.xsl src/$1.xml >build/$1.html

# Create the .fo
xsltproc --stringparam  section.autolabel 1 /usr/share/sgml/docbook/xsl-stylesheets/fo/docbook.xsl src/$1.xml >build/$1.fo

# Use Apache FOP to create the PDF
fop build/$1.fo pdf/$1.pdf
