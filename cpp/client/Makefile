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

#
# Build client library.
# 

QPID_HOME = ../..
include ${QPID_HOME}/cpp/options.mk

SOURCES := $(wildcard src/*.cpp)
OBJECTS := $(subst .cpp,.o,$(SOURCES))
CLIENT_LIB=$(LIB_DIR)/libqpid_client.so.1.0

.PHONY: all clean 

all: $(CLIENT_LIB)
	@$(MAKE) -C test all

clean:
	-@rm -f $(CLIENT_LIB) $(OBJECTS)  src/*.d
	$(MAKE) -C test clean

$(CLIENT_LIB): $(OBJECTS)
	$(CXX) -shared -o $@ $^ $(LDFLAGS)  $(COMMON_LIB) 

# Dependencies
-include $(SOURCES:.cpp=.d)
