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
# Build broker library and executable.
#

QPID_HOME = ../..
include ${QPID_HOME}/cpp/options.mk

SOURCES= $(wildcard src/*.cpp)
OBJECTS= $(subst .cpp,.o,$(SOURCES))
LIB_OBJECTS= $(subst src/Broker.o,,$(OBJECTS))
EXE_OBJECTS= src/Broker.o


.PHONY: all clean 

all: $(BROKER)
	@$(MAKE) -C test all

clean:
	-@rm -f ${OBJECTS} src/*.d ${BROKER} $(BROKER_LIB)
	@$(MAKE) -C test clean

$(BROKER): $(BROKER_LIB) $(EXE_OBJECTS)
	${CXX} -o $@ $(EXE_OBJECTS) $(LDFLAGS) -lapr-1 $(COMMON_LIB) $(BROKER_LIB)

$(BROKER_LIB): $(LIB_OBJECTS)
	$(CXX) -shared -o $@ $(LDFLAGS) $(LIB_OBJECTS)  -lapr-1 $(COMMON_LIB) $(LIBDIR)

-include $(SOURCES:.cpp=.d)
