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
# clock_time.m4 serial 8
dnl Copyright (C) 2002-2006 Free Software Foundation, Inc.
dnl This file is free software; the Free Software Foundation
dnl gives unlimited permission to copy and/or distribute it,
dnl with or without modifications, as long as this notice is preserved.

# Check for clock_gettime and clock_settime, and set LIB_CLOCK_GETTIME.
# For a program named, say foo, you should add a line like the following
# in the corresponding Makefile.am file:
# foo_LDADD = $(LDADD) $(LIB_CLOCK_GETTIME)

AC_DEFUN([gl_CLOCK_TIME],
[
  dnl Persuade glibc and Solaris <time.h> to declare these functions.
  AC_REQUIRE([gl_USE_SYSTEM_EXTENSIONS])

  # Solaris 2.5.1 needs -lposix4 to get the clock_gettime function.
  # Solaris 7 prefers the library name -lrt to the obsolescent name -lposix4.

  # Save and restore LIBS so e.g., -lrt, isn't added to it.  Otherwise, *all*
  # programs in the package would end up linked with that potentially-shared
  # library, inducing unnecessary run-time overhead.
  gl_saved_libs=$LIBS
    AC_SEARCH_LIBS(clock_gettime, [rt posix4],
                   [test "$ac_cv_search_clock_gettime" = "none required" ||
                    LIB_CLOCK_GETTIME=$ac_cv_search_clock_gettime])
    AC_SUBST([LIB_CLOCK_GETTIME])
    AC_CHECK_FUNCS(clock_gettime clock_settime)
  LIBS=$gl_saved_libs
])
