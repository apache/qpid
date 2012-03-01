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
# Makefile to build and install the selinux policies in this directory.
# Needs to be run as root.

POLICIES = qpidd.pp qpiddevel.pp
SOURCES = $(POLICIES:.pp=.te)

all: $(POLICIES)

$(POLICIES): $(SOURCES)
	make -f /usr/share/selinux/devel/Makefile

install: $(POLICIES)
	for p in $(POLICIES); do /usr/sbin/semodule -i $$p; done

clean:
	rm -rf *~ *.pp *.fc *.if tmp
