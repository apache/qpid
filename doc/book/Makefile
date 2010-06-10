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

all: cpp java programming

cpp: build/AMQP-Messaging-Broker-CPP-Book

java: build/AMQP-Messaging-Broker-Java-Book

programming: build/Programming-In-Apache-Qpid

build/AMQP-Messaging-Broker-CPP-Book:
	./build-book.sh AMQP-Messaging-Broker-CPP-Book

build/AMQP-Messaging-Broker-Java-Book:
	./build-book.sh AMQP-Messaging-Broker-Java-Book

build/Programming-In-Apache-Qpid:
	./build-book.sh Programming-In-Apache-Qpid

clean:
	rm -rf build