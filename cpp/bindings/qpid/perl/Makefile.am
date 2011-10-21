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

if HAVE_PERL_DEVEL

INCLUDES = -I$(top_srcdir)/include -I$(top_builddir)/include -I$(top_srcdir)/src -I$(top_builddir)/src -I$(PERL_INC)

EXTRA_DIST = CMakeLists.txt perl.i
BUILT_SOURCES = cqpid_perl.cpp
SWIG_FLAGS = -w362,401

cqpid_perl.cpp: $(srcdir)/perl.i $(srcdir)/../qpid.i $(srcdir)/../../swig_perl_typemaps.i
	$(SWIG) -perl -c++ $(SWIG_FLAGS) $(INCLUDES) $(QPID_CXXFLAGS) -I/usr/include -o cqpid_perl.cpp $(srcdir)/perl.i

lib_LTLIBRARIES = libcqpid_perl.la
cqpid_perl_PERL = cqpid_perl.pm

libcqpid_perl_la_LDFLAGS = -avoid-version -shared
libcqpid_perl_la_LIBADD = -L$(top_builddir)/src/.libs -lqpidmessaging -lqpidtypes \
	$(top_builddir)/src/libqpidmessaging.la $(top_builddir)/src/libqpidtypes.la
libcqpid_perl_la_CXXFLAGS = $(INCLUDES) -fno-strict-aliasing
nodist_libcqpid_perl_la_SOURCES = cqpid_perl.cpp

CLEANFILES = cqpid_perl.cpp cqpid_perl.pm

endif # HAVE_PERL_DEVEL
