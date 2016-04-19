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

BOOK=$(wildcard *Book.xml Programming-In-Apache-Qpid.xml )
XML=$(wildcard *.xml) $(wildcard ../common/*.xml)
IMAGES=$(wildcard images/*.png) 
CSS=$(wilcard ../common/css/*.css)

OUTPUTDIR=output
OUTPUT= $(BOOK:%.xml=$(OUTPUTDIR)/%/)

all: html pdf 

pdf: $(OUTPUT)/pdf $(BOOK:%.xml=$(OUTPUT)/pdf/%.pdf) 

html: $(OUTPUT)/html $(BOOK:%.xml=$(OUTPUT)/html/index.html) 

$(OUTPUT)/html/images: $(IMAGES)
	-mkdir -p $(OUTPUT)/html/images
	-cp images/*.png $(OUTPUT)/html/images/

$(OUTPUT)/html/css: $(CSS)
	-mkdir -p $(OUTPUT)/html/css
	-cp ../common/css/*.css $(OUTPUT)/html/css

$(OUTPUT)/html:
	-mkdir -p $(OUTPUT)/html

$(OUTPUT)/pdf:
	-mkdir -p $(OUTPUT)/pdf

$(OUTPUT)/html/index.html: $(BOOK) $(OUTPUT)/html/css $(OUTPUT)/html/images $(XML)
	xsltproc -o $(OUTPUT)/html/ --xinclude --stringparam  chunk.section.depth 1  --stringparam  section.autolabel 1  --stringparam  callout.graphics 0  --stringparam  callout.unicode 0 --stringparam section.label.includes.component.label 1 --stringparam use.id.as.filename 1 --stringparam html.stylesheet css/style.css --stringparam section.autolabel.max.depth 3 --stringparam toc.section.depth 2 --stringparam chunker.output.encoding UTF-8 --stringparam css.decoration 0 ../../xsl/html-custom.xsl $<

%.fo: %.xml
	xsltproc --xinclude --stringparam  section.autolabel 1  --stringparam  callout.graphics 0  --stringparam  callout.unicode 0 --stringparam section.label.includes.component.label 1 http://docbook.sourceforge.net/release/xsl/current/fo/docbook.xsl $< > $@
 

%.pdf: %.fo
	fop $< $@

$(OUTPUT)/pdf/%.pdf: %.pdf
	@mv $< $@

clean:
	-rm -rf $(OUTPUT) *.fo

