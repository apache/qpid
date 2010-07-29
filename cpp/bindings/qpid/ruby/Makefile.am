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

if HAVE_RUBY_DEVEL

INCLUDES = -I$(top_srcdir)/include -I$(top_builddir)/include -I$(top_srcdir)/src -I$(top_builddir)/src

EXTRA_DIST = ruby.i
BUILT_SOURCES = qpidw.cpp

rubylibdir = $(RUBY_LIB)

qpidw.cpp: $(srcdir)/ruby.i $(srcdir)/../qpid.i $(srcdir)/../../swig_ruby_typemaps.i
	$(SWIG) -ruby -c++ -Wall $(INCLUDES) $(QPID_CXXFLAGS) -I/usr/include -o qpidw.cpp $(srcdir)/ruby.i

rubylibarchdir = $(RUBY_LIB_ARCH)
rubylibarch_LTLIBRARIES = qpidw.la

qpidw_la_LDFLAGS = -avoid-version -module -shrext ".$(RUBY_DLEXT)"
qpidw_la_LIBADD = $(RUBY_LIBS) -L$(top_builddir)/src/.libs -lqpidmessaging -lqpidtypes \
	$(top_builddir)/src/libqpidmessaging.la $(top_builddir)/src/libqpidtypes.la
qpidw_la_CXXFLAGS = $(INCLUDES) -I$(RUBY_INC) -I$(RUBY_INC_ARCH)
nodist_qpidw_la_SOURCES = qpidw.cpp

CLEANFILES = qpidw.cpp

endif # HAVE_RUBY_DEVEL
