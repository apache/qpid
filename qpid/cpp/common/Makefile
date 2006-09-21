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
# Make file to build qpid_common library.
# 

QPID_HOME=../..
include $(QPID_HOME)/cpp/options.mk

TARGET = $(LIB_DIR)/libqpid_common.so.1.0

CXXFLAGS = $(DEBUG) $(OPT) -MMD -fpic $(COMMON_INCLUDES)

SOURCES = $(wildcard */src/*.cpp framing/generated/*.cpp)
OBJECTS = $(SOURCES:.cpp=.o)
DEPS = $(SOURCES:.cpp=.d)

GENERATED_OBJECTS = framing/generated/amqp_methods.o

.PHONY: all clean

# We have to do two separate makes to ensure we pick up all generated files.
all:
	@$(MAKE) -C framing all
	@$(MAKE) $(TARGET)
	@$(MAKE) -C framing test

clean:
	@$(MAKE) -C framing clean
	-@rm -f $(TARGET) $(OBJECTS) $(DEPS)

$(TARGET): $(OBJECTS) 
	$(CXX)  -shared -o $@ $(OBJECTS) $(LDFLAGS)   -lapr-1

-include $(DEPS)
