###############################################################
#
#  This Makefile requires the following:
#
#  Apache FOP, version 0.95 or higher
#  Docbook 4.5
#  Docbook XSL stylesheets - tested with xsl-stylesheets-1.75.2
#  xsltproc
#  xmllint
# 

DOCBOOKXSL = /usr/share/sgml/docbook/xsl-stylesheets-1.75.2/

out/pdf/qpid-book.pdf: build/qpid-book.fo
	mkdir -p out/pdf
	fop build/qpid-book.fo out/pdf/qpid-book.pdf

build/qpid-book.fo: build/qpid-book.xml
	xsltproc ${DOCBOOKXSL}/fo/docbook.xsl build/qpid-book.xml >build/qpid-book.fo

build/qpid-book.xml: 
	mkdir -p build
	xmllint --xinclude src/Book.xml >build/qpid-book.xml

out/html/index.html: build/qpid-book.xml
	mkdir -p out/html
	mkdir -p out/html/Common_Content
	cd out/html; ln -s ../../src/images
	cd  out/html/Common_Content; ln -s ../../../src/images
	cd out/html; xsltproc ${DOCBOOKXSL}/html/chunk.xsl ../../build/qpid-book.xml

pdf: out/pdf/qpid-book.pdf

html:  out/html/index.html

all: pdf html

clean:
	rm -rf build
	rm -rf out

  