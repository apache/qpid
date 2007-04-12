dnl
dnl AM_PATH_QPID(MINIMUM-VERSION, [ACTION-IF-FOUND [, ACTION-IF-NOT-FOUND]])
dnl
AC_DEFUN([AM_PATH_QPID],
[

AC_ARG_WITH(qpid-prefix,[  --with-qpid-prefix=PFX   Prefix where Qpid is installed (optional)],
            qpid_config_prefix="$withval", qpid_config_prefix="")
AC_ARG_WITH(qpid-exec-prefix,[  --with-qpid-exec-prefix=PFX  Exec prefix where Qpid is installed (optional)],
            qpid_config_exec_prefix="$withval", qpid_config_exec_prefix="")

  if test x$qpid_config_exec_prefix != x ; then
     qpid_config_args="$qpid_config_args --exec-prefix=$qpid_config_exec_prefix"
     if test x${QPID_CONFIG+set} != xset ; then
        QPID_CONFIG=$qpid_config_exec_prefix/bin/qpid-config
     fi
  fi
  if test x$qpid_config_prefix != x ; then
     qpid_config_args="$qpid_config_args --prefix=$qpid_config_prefix"
     if test x${QPID_CONFIG+set} != xset ; then
        QPID_CONFIG=$qpid_config_prefix/bin/qpid-config
     fi
  fi

  AC_PATH_PROG(QPID_CONFIG, qpid-config, no)
  qpid_version_min=$1

  AC_MSG_CHECKING(for Qpid - version >= $qpid_version_min)
  no_qpid=""
  if test "$QPID_CONFIG" = "no" ; then
    AC_MSG_RESULT(no)
    no_qpid=yes
  else
    QPID_CFLAGS=`$QPID_CONFIG --cflags`
    QPID_LIBS=`$QPID_CONFIG --libs`
    qpid_version=`$QPID_CONFIG --version`

    qpid_major_version=`echo $qpid_version | \
           sed 's/\([[0-9]]*\).\([[0-9]]*\).\([[0-9]]*\)/\1/'`
    qpid_minor_version=`echo $qpid_version | \
           sed 's/\([[0-9]]*\).\([[0-9]]*\).\([[0-9]]*\)/\2/'`
    qpid_micro_version=`echo $qpid_version | \
           sed 's/\([[0-9]]*\).\([[0-9]]*\).\([[0-9]]*\)/\3/'`

    qpid_major_min=`echo $qpid_version_min | \
           sed 's/\([[0-9]]*\).\([[0-9]]*\).\([[0-9]]*\)/\1/'`
    if test "x${qpid_major_min}" = "x" ; then
       qpid_major_min=0
    fi

    qpid_minor_min=`echo $qpid_version_min | \
           sed 's/\([[0-9]]*\).\([[0-9]]*\).\([[0-9]]*\)/\2/'`
    if test "x${qpid_minor_min}" = "x" ; then
       qpid_minor_min=0
    fi

    qpid_micro_min=`echo $qpid_version_min | \
           sed 's/\([[0-9]]*\).\([[0-9]]*\).\([[0-9]]*\)/\3/'`
    if test "x${qpid_micro_min}" = "x" ; then
       qpid_micro_min=0
    fi

    qpid_version_proper=`expr \
        $qpid_major_version \> $qpid_major_min \| \
        $qpid_major_version \= $qpid_major_min \& \
        $qpid_minor_version \> $qpid_minor_min \| \
        $qpid_major_version \= $qpid_major_min \& \
        $qpid_minor_version \= $qpid_minor_min \& \
        $qpid_micro_version \>= $qpid_micro_min `

    if test "$qpid_version_proper" = "1" ; then
      AC_MSG_RESULT([$qpid_major_version.$qpid_minor_version.$qpid_micro_version])
    else
      AC_MSG_RESULT(no)
      no_qpid=yes
    fi
  fi

  if test "x$no_qpid" = x ; then
     ifelse([$2], , :, [$2])
  else
     QPID_CFLAGS=""
     QPID_LIBS=""
     ifelse([$3], , :, [$3])
  fi

  AC_SUBST(QPID_CFLAGS)
  AC_SUBST(QPID_LIBS)
])
