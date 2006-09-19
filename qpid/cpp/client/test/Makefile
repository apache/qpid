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
#

QPID_HOME = ../../..
include ${QPID_HOME}/cpp/options.mk

# TODO aconway 2006-09-12: These are system tests, not unit tests.
# We need client side unit tests.
# We should separate them from the system tets.
# We need an approach to automate the C++ client/server system tests.
# 

SOURCES=$(wildcard *.cpp)
TESTS=$(SOURCES:.cpp=)
DEPS= $(SOURCES:.cpp=.d)

INCLUDES = $(TEST_INCLUDES)
LDLIBS= -lapr-1  $(COMMON_LIB) $(CLIENT_LIB)

.PHONY: all clean

all: $(TESTS) 

clean:
	-@rm -f $(TESTS) $(DEPS)

# Rule to build test programs.
%: %.cpp
	$(CXX) -o $@ $< $(CXXFLAGS) $(LDFLAGS) $(LDLIBS)

# Dependencies
-include $(DEPS)
