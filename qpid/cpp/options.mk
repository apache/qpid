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

# Directories
SPEC_DIR = ${QPID_HOME}/specs
QPID_CPP_HOME = ${QPID_HOME}/cpp
COMMON_HOME = ${QPID_CPP_HOME}/common
TOOLS_DIR = ${QPID_CPP_HOME}/tools
LIB_DIR = ${QPID_CPP_HOME}/lib
BIN_DIR = ${QPID_CPP_HOME}/bin
APR_HOME = /usr/local/apr

# Compile flags
DEBUG = -ggdb3 -O0
OPTIMIZE =
# _USE_APR_IO_ set when APR IO build is desired.
DEFINES   = -D _USE_APR_IO_
APR_INCLUDES=-I ${APR_HOME}/include/apr-1/ 
COMMON_INCLUDES = -I ${COMMON_HOME}/framing/inc -I ${COMMON_HOME}/framing/generated -I ${COMMON_HOME}/concurrent/inc -I ${COMMON_HOME}/io/inc -I ${COMMON_HOME}/error/inc -I $(COMMON_HOME)/utils/inc ${APR_INCLUDES}
SRC_INCLUDES = $(COMMON_INCLUDES) -I inc 
TEST_INCLUDES = $(COMMON_INCLUDES) -I ../inc -I $(QPID_CPP_HOME)/test/include
INCLUDES=$(SRC_INCLUDES)	# Default to src

# Warnings: Enable as many as possible, keep the code clean. Please
# do not disable warnings or remove -Werror without discussing on
# qpid-dev list.
# 
# The following warnings deliberately omitted, they warn on valid code.
# -Wno-unreachable-code -Wpadded
# 
WARN  = -Werror -pedantic -Wall -Wextra -Wshadow -Wpointer-arith -Wcast-qual -Wcast-align -Wno-long-long -Wvolatile-register-var -Winvalid-pch -Winline 

CXXFLAGS = $(DEBUG) $(OPTIMIZE) $(DEFINES) $(WARN) -MMD -fpic $(INCLUDES) 

# General link flags
LDFLAGS= -L $(LIB_DIR) -L ${APR_HOME}/lib $(RPATH)

# TODO aconway 2006-09-12: This is not something we want in a release
# but it's useful for development.
RPATH= -Wl,-rpath,$(CURDIR)/$(LIB_DIR)

# Libraries and executables. Use absolute paths so exes can find
# libs wherever they are run. TODO: Proper library management.
BROKER=$(BIN_DIR)/qpidd
BROKER_LIB=$(CURDIR)/$(LIB_DIR)/libqpid_broker.so.1.0
COMMON_LIB=$(CURDIR)/$(LIB_DIR)/libqpid_common.so.1.0
CLIENT_LIB=$(CURDIR)/$(LIB_DIR)/libqpid_client.so.1.0

