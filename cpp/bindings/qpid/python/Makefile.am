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

INCLUDES = -I$(top_srcdir)/include -I$(top_srcdir)/bindings -I$(top_builddir)/include -I$(top_srcdir)/src/qmf -I$(top_srcdir)/src -I$(top_builddir)/src

generated_file_list = \
	cqpid.cpp \
	cqpid.py

EXTRA_DIST = CMakeLists.txt python.i
BUILT_SOURCES = $(generated_file_list)
SWIG_FLAGS = -w362,401

$(generated_file_list): $(srcdir)/python.i
	$(SWIG) -c++ -python $(SWIG_FLAGS) $(INCLUDES) $(QPID_CXXFLAGS) -I$(top_srcdir)/src/qmf -I/usr/include -o cqpid.cpp $(srcdir)/python.i

pylibdir = $(pyexecdir)

pylib_LTLIBRARIES = _cqpid.la
cqpiddir = $(pythondir)
cqpid_PYTHON = cqpid.py

_cqpid_la_LDFLAGS = -avoid-version -module -shared
_cqpid_la_LIBADD = $(PYTHON_LIBS) -L$(top_builddir)/src/.libs -lqpidmessaging -lqpidtypes $(top_builddir)/src/libqpidmessaging.la $(top_builddir)/src/libqpidtypes.la
_cqpid_la_CXXFLAGS = $(INCLUDES) $(PYTHON_CFLAGS) -fno-strict-aliasing
nodist__cqpid_la_SOURCES = cqpid.cpp

CLEANFILES = $(generated_file_list)

endif  # HAVE_PYTHON_DEVEL

