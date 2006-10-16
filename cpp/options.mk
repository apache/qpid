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


## External dependencies:

# Add location for headers and libraries of any external dependencies here
EXTRA_INCLUDES := -I/usr/local/apr/include/apr-1
EXTRA_LIBDIRS := -L/usr/local/apr/lib

## Compile flags

DEBUG := -ggdb3

# _USE_APR_IO_ set when APR IO build is desired.
DEFINES := -D _USE_APR_IO_

# Warnings: Enable as many as possible, keep the code clean. Please
# do not disable warnings or remove -Werror without discussing on
# qpid-dev list.
# 
# The following warnings deliberately omitted, they warn on valid code.
# -Wno-unreachable-code -Wpadded
# 
WARN := -Werror -pedantic -Wall -Wextra -Wshadow -Wpointer-arith -Wcast-qual -Wcast-align -Wno-long-long -Wvolatile-register-var -Winvalid-pch -Winline 
INCLUDES :=  -Isrc -Igen $(EXTRA_INCLUDES) 
CXXFLAGS := $(DEBUG) $(DEFINES) $(WARN) -MMD -fpic $(INCLUDES)
## Link flags
# Allow exes to find libs without env changes. Remove for release builds.
LDFLAGS := -Llib $(EXTRA_LIBDIRS) 
