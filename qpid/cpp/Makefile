#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# ----------------------------------------------------------------
#
# Makefile for Qpid C++ project.
# 
# Build system principles
#  * Single Makefile (see http://www.apache.org/licenses/LICENSE-2.0)
#  * Build from directories, no explicit source lists in Makefile.
#  * Corresponding .cpp and .h files in same directory for easy editing.
#  * Source directory structure mirrors C++ namespaces.
#
# Source directories:
#  * src/ -  .h and .cpp source files, directories mirror namespaces.
#  * test/ 
#   * unit/ - unit tests (cppunit plugins), directories mirror namespaces.
#   * include/ - .h files used by tests
#   * client/ - sources for client test executables.
#  * etc/ - Non-c++ resources, e.g. stylesheets.
#  * gen/ - generated code
#
# Output directories: 
#  * gen/ - (created by make) generated code 
#  * bin/ lib/ - exes & libraries.
#
# NOTE: always use := rather than = unless you have a specific need
# for delayed evaluation. See the link for details.
#

include options.mk

.PHONY: test all unittest pythontest

test: all unittest pythontest

## Generaged code

SPEC        := $(CURDIR)/../specs/amqp-8.0.xml
XSL         := code_gen.xsl framing.xsl
STYLESHEETS := $(XSL:%=$(CURDIR)/etc/stylesheets/%)
TRANSFORM   := java -jar $(CURDIR)/tools/saxon8.jar -o results.out $(SPEC)

# Restart make if code is re-generated to get proper dependencies.
# Remove "clean" to avoid infinite loop.
REMAKE := exec $(MAKE) $(MAKECMDGOALS:clean=)
gen/timestamp: $(wildcard etc/stylesheets/*.xsl) $(SPEC)
	mkdir -p gen/qpid/framing
	echo > gen/timestamp
	cd gen/qpid/framing && for s in $(STYLESHEETS) ; do $(TRANSFORM) $$s ;	done
	@echo "*** Re-generated code, re-starting make ***"
	$(REMAKE)

gen $(wildcard gen/qpid/framing/*.cpp): gen/timestamp

## Libraries

# Library command, late evaluated for $@
LIB_CMD = $(CXX) -shared -o $@ $(LDFLAGS) $(CXXFLAGS) -lapr-1 

# Common library.
COMMON_LIB  := lib/libqpid_common.so.1.0
COMMON_DIRS := qpid/concurrent qpid/framing qpid/io qpid
COMMON_SRC  := $(wildcard gen/qpid/framing/*.cpp $(COMMON_DIRS:%=src/%/*.cpp))
$(COMMON_LIB): gen/timestamp $(COMMON_SRC:.cpp=.o)
	$(LIB_CMD) $(COMMON_SRC:.cpp=.o)
all: $(COMMON_LIB)
UNITTESTS := $(UNITTESTS) $(wildcard $(COMMON_DIRS:%=test/unit/%/*Test.cpp))

# Client library.
CLIENT_LIB  := lib/libqpid_client.so.1.0
CLIENT_SRC  := $(wildcard src/qpid/client/*.cpp)
$(CLIENT_LIB): $(CLIENT_SRC:.cpp=.o) $(CURDIR)/$(COMMON_LIB)
	$(LIB_CMD) $^
all: $(CLIENT_LIB) 
UNITTESTS := $(UNITTESTS) $(wildcard $(COMMON_DIRS:%=test/unit/%/*Test.cpp))

# Broker library.
BROKER_LIB  := lib/libqpid_broker.so.1.0
BROKER_SRC  := $(wildcard src/qpid/broker/*.cpp)
$(BROKER_LIB): $(BROKER_SRC:.cpp=.o)  $(CURDIR)/$(COMMON_LIB)
	$(LIB_CMD) $^
all: $(BROKER_LIB)
UNITTESTS := $(UNITTESTS) $(wildcard test/unit/qpid/broker/*Test.cpp)

# Implicit rule for unit test plugin libraries.
%Test.so: %Test.cpp 
	$(CXX) -shared -o $@ $< $($(LIB)_FLAGS) -Itest/include $(CXXFLAGS) $(LDFLAGS) -lapr-1 -lcppunit $(CURDIR)/$(COMMON_LIB) $(CURDIR)/$(BROKER_LIB)

## Client tests

all: $(wildcard test/client/*.cpp:.cpp=)
test/client/%: test/client/%.cpp
	$(CXX) -o $@ $< $($(LIB)_FLAGS) -Itest/include $(CXXFLAGS) $(LDFLAGS) -lapr-1 $(LINK_WITH_$(LIB)) $(LINK_WITH_$(LIB)_DEPS)

## Daemon executable

bin/qpidd: src/qpidd.o $(CURDIR)/$(COMMON_LIB) $(CURDIR)/$(BROKER_LIB)
	$(CXX) -o $@ $(LDFLAGS) -lapr-1 $^ 
all: bin/qpidd

## Run unit tests.
unittest: $(UNITTESTS:.cpp=.so)
	DllPlugInTester -c -b $(UNITTESTS:.cpp=.so)

## Run python tests
pythontest: bin/qpidd
	bin/qpidd > qpidd.log &
	cd ../python ; ./run-tests -v -I cpp_failing.txt	

## Doxygen documentation.
doxygen: doxygen/doxygen.cfg $(SOURCES)
	cd doxygen && doxygen doxygen.cfg

## Cleanup
clean::
	rm -f bin/* lib/* qpidd.log
	rm -rf gen
	rm -f `find src test -name '*.o' -o -name '*.d' -o -name '*.so'` 


