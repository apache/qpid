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

examplesdir=$(pkgdatadir)/examples/qmf-console

MAKELDFLAG = qmfconsole
include $(top_srcdir)/examples/makedist.mk

noinst_PROGRAMS=console printevents ping

console_SOURCES=console.cpp
console_LDADD=$(CONSOLE_LIB)

printevents_SOURCES=printevents.cpp
printevents_LDADD=$(CONSOLE_LIB)

ping_SOURCES=ping.cpp
ping_LDADD=$(CONSOLE_LIB)

examples_DATA= \
	console.cpp \
	printevents.cpp \
	ping.cpp \
	$(MAKEDIST)

