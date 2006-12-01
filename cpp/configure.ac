dnl Process this file with autoconf to produce a configure script.
dnl
dnl This file is free software; as a special exception the author gives
dnl unlimited permission to copy and/or distribute it, with or without
dnl modifications, as long as this notice is preserved.
dnl
dnl This program is distributed in the hope that it will be useful, but
dnl WITHOUT ANY WARRANTY, to the extent permitted by law; without even the
dnl implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

AC_INIT([qpid], [0.1], [qpid-dev@incubator.apache.org])
AC_CONFIG_AUX_DIR([build-aux])
AM_INIT_AUTOMAKE([dist-bzip2])

# Minimum Autoconf version required.
AC_PREREQ(2.59)

# Where to generate output; srcdir location.
# FIXME: if no one includes config.h, don't do this
AC_CONFIG_HEADERS([config.h:config.in])
AC_CONFIG_SRCDIR([lib/broker/ExchangeBinding.cpp])

AC_PROG_CC_STDC
AM_PROG_CC_C_O
AC_PROG_CXX
AC_USE_SYSTEM_EXTENSIONS

AC_ARG_ENABLE(warnings,
[  --enable-warnings   turn on lots of compiler warnings (recommended)],
[case "${enableval}" in
   yes|no) ;;
   *)      AC_MSG_ERROR([bad value ${enableval} for warnings option]) ;;
 esac],
 [enableval=yes])
 
# Warnings: Enable as many as possible, keep the code clean. Please
# do not disable warnings or remove -Werror without discussing on
# qpid-dev list.
#
# The following warnings are deliberately omitted, they warn on valid code.
# -Wunreachable-code -Wpadded -Winline
# -Wshadow - warns about boost headers.

if test "${enableval}" = yes; then
  gl_COMPILER_FLAGS(-Werror)
  gl_COMPILER_FLAGS(-pedantic)
  gl_COMPILER_FLAGS(-Wall)
  gl_COMPILER_FLAGS(-Wextra)
  gl_COMPILER_FLAGS(-Wno-shadow)
  gl_COMPILER_FLAGS(-Wpointer-arith)
  gl_COMPILER_FLAGS(-Wcast-qual)
  gl_COMPILER_FLAGS(-Wcast-align)
  gl_COMPILER_FLAGS(-Wno-long-long)
  gl_COMPILER_FLAGS(-Wvolatile-register-var)
  gl_COMPILER_FLAGS(-Winvalid-pch)
  gl_COMPILER_FLAGS(-Wno-system-headers)
  AC_SUBST([WARNING_CFLAGS], [$COMPILER_FLAGS])
  AC_DEFINE([lint], 1, [Define to 1 if the compiler is checking for lint.])
  COMPILER_FLAGS=
fi

AC_PROG_LIBTOOL
AC_SUBST([LIBTOOL_DEPS])

# For libraries (libcommon) that use dlopen, dlerror, etc.,
# test whether we need to link with -ldl.
gl_saved_libs=$LIBS
  AC_SEARCH_LIBS(dlopen, [dl],
		 [test "$ac_cv_search_dlopen" = "none required" ||
		  LIB_DLOPEN=$ac_cv_search_dlopen])
  AC_SUBST([LIB_DLOPEN])
LIBS=$gl_saved_libs

# Set the argument to be used in "libtool -version-info ARG".
QPID_CURRENT=1
QPID_REVISION=0
QPID_AGE=1
LIBTOOL_VERSION_INFO_ARG=$QPID_CURRENT:$QPID_REVISION:$QPID_AGE
AC_SUBST(LIBTOOL_VERSION_INFO_ARG)

gl_CLOCK_TIME

AC_CONFIG_FILES([
  Makefile
  gen/Makefile
  lib/Makefile
  lib/common/Makefile
  lib/client/Makefile
  lib/broker/Makefile
  src/Makefile
  tests/Makefile
  ])

AC_OUTPUT
