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
#
# Build RPMs from the distribution tarball.
#

abs_top_srcdir=@abs_top_srcdir@
abs_builddir=@abs_builddir@

SPEC=${top_srcdir}/qpidc.spec
RPMOPTS=--define "_sourcedir ${abs_top_srcdir}" --define "_topdir ${abs_builddir}" --define "dist .M2"

clean-local:
	-rm -rf BUILD  RPMS  SOURCES  SPECS  SRPMS

.PHONY: rpm srpm dist

# Build source and binary RPMs.
rpm: dist
	rpmbuild $(RPMOPTS) $(RPMEXTRAOPTS) -ba $(SPEC)
	rpmlint RPMS/*/*.rpm	

# Build source RPM only.
srpm: dist
	rpmbuild $(RPMOPTS) -bs $(SPEC)

# Build source distribution and create required subdirs.
dist:
	cd .. && $(MAKE) $(AM_MAKEFLAGS) dist
	mkdir -p BUILD  RPMS  SOURCES  SPECS  SRPMS
