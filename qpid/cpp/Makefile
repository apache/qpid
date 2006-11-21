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
# See README for details.
#

include options.mk

.PHONY: test all all-nogen generate unittest pythontest doxygen

test: unittest pythontest

.PHONY: show-vars
show-vars:
	@echo APR_LDFLAGS=$(APR_LDFLAGS)
	@echo APR_CFLAGS=$(APR_CFLAGS)
	@echo CXXFLAGS=$(CXXFLAGS)
	@echo LDFLAGS=$(LDFLAGS)
	@echo CXX=$(CXX)

# Must run two separate make processes to pick up generated files.
all:
	$(MAKE) generate
	$(MAKE) all-nogen

## Generaged code

SPEC        := $(CURDIR)/../specs/amqp-8.0.xml
XSL         := code_gen.xsl framing.xsl
STYLESHEETS := $(XSL:%=$(CURDIR)/etc/stylesheets/%)
TRANSFORM   := java -jar $(CURDIR)/tools/saxon8.jar -o results.out $(SPEC)
generate: $(GENDIR)/timestamp
$(GENDIR)/timestamp: $(wildcard etc/stylesheets/*.xsl) $(SPEC)
	rm -rf $(GENDIR)
	mkdir -p $(GENDIR)/qpid/framing
	( cd $(GENDIR)/qpid/framing && for s in $(STYLESHEETS) ; do $(TRANSFORM) $$s ; done ) && echo > $(GENDIR)/timestamp

# Dependencies for existing generated files.
GENFILES:=$(wildcard $(GENDIR)/qpid/*/*.cpp $(GENDIR)/qpid/*/*.h)
ifdef GENFILES
$(GENFILES): $(GENDIR)/timestamp
endif

$(BUILDDIRS):
	mkdir -p $(BUILDDIRS)

## Library rules

LIB_common  := $(call LIBFILE,common,1.0)
DIRS_common := qpid qpid/framing qpid/sys qpid/$(PLATFORM)
$(LIB_common): $(call OBJECTS, $(DIRS_common))
	$(LIB_COMMAND)

LIB_client  := $(call LIBFILE,client,1.0)
DIRS_client := qpid/client
$(LIB_client): $(call OBJECTS,$(DIRS_client)) $(LIB_common)
	$(LIB_COMMAND)

LIB_broker  := $(call LIBFILE,broker,1.0)
DIRS_broker := qpid/broker
$(LIB_broker): $(call OBJECTS,$(DIRS_broker)) $(LIB_common)
	$(LIB_COMMAND)

## Daemon executable
$(BINDIR)/qpidd: $(OBJDIR)/qpidd.o $(LIB_common) $(LIB_broker)
	mkdir -p $(dir $@)
	$(CXX) -o $@ $(CXXFLAGS) $(LDFLAGS) $^ 
all-nogen: $(BINDIR)/qpidd

## Unit tests.
UNITTEST_SRC:=$(shell find test/unit -name *Test.cpp)
UNITTEST_SRC:=$(filter-out test/unit/qpid/$(IGNORE)/%,$(UNITTEST_SRC))
UNITTESTS:=$(UNITTEST_SRC:test/unit/%.cpp=$(TESTDIR)/%.so)

unittest: all 
	DllPlugInTester -c -b $(UNITTESTS:.cpp=.so)
all-nogen: $(UNITTESTS)

## Run python tests
pythontest: all
	$(BINDIR)/qpidd > qpidd.log 2>&1 &
	cd ../python ; ./run-tests -v -I cpp_failing.txt	

## Doxygen documentation.
doxygen: generate build/html
build/html: doxygen.cfg 
	doxygen doxygen.cfg
	touch $@

## Implicit rules

# C++ compile
define CPPRULE
$(OBJDIR)/%.o: $1/%.cpp
	@mkdir -p $$(dir $$@)
	$(CXX) $(CXXFLAGS) -c -o $$@ $$< 
endef

$(foreach dir,$(SRCDIRS),$(eval $(call CPPRULE,$(dir))))

ifndef CPPUNIT_LDFLAGS
  CPPUNIT_LDFLAGS := -lcppunit
endif

#  Unit test plugin libraries.
$(TESTDIR)/%Test.so: test/unit/%Test.cpp $(LIB_common) $(LIB_broker)
	mkdir -p $(dir $@)
	$(CXX) -shared -o $@ $< $(CXXFLAGS)  -Itest/include $(LDFLAGS) $(CPPUNIT_LDFLAGS) $(LIB_common) $(LIB_broker)

# Client test programs
$(TESTDIR)/%: test/client/%.cpp $(LIB_common) $(LIB_client)
	mkdir -p $(dir $@)
	$(CXX) -o $@ $(CXXFLAGS) -Itest/include $(LDFLAGS) $^
CLIENT_TEST_SRC := $(wildcard test/client/*.cpp)
CLIENT_TEST_EXE := $(CLIENT_TEST_SRC:test/client/%.cpp=$(TESTDIR)/%)
all-nogen: $(CLIENT_TEST_EXE)
client: $(CLIENT_TEST_EXE)

## include dependencies
DEPFILES:=$(wildcard $(OBJDIR)/*.d $(OBJDIR)/*/*.d $(OBJDIR)/*/*/*.d)
ifdef DEPFILES
-include $(DEPFILES)
endif

## Clean up

# Just the current build.
clean:
	rm -rf build/$(BUILD)

# Clean all builds
spotless:
	rm -rf build

