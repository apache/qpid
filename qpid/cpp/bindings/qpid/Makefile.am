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

SUBDIRS = dotnet

if HAVE_SWIG

if HAVE_RUBY_DEVEL
SUBDIRS += ruby
endif

if HAVE_PYTHON_DEVEL
SUBDIRS += python
endif

if HAVE_PERL_DEVEL

INCLUDES = -I$(top_srcdir)/include -I$(top_srcdir)/bindings -I$(top_builddir)/include -I$(top_srcdir)/src -I$(top_builddir)/src -I$(PERL_INC)

EXTRA_DIST = perl/perl.i perl/CMakeLists.txt
BUILT_SOURCES = perl/cqpid_perl.cpp
SWIG_FLAGS = -w362,401

perl/cqpid_perl.cpp: $(srcdir)/perl/perl.i
	mkdir -p perl
	$(SWIG) -perl -c++ $(SWIG_FLAGS) $(INCLUDES) $(QPID_CXXFLAGS) -I/usr/include -o perl/cqpid_perl.cpp $(srcdir)/perl/perl.i

perl/Makefile: perl/cqpid_perl.cpp
	mkdir -p perl
	cd perl; \
		$(PERL) Makefile.PL PREFIX=$(prefix) ; \
	cd ..

all-local: perl/Makefile
	cd perl; \
		$(MAKE) OPTIMIZE="$(CXXFLAGS)" ; \
	cd ..

install-exec-local:
	cd perl ; \
		$(MAKE) pure_install ; \
	cd ..

clean-local:
	cd perl ; \
		$(MAKE) clean ; \
	cd ..

distclean-local:
	cd perl ; \
		$(MAKE) distclean ; \
	cd ..

maintainer-clean-local:
	cd perl ; \
		$(PERL) maintainer-clean ; \
	cd ..

CLEANFILES = perl/cqpid_perl.cpp perl/Makefile.old perl/cqpid_perl.pm

endif

endif
