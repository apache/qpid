dnl Process this file with autoconf to produce a configure script.
dnl
dnl This file is free software; as a special exception the author gives
dnl unlimited permission to copy and/or distribute it, with or without
dnl modifications, as long as this notice is preserved.
dnl
dnl This program is distributed in the hope that it will be useful, but
dnl WITHOUT ANY WARRANTY, to the extent permitted by law; without even the
dnl implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
dnl
dnl When updating the name/version number here, also update it in
dnl src/qpid/Version.h

AC_INIT([qpidc], [0.4], [qpid-dev@incubator.apache.org])
AC_CONFIG_AUX_DIR([build-aux])
AM_INIT_AUTOMAKE([dist-bzip2 subdir-objects])

# Minimum Autoconf version required.
AC_PREREQ(2.59)

AC_CONFIG_HEADERS([src/config.h])

AC_PROG_CC_STDC
AM_PROG_CC_C_O
AC_PROG_CXX
AC_USE_SYSTEM_EXTENSIONS
AC_LANG([C++])	

# Check for optional use of help2man
AC_CHECK_PROG([HELP2MAN], [help2man], [help2man])
AC_ARG_WITH([help2man],
  [AS_HELP_STRING([--with-help2man], [Use help2man to generate man pages.])],
  [case "$withval" in
    yes) test -z "$HELP2MAN" && AC_MSG_ERROR([help2man not found.]) ;;
    no) HELP2MAN="" ;;
    *)   AC_MSG_ERROR([Bad value ${withval} for --with-help2man.]) ;;
   esac])
AM_CONDITIONAL([HAVE_HELP2MAN], [test -n "$HELP2MAN"])

# Check for optional use of doxygen
AC_CHECK_PROG([DOXYGEN], [doxygen], [doxygen])
AC_ARG_WITH([doxygen],
  [AS_HELP_STRING([--with-doxygen], [Use doxygen to generate API documentation.])],
  [case "$withval" in
    yes) test -z "$DOXYGEN" && AC_MSG_ERROR([doxygen not found.]) ;;
    no) DOXYGEN="" ;;
    *)   AC_MSG_ERROR([Bad value ${withval} for --with-doxygen.]) ;;
   esac])
AM_CONDITIONAL([HAVE_DOXYGEN], [test -n "$DOXYGEN"])

AC_ARG_ENABLE(warnings,
[  --enable-warnings   turn on lots of compiler warnings (recommended)],
[case "${enableval}" in
   yes|no) ;;
   *)  AC_MSG_ERROR([bad value ${enableval} for warnings option]) ;;
 esac],
 [enableval=yes])

# Set up for gcc as compiler
if test x$GXX = xyes; then
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
else
	AC_CHECK_DECL([__SUNPRO_CC], [SUNCC=yes], [SUNCC=no])
	
	# Set up for sun CC compiler
	if test x$SUNCC = xno; then
		if test "${enableval}" = yes; then
			WARNING_FLAGS=+w
		fi
		CXXFLAGS="$CXXFLAGS -library=stlport4 -mt"
		LD="$CXX"
		LDFLAGS="$LDFLAGS -library=stlport4 -mt"
	fi
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

# If rpmlint is available we'll run it when building RPMs.
AC_CHECK_PROG([RPMLINT], [rpmlint], [rpmlint])
AM_CONDITIONAL([HAS_RPMLINT], [test -n "$RPMLINT"])

# Code generation: generated code is included in the distribution
# so code generation is only required in an svn checkout.
# It requires several external tools and files, which we check for here.

AC_CHECK_PROG([RUBY], [ruby], [ruby])
test -n "$RUBY" && generate=yes
test -z "$RUBY" && AC_MSG_ERROR([Missing ruby installation (try "yum install ruby").])

specdir=`pwd`/$srcdir/../specs  
AMQP_FINAL_XML=$specdir/amqp.0-10-qpid-errata.xml
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

# Check for optional cluster requirements.
tmp_LIBS=$LIBS
LDFLAGS="$LDFLAGS -L/usr/lib/openais -L/usr/lib64/openais"
AC_ARG_WITH([cpg],  
  [AS_HELP_STRING([--with-cpg], [Build with CPG support for clustering.])],
  [case "${withval}" in
    yes)			# yes - enable
    with_CPG=yes
    AC_CHECK_LIB([cpg],[cpg_local_get],,[AC_MSG_ERROR([libcpg not found, install openais])])
    AC_CHECK_HEADERS([openais/cpg.h],,[AC_MSG_ERROR([openais/cpg.h not found, install openais-devel])])
    ;;
    no) with_CPG=no ;; 
    *) AC_MSG_ERROR([Bad value ${withval} for --with-cpg option]) ;;
   esac],
   [		   # not specified - use if present
    with_CPG=yes
    AC_CHECK_LIB([cpg],[cpg_local_get],,[with_CPG=no])
    AC_CHECK_HEADERS([openais/cpg.h],,[with_CPG=no])
   ]
)
AM_CONDITIONAL([HAVE_LIBCPG], [test x$with_CPG = xyes])

