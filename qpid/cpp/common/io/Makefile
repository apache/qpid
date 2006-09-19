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

# Compiler flags
CXXFLAGS = ${DEBUG} ${OPT} -MMD -I inc  -I ../concurrent/inc -I ../error/inc -I ../framing/inc -I ../framing/generated -I ${APR_HOME}/include/apr-1/

SOURCES := $(wildcard src/*.cpp)
OBJECTS := $(subst .cpp,.o,$(SOURCES))
DEPS := $(subst .cpp,.d,$(SOURCES))

.PHONY: all clean

all: ${OBJECTS}

-include $(DEPS)

clean :
	-@rm -f ${OBJECTS} src/*.d

