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

TESTAGENT_GEN_SRC=									\
	testagent_gen/qmf/org/apache/qpid/agent/example/Parent.h			\
	testagent_gen/qmf/org/apache/qpid/agent/example/Child.h				\
	testagent_gen/qmf/org/apache/qpid/agent/example/Parent.cpp			\
	testagent_gen/qmf/org/apache/qpid/agent/example/Child.cpp			\
	testagent_gen/qmf/org/apache/qpid/agent/example/ArgsParentCreate_child.h	\
	testagent_gen/qmf/org/apache/qpid/agent/example/EventChildCreated.h		\
	testagent_gen/qmf/org/apache/qpid/agent/example/EventChildDestroyed.h		\
	testagent_gen/qmf/org/apache/qpid/agent/example/EventChildCreated.cpp		\
	testagent_gen/qmf/org/apache/qpid/agent/example/EventChildDestroyed.cpp		\
	testagent_gen/qmf/org/apache/qpid/agent/example/Package.h			\
	testagent_gen/qmf/org/apache/qpid/agent/example/Package.cpp

$(TESTAGENT_GEN_SRC): testagent_gen.timestamp
if GENERATE
TESTAGENT_DEPS=../mgen.timestamp
endif # GENERATE
testagent_gen.timestamp: testagent.xml ${TESTAGENT_DEPS}
	$(QMF_GEN) -o testagent_gen/qmf $(srcdir)/testagent.xml
	touch $@

CLEANFILES+=$(TESTAGENT_GEN_SRC) testagent_gen.timestamp

testagent-testagent.$(OBJEXT): $(TESTAGENT_GEN_SRC)
qpidexectest_PROGRAMS+=testagent
testagent_CXXFLAGS=$(CXXFLAGS) -Itestagent_gen
testagent_SOURCES=testagent.cpp $(TESTAGENT_GEN_SRC)
testagent_LDADD=$(top_builddir)/src/libqmf.la -lqpidcommon -lqpidtypes -lqpidclient

EXTRA_DIST+=testagent.xml
