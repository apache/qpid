dnl Process this file with autoconf to produce a configure script.
dnl
dnl This file is free software; as a special exception the author gives
dnl unlimited permission to copy and/or distribute it, with or without
dnl modifications, as long as this notice is preserved.
dnl
dnl This program is distributed in the hope that it will be useful, but
dnl WITHOUT ANY WARRANTY, to the extent permitted by law; without even the
dnl implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

AC_INIT([qpidc], [0.2], [qpid-dev@incubator.apache.org])
AC_CONFIG_AUX_DIR([build-aux])
AM_INIT_AUTOMAKE([dist-bzip2 subdir-objects])

# Minimum Autoconf version required.
AC_PREREQ(2.59)

AC_CONFIG_HEADERS([src/config.h])
AC_CONFIG_SRCDIR([qpidc.spec.in])

AC_PROG_CC_STDC
AM_PROG_CC_C_O
AC_PROG_CXX
AC_USE_SYSTEM_EXTENSIONS
AC_LANG([C++])	

# AM_MISSING_PROG([HELP2MAN], [help2man])
AC_CHECK_PROG([HELP2MAN], [help2man], [help2man])
test -z "$HELP2MAN" && AC_MSG_ERROR([Missing help2man installation (try "yum install help2man").])

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
  gl_COMPILER_FLAGS(-Woverloaded-virtual)	
  AC_SUBST([WARNING_CFLAGS], [$COMPILER_FLAGS])
  AC_DEFINE([lint], 1, [Define to 1 if the compiler is checking for lint.])
  COMPILER_FLAGS=
fi

AC_DISABLE_STATIC
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

# Check for cppunit support.
CPPUNIT_MINIMUM_VERSION=1.10.2
AM_PATH_CPPUNIT([$CPPUNIT_MINIMUM_VERSION], , [CPPUNIT_LIBS=-lcppunit])
CPPUNIT_CXXFLAGS=$CPPUNIT_CFLAGS
AC_SUBST(CPPUNIT_LIBS)
AC_SUBST(CPPUNIT_CXXFLAGS)

# Enable Valgrind	
AC_ARG_ENABLE([valgrind],
  [AS_HELP_STRING([--enable-valgrind],
    [run valgrind memory checker on tests, if available (default yes)])],
  [case $enableval in
    yes|no) enable_VALGRIND=$enableval;;
    *) AC_MSG_ERROR([Invalid value for --enable-valgrind: $enableval]);;
   esac],
  [enable_VALGRIND=yes]
)

# We use valgrind for the tests.  See if it's available.
# Check for it unconditionally, so we don't have to duplicate its
# use of AC_SUBST([VALGRIND]).
AC_CHECK_PROG([VALGRIND], [valgrind], [valgrind])
test "$enable_VALGRIND" = no && VALGRIND=

# If rpmlint is availalbe we'll run it when building RPMs.
AC_CHECK_PROG([RPMLINT], [rpmlint], [rpmlint])
AM_CONDITIONAL([HAS_RPMLINT], [test -n "$RPMLINT"])

# Code generation: generated code is included in the distribution
# so code generation is only required in an svn checkout.
# It requires several external tools and files, which we check for here.

AC_CHECK_PROG([RUBY], [ruby], [ruby])
test -n "$RUBY" && generate=yes
test -z "$RUBY" && AC_MSG_ERROR([Missing ruby installation (try "yum install ruby").])

specdir=`pwd`/$srcdir/../specs  
AMQP_PREVIEW_XML=$specdir/amqp.0-10-preview.xml
AMQP_FINAL_XML=$specdir/amqp.0-10.xml
AC_SUBST(AMQP_PREVIEW_XML)
AC_SUBST(AMQP_FINAL_XML)
AM_CONDITIONAL([GENERATE], [ls $AMQP_FINAL_XML >/dev/null])

# URL and download URL for the package.
URL=http://rhm.et.redhat.com/qpidc
AC_SUBST(URL)
DOWNLOAD_URL=http://rhm.et.redhat.com/download
AC_SUBST(DOWNLOAD_URL)

# Check for headers from required devel kits.
AC_CHECK_HEADERS([boost/shared_ptr.hpp uuid/uuid.h],,
  AC_MSG_ERROR([Missing required header files.]))

# Check for optional CPG requirement.
LDFLAGS="$LDFLAGS -L/usr/lib/openais -L/usr/lib64/openais"

AC_ARG_WITH([cpg],  
  [AS_HELP_STRING([--with-cpg], [Build with CPG support])],
  [case "${withval}" in
    yes)			# yes - enable
    with_CPG=yes
    AC_CHECK_LIB([cpg],[cpg_initialize],,[AC_MSG_ERROR([libcpg not found, install openais])])
    AC_CHECK_HEADERS([openais/cpg.h],,[AC_MSG_ERROR([openais/cpg.h not found, install openais-devel])])
    ;;
    no)				# no -disable
    with_CPG=no
    ;; 
    *) AC_MSG_ERROR([Bad value ${withval} for --with-cpg option]) ;;
   esac],
   [ # not specified - enable if libs/headers available.
    with_CPG=yes
    AC_CHECK_HEADERS([openais/cpg.h],,[with_CPG=no])
    AC_CHECK_LIB([cpg],[cpg_initialize],,[with_CPG=no])
   ]
)
AM_CONDITIONAL([CPG], [test x$with_CPG = xyes])
if test x$with_CPG = xyes; then
  CPPFLAGS+=" -DCPG"
fi
	
# Files to generate	
AC_CONFIG_FILES([
  qpidc.spec
  Makefile
  examples/Makefile
  src/Makefile
  src/tests/Makefile
  docs/man/Makefile
  docs/api/Makefile
  docs/api/user.doxygen
  docs/api/developer.doxygen
  ])
AC_OUTPUT

