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

if HAVE_SWIG
if HAVE_SASL

lib_LTLIBRARIES = libsaslwrapper.la
libsaslwrapper_la_SOURCES = saslwrapper.h cyrus/saslwrapper.cpp

# Library Version Information:
#
#  CURRENT  => API/ABI version.  Bump this if the interface changes
#  REVISION => Version of underlying implementation.
#              Bump if implementation changes but API/ABI doesn't
#  AGE      => Number of API/ABI versions this is backward compatible with
#
CURRENT  = 1
REVISION = 0
AGE      = 0

libsaslwrapper_la_LDFLAGS = -version-info $(CURRENT):$(REVISION):$(AGE)
EXTRA_DIST = saslwrapper.i
CLEANFILES =

if HAVE_PYTHON_DEVEL

EXTRA_DIST += $(srcdir)/python/python.i
generated_file_list = python/qpidsasl.cpp python/qpidsasl.py
BUILT_SOURCES = $(generated_file_list)

$(generated_file_list): $(srcdir)/python/python.i $(srcdir)/saslwrapper.i
	$(SWIG) -c++ -python -Wall -I/usr/include -I. -o python/qpidsasl.cpp $(srcdir)/python/python.i

pylibdir = $(PYTHON_LIB)
lib_LTLIBRARIES += _qpidsasl.la

_qpidsasl_la_LDFLAGS = -avoid-version -module -shared
_qpidsasl_la_LIBADD = $(PYTHON_LIBS) libsaslwrapper.la -lsasl2
_qpidsasl_la_CXXFLAGS = -I$(PYTHON_INC)
nodist__qpidsasl_la_SOURCES = python/qpidsasl.cpp

CLEANFILES += $(generated_file_list)
endif

if HAVE_RUBY_DEVEL
endif

endif
endif
