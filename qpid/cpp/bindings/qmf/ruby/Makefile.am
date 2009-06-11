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

INCLUDES = -I$(top_srcdir)/src/qmf -I$(top_srcdir)/src -I$(top_builddir)/src/gen

EXTRA_DIST = ruby.i

generated_file_list = \
	qmfengine.cpp

rubylibdir = $(RUBY_LIB)

dist_rubylib_DATA = qmf.rb

$(generated_file_list): $(srcdir)/ruby.i $(srcdir)/../qmfengine.i
	swig -ruby -c++ -Wall -I/usr/include $(INCLUDES) $(QPID_CXXFLAGS) -o qmfengine.cpp $(srcdir)/ruby.i

AM_CPPFLAGS = $(QPID_CXXFLAGS) $(INCLUDES) -I$(srcdir) -I$(RUBY_INC) -I$(RUBY_INC_ARCH)
AM_CXXFLAGS = $(QPID_CXXFLAGS)

rubylibarchdir = $(RUBY_LIB_ARCH)
rubylibarch_LTLIBRARIES = qmfengine.la

qmfengine_la_LDFLAGS = -avoid-version -module -shrext ".$(RUBY_DLEXT)"
qmfengine_la_LIBADD = $(RUBY_LIBS) -L$(top_builddir)/src/.libs -lqpidclient $(top_builddir)/src/libqmfcommon.la

qmfengine_la_SOURCES = \
	qmfengine.cpp

endif # HAVE_RUBY_DEVEL