AC_ARG_WITH([libcman],  
  [AS_HELP_STRING([--with-libcman], [Integration with libcman quorum service.])],
  [case "${withval}" in
    yes)			# yes - enable
    with_LIBCMAN=yes
    AC_CHECK_LIB([cman],[cman_is_quorate],,[AC_MSG_ERROR([libcman not found, install libcman package.])])
    AC_CHECK_HEADERS([libcman.h],,[AC_MSG_ERROR([libcman.h not found, install cman-devel])])
    ;;
    no) with_LIBCMAN=no ;; 
    *) AC_MSG_ERROR([Bad value ${withval} for --with-libcman option]) ;;
   esac],
   [		   # not specified - use if present and with_CPG=yes
    with_LIBCMAN=$with_CPG
    AC_CHECK_LIB([cman],[cman_is_quorate],,with_LIBCMAN=no)
    AC_CHECK_HEADERS([libcman.h],,with_LIBCMAN=no)
   ]
)
AM_CONDITIONAL([HAVE_LIBCMAN], [test x$with_LIBCMAN = xyes])

LIBS=$tmp_LIBS

# Setup --with-sasl/--without-sasl as arguments to configure
AC_ARG_WITH([sasl],
  [AS_HELP_STRING([--with-sasl], [Build with SASL authentication support])],
  [WANT_SASL="$withval"],
  [WANT_SASL=check])

# Make sure --with-sasl/--without-sasl were only give yes|no|check
AS_IF([test "x$WANT_SASL" != xyes -a \
            "x$WANT_SASL" != xno -a \
            "x$WANT_SASL" != xcheck],
  [AC_MSG_ERROR([Bad value for --with-sasl: $withval])])

# If we weren't explicitly asked /not/ to test, i.e. not given --without-sasl
have_sasl=no
AS_IF([test "x$WANT_SASL" != xno],
  # Perform tests for headers and libraries. Remember, AC_CHECK_LIB
  # will give you some useful default behavior, e.g. setup LDFLAGS, if
  # you do not give it a second argument, so try not to
  [AC_CHECK_HEADER([sasl/sasl.h], , [HAVE_SASL_H=no])
   tmp_LIBS=$LIBS
   AC_CHECK_LIB([sasl2], [sasl_checkpass], , [HAVE_SASL_LIB=no])
   # Remove from LIBS, we will link it explicitly in make files.
   LIBS=$tmp_LIBS
   # If any of the tests failed
   AS_IF([test "x$HAVE_SASL_H" = xno -o \
               "x$HAVE_SASL_LIB" = xno],
     # And we were given --with, then fail
     [AS_IF([test "x$WANT_SASL" = xyes],
       [AC_MSG_ERROR([sasl requested but not available])])],
     # Otherwise, no tests failed, setup AC_SUBST/AC_DEFINE/vars for AM_CONDITIONALs
     [AC_DEFINE([BROKER_SASL_NAME], ["qpidd"],
                [The SASL app name for the qpid Broker])
      AC_DEFINE([HAVE_SASL], [1], [Enable if libsasl is present])
      have_sasl=yes])])
AM_CONDITIONAL([HAVE_SASL], [test "x$have_sasl" = xyes])


# Setup --with-xml/--without-xml as arguments to configure
use_xml=yes
want_xml=check
AC_ARG_WITH([xml],
  [AS_HELP_STRING([--with-xml], [Build with XML Exchange])],
  [want_xml=$withval])

case $want_xml in
  yes|no|check) ;;
  *) AC_MSG_ERROR([Bad value for --with-xml: $withval]) ;;
esac

test $want_xml = no && use_xml=no

# If the user doesn't say not to use XML, see if it's available.
if test $use_xml != no; then
  # Then see if XQilla is available
  tmp_LIBS=$LIBS
  AC_CHECK_LIB([xerces-c], [_init], , [use_xml=no])
  AC_CHECK_HEADER([xercesc/framework/MemBufInputSource.hpp], , [use_xml=no])
  AC_CHECK_HEADER([xqilla/xqilla-simple.hpp], , [use_xml=no])
  AC_CHECK_LIB([xqilla], [_init], , [use_xml=no])
  # Remove from LIBS, we will link it explicitly in make files.
  LIBS=$tmp_LIBS

  # If XQilla is not available, yet specifically requested, die.
  test $use_xml:$want_xml = no:yes &&
    AC_MSG_ERROR([XML Exchange requested, but XQilla or Xerces-C not available])

  # Else XQilla is available - use it to build
  test $use_xml = yes &&
    AC_DEFINE([HAVE_XML], [1], [Compile-in XML Exchange support.])
