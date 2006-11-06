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

## Build platform and type defaults
TYPE := debug
PLATFORM := apr

# Local options, may override PLATFORM and/or TYPE
-include options-local.mk

## Platform specific options

# apr: Apache Portable Runtime.
CXXFLAGS_apr := -I/usr/local/apr/include
LDFLAGS_apr  := -L/usr/local/apr/lib -lapr-1 

# posix: Native posix implementation
CXXFLAGS_posix :=
LDFLAGS_posix  :=

## Build directories.

BUILD=$(PLATFORM)-$(TYPE)
GENDIR:=build/gen
BINDIR:=build/$(BUILD)/bin
LIBDIR:=build/$(BUILD)/lib
OBJDIR:=build/$(BUILD)/obj
TESTDIR:=build/$(BUILD)/test

BUILDDIRS := $(BINDIR) $(LIBDIR) $(OBJDIR) $(TESTDIR) $(GENDIR)
SRCDIRS := src $(GENDIR)

## External dependencies:

# Add location for headers and libraries of any external dependencies here
EXTRA_INCLUDES :=
EXTRA_LIBDIRS  :=

## Compile flags

# Release vs. debug flags.
CXXFLAGS_debug   := -ggdb3
CXXFLAGS_release := -O3 -DNDEBUG

# Warnings: Enable as many as possible, keep the code clean. Please
# do not disable warnings or remove -Werror without discussing on
# qpid-dev list.
# 
# The following warnings deliberately omitted, they warn on valid code.
# -Wno-unreachable-code -Wpadded -Winline
# 
WARN := -Werror -pedantic -Wall -Wextra -Wshadow -Wpointer-arith -Wcast-qual -Wcast-align -Wno-long-long -Wvolatile-register-var -Winvalid-pch

INCLUDES :=  $(SRCDIRS:%=-I%) $(EXTRA_INCLUDES)
LDFLAGS := -L$(LIBDIR) $(LDFLAGS_$(PLATFORM))
CXXFLAGS :=  $(DEFINES) $(WARN) -MMD -fpic $(INCLUDES) $(CXXFLAGS_$(PLATFORM)) $(CXXFLAGS_$(TYPE))

## Macros for linking, must be late evaluated

# Collect object files from a collection of src subdirs
# $(call OBJ_FROM,srcdir,subdir)
OBJECTS_1 = $(patsubst $1/$2/%.cpp,$(OBJDIR)/$2/%.o,$(wildcard $1/$2/*.cpp))
OBJECTS = $(foreach src,$(SRCDIRS),$(foreach sub,$1,$(call OBJECTS_1,$(src),$(sub))))

# $(call LIBFILE,name,version)
LIBFILE =$(CURDIR)/$(LIBDIR)/libqpid_$1.so.$2

LIB_COMMAND = 	mkdir -p $(dir $@) && $(CXX) -shared -o $@ $(LDFLAGS) $(CXXFLAGS) $^

