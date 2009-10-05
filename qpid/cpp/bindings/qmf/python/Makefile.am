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

if HAVE_PYTHON_DEVEL

INCLUDES = -I$(top_srcdir)/include -I$(top_builddir)/include -I$(top_srcdir)/src/qmf -I$(top_srcdir)/src -I$(top_builddir)/src

EXTRA_DIST = python.i

generated_file_list = \
	qmfengine.cpp \
	qmfengine.py

$(generated_file_list): $(srcdir)/python.i $(srcdir)/../qmfengine.i
	swig -python -c++ -Wall -I/usr/include $(INCLUDES) $(QPID_CXXFLAGS) -I$(top_srcdir)/src/qmf -o qmfengine.cpp $(srcdir)/python.i

AM_CPPFLAGS = $(QPID_CXXFLAGS) $(INCLUDES) -I$(srcdir)/qmf -I$(PYTHON_INC)
AM_CXXFLAGS = $(QPID_CXXFLAGS)

pylibdir = $(PYTHON_LIB)

lib_LTLIBRARIES = _qmfengine.la

_qmfengine_la_LDFLAGS = -avoid-version -module -shrext "$(PYTHON_SO)"
_qmfengine_la_LIBADD = $(PYTHON_LIBS) -L$(top_builddir)/src/.libs -lqpidclient $(top_builddir)/src/libqmfcommon.la

_qmfengine_la_SOURCES = \
	qmfengine.cpp

endif  # HAVE_PYTHON_DEVEL

