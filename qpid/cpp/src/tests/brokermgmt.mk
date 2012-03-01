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

# Build a unit test for the broker's internal management agent.

BROKERMGMT_GEN_SRC=									\
	brokermgmt_gen/qmf/org/apache/qpid/broker/mgmt/test/Package.cpp			\
	brokermgmt_gen/qmf/org/apache/qpid/broker/mgmt/test/Package.h			\
	brokermgmt_gen/qmf/org/apache/qpid/broker/mgmt/test/TestObject.h		\
	brokermgmt_gen/qmf/org/apache/qpid/broker/mgmt/test/TestObject.cpp

$(BROKERMGMT_GEN_SRC): brokermgmt_gen.timestamp

if GENERATE
BROKERMGMT_DEPS=../mgen.timestamp
endif # GENERATE
brokermgmt_gen.timestamp: BrokerMgmtAgent.xml ${BROKERMGMT_DEPS}
	$(QMF_GEN) -b -o brokermgmt_gen/qmf $(srcdir)/BrokerMgmtAgent.xml
	touch $@

BrokerMgmtAgent.$(OBJEXT): $(BROKERMGMT_GEN_SRC)

CLEANFILES+=$(BROKERMGMT_GEN_SRC) brokermgmt_gen.timestamp

unit_test_SOURCES+=BrokerMgmtAgent.cpp ${BROKERMGMT_GEN_SRC}
INCLUDES+= -Ibrokermgmt_gen

EXTRA_DIST+=BrokerMgmtAgent.xml
