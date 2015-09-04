#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# This module checks to see if size_t is a distinct type from the other
# integer types already set up in IntegerTypes.h.

INCLUDE (CheckCXXSourceCompiles)

FUNCTION (check_size_t_distinct VARIABLE)
  # No need to check if we already did. If you want to re-run, clear it
  # from the cache.
  if (NOT DEFINED ${VARIABLE})
    message (STATUS "Check for size_t")
    set (CMAKE_REQUIRED_QUIET ON)
    set (CMAKE_REQUIRED_INCLUDES "${CMAKE_SOURCE_DIR}/include")
    CHECK_CXX_SOURCE_COMPILES (
"
#include \"qpid/sys/IntegerTypes.h\"
// Define functions that will fail to compile if size_t is the same as
// one of the int types defined in IntegerTypes.h
int foo(int16_t)    { return 1; }
int foo(int32_t)    { return 2; }
int foo(int64_t)    { return 3; }
int foo(uint16_t)   { return 4; }
int foo(uint32_t)   { return 5; }
int foo(uint64_t)   { return 6; }
int foo(size_t)     { return 7; }
int main (int, char *[]) {
  return 0;
}
"
    ${VARIABLE})
    if (${VARIABLE})
      message (STATUS "Check for size_t -- NOT a distinct type")
    else (${VARIABLE})
      message (STATUS "Check for size_t -- distinct type")
    endif (${VARIABLE})
  endif ()
ENDFUNCTION (check_size_t_distinct VARIABLE)