fi

AM_CONDITIONAL([HAVE_XML], [test $use_xml = yes])

# Setup --with-rdma/--without-rdma as arguments to configure
tmp_LIBS=$LIBS
AC_ARG_WITH([rdma],
  [AS_HELP_STRING([--with-rdma], [Build with support for Remote DMA protocols])],
  [case ${withval} in
   yes)
     with_RDMA=yes
     AC_CHECK_LIB([ibverbs],[ibv_create_qp],,[AC_MSG_ERROR([libibverbs not found])])
     AC_CHECK_LIB([rdmacm],[rdma_create_id],,[AC_MSG_ERROR([librdmacm not found])])
     AC_CHECK_HEADERS([infiniband/verbs.h],,[AC_MSG_ERROR([ibverbs header files not found])])
     AC_CHECK_HEADERS([rdma/rdma_cma.h],,[AC_MSG_ERROR([rdma_cm header files not found])])
     ;;
   no)
     with_RDMA=no
     ;;
   *)
     AC_MSG_ERROR([Bad value for --with-rdma: ${withval}])
     ;;
   esac],
  [
   with_RDMA=yes
   AC_CHECK_LIB([ibverbs],[ibv_create_qp],,[with_RDMA=no])
   AC_CHECK_LIB([rdmacm],[rdma_create_id],,[with_RDMA=no])
   AC_CHECK_HEADERS([infiniband/verbs.h],,[with_RDMA=no])
   AC_CHECK_HEADERS([rdma/rdma_cma.h],,[with_RDMA=no])
  ]
)
# Remove from LIBS, we will link it explicitly in make files.
LIBS=$tmp_LIBS
AM_CONDITIONAL([RDMA], [test x$with_RDMA = xyes])

# Setup --with-ssl/--without-ssl as arguments to configure
tmp_LIBS=$LIBS
AC_ARG_WITH([ssl],
  [AS_HELP_STRING([--with-ssl], [Build with support for SSL])],
  [case ${withval} in
   yes)
     with_SSL=yes
     PKG_CHECK_MODULES([SSL], [nspr],,[AC_MSG_ERROR([nspr not found])])
     PKG_CHECK_MODULES([SSL], [nss],,[AC_MSG_ERROR([nss not found])])
     ;;
   no)
     with_SSL=no
     ;;
   *)
     AC_MSG_ERROR([Bad value for --with-ssl: ${withval}])
     ;;
   esac],
  [
    with_SSL=yes
    PKG_CHECK_MODULES([SSL], [nspr],,[with_SSL=no])
    PKG_CHECK_MODULES([SSL], [nss],,[with_SSL=no])
  ]
)
# Remove from LIBS, we will link it explicitly in make files.
LIBS=$tmp_LIBS
AM_CONDITIONAL([SSL], [test x$with_SSL = xyes])


poller=no
AC_ARG_WITH([poller],
 [AS_HELP_STRING([--with-poller], [The low level poller implementation: poll/solaris-ecf/epoll])],
 [case ${withval} in
  poll)
   AC_CHECK_HEADERS([sys/poll.h],[poller=no],[AC_MSG_ERROR([Can't find poll.h header file for poll])])
   ;;
  solaris-ecf)
   AC_CHECK_HEADERS([port.h],[poller=solaris-ecf],[AC_MSG_ERROR([Can't find port.h header file for solaris-ecf])])
   ;;
  epoll)
   AC_CHECK_HEADERS([sys/epoll.h],[poller=epoll],[AC_MSG_ERROR([Can't find epoll.h header file for epoll])])
   ;;
 esac],
 [
  AC_CHECK_HEADERS([sys/poll.h],[poller=no],)
  AC_CHECK_HEADERS([port.h],[poller=solaris-ecf],)
  AC_CHECK_HEADERS([sys/epoll.h],[poller=epoll],)
 ]
)

AM_CONDITIONAL([HAVE_ECF], [test x$poller = xsolaris-ecf])
AM_CONDITIONAL([HAVE_EPOLL], [test x$poller = xepoll])

#Filter not implemented or invalid mechanisms
if test $poller = xno; then
  AC_MSG_ERROR([Polling mechanism not implemented for $host])
fi

AM_PATH_PYTHON()

# Files to generate	
AC_CONFIG_FILES([
  Makefile
  examples/Makefile
  examples/direct/Makefile
  examples/fanout/Makefile
  examples/pub-sub/Makefile
  examples/request-response/Makefile
  examples/failover/Makefile
  examples/xml-exchange/Makefile
  managementgen/Makefile
  etc/Makefile
  src/Makefile
  src/tests/Makefile
  docs/man/Makefile
  docs/api/Makefile
  docs/api/user.doxygen
  docs/api/developer.doxygen
  ])
AC_OUTPUT

