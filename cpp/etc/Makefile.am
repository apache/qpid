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
SASL_CONF = sasl2/qpidd.conf

EXTRA_DIST = \
	$(SASL_CONF) \
	qpidd.service qpidd.in qpidd-primary.in qpidd.conf qpidc.conf CMakeLists.txt \
	cluster.conf-example.xml.in

confdir = $(sysconfdir)/qpid
nobase_conf_DATA=\
	qpidc.conf

nobase_sysconf_DATA = \
	qpidd.conf

if HAVE_SASL
nobase_sysconf_DATA += \
	$(SASL_CONF)
endif

# Substitute values for directories in init scripts.
#
# We can't use autoconf substitution directly because it leaves
#  ${prefix} and ${exec_prefix} unexpanded. Substitute with sed
#  scripts.
SUBST="s|!!sysconfdir!!|${sysconfdir}|;s|!!sbindir!!|${sbindir}|;s|!!bindir!!|${bindir}|"
qpidd: qpidd.in
	sed $(SUBST) $< > $@
qpidd-primary: qpidd-primary.in
	sed $(SUBST) $< > $@
cluster.conf-example.xml: cluster.conf-example.xml.in
	sed $(SUBST) $< > $@

CLEANFILES = qpidd qpidd-primary cluster.conf-example.xml qpidd.service

initddir = $(sysconfdir)/init.d
nobase_initd_SCRIPTS = qpidd qpidd-primary

systemddir = /usr/lib/systemd/system
nobase_systemd_SCRIPTS = qpidd.service
