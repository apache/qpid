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

#
#  This Makefile requires the following:
#
#  Apache FOP, version 0.95 or higher
#  Docbook 4.5
#  Docbook XSL stylesheets - tested with xsl-stylesheets (1.75.2 and 1.65.1-2)
#  xsltproc
#  xmllint
# 

all: cpp java programming

cpp: build/AMQP-Messaging-Broker-CPP-Book

java: build/AMQP-Messaging-Broker-Java-Book

programming: build/Programming-In-Apache-Qpid

qmf: build/QmfBook

build/AMQP-Messaging-Broker-CPP-Book:
	./build-book.sh AMQP-Messaging-Broker-CPP-Book

build/AMQP-Messaging-Broker-Java-Book:
	./build-book.sh AMQP-Messaging-Broker-Java-Book

build/Programming-In-Apache-Qpid:
	./build-book.sh Programming-In-Apache-Qpid

build/QmfBook: src/QmfBook.xml src/QmfIntroduction.xml
	./build-book.sh QmfBook

clean:
	rm -rf build
