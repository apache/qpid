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

# Build a simple qmf agent for test purposes.

QMF_GEN=$(top_srcdir)/managementgen/qmf-gen
GEN_SRC= \
	gen/qmf/org/apache/qpid/agent/example/Parent.h			\
	gen/qmf/org/apache/qpid/agent/example/Child.h			\
	gen/qmf/org/apache/qpid/agent/example/Parent.cpp		\
	gen/qmf/org/apache/qpid/agent/example/Child.cpp			\
	gen/qmf/org/apache/qpid/agent/example/ArgsParentCreate_child.h	\
	gen/qmf/org/apache/qpid/agent/example/EventChildCreated.h	\
	gen/qmf/org/apache/qpid/agent/example/EventChildDestroyed.h	\
	gen/qmf/org/apache/qpid/agent/example/EventChildCreated.cpp	\
	gen/qmf/org/apache/qpid/agent/example/EventChildDestroyed.cpp	\
	gen/qmf/org/apache/qpid/agent/example/Package.h			\
	gen/qmf/org/apache/qpid/agent/example/Package.cpp

$(GEN_SRC): gen.timestamp

gen.timestamp: schema.xml
	$(QMF_GEN) -o gen/qmf $(srcdir)/schema.xml
	touch gen.timestamp

CLEANFILES=$(GEN_SRC)

INCLUDES = -I$(top_srcdir)/include -I$(top_builddir)/include -I$(top_srcdir)/src -I$(top_builddir)/src -Igen

noinst_PROGRAMS=testagent
testagent_SOURCES=testagent.cpp $(GEN_SRC)
testagent_LDADD=$(top_builddir)/src/libqmf.la

