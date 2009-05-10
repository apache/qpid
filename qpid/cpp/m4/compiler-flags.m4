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
# serial 3
# Find valid warning flags for the C Compiler.           -*-Autoconf-*-
dnl Copyright (C) 2001, 2002, 2006 Free Software Foundation, Inc.
dnl This file is free software; the Free Software Foundation
dnl gives unlimited permission to copy and/or distribute it,
dnl with or without modifications, as long as this notice is preserved.
dnl Written by Jesse Thilo.

AC_DEFUN([gl_COMPILER_FLAGS],
  [AC_MSG_CHECKING(whether compiler accepts $1)
   AC_SUBST(COMPILER_FLAGS)
   ac_save_CFLAGS="$CFLAGS"
   CFLAGS="$CFLAGS $1"
   ac_save_CXXFLAGS="$CXXFLAGS"
   CXXFLAGS="$CXXFLAGS $1"
   AC_TRY_COMPILE(,
    [int x;],
    COMPILER_FLAGS="$COMPILER_FLAGS $1"
    AC_MSG_RESULT(yes),
    AC_MSG_RESULT(no))
  CFLAGS="$ac_save_CFLAGS"
  CXXFLAGS="$ac_save_CXXFLAGS"
 ])
