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

QPID_HOME = ../../../..
include $(QPID_HOME)/cpp/options.mk
INCLUDES=$(TEST_INCLUDES)

##### Targets #####
# Add additional source files to SOURCE LIST to include them in the build.
COMMON_SOURCE_LIST = logger.cpp

COMMON_OBJ_LIST = $(COMMON_SOURCE_LIST:.cpp=.o)
LOGGER_TEST_EXE = logger_test


.PHONY: all clean

all: $(LOGGER_TEST_EXE)

$(LOGGER_TEST_EXE) : $(COMMON_OBJ_LIST) $(LOGGER_TEST_EXE).o
	$(CXX) -o $@ $^ -l apr-1 -L /usr/local/apr/lib/

clean:
	-@rm -f $(LOGGER_TEST_EXE) $(LOGGER_TEST_EXE).o $(COMMON_OBJ_LIST) test_log.txt *~ ../inc/*~
