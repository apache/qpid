#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Master make file for c++ Qpid project (AMQP)
#
# Calls the makefiles in the various subdirectories in order to
# build them in the correct sequence.
#

UNITTESTS=$(wildcard common/*/test/*.so broker/test/*.so)
SUBDIRS=common broker qpidd client

.PHONY: all test unittest pythontest runtests clean doxygen

test:   all runtests

unittest: 
	DllPlugInTester -c -b $(UNITTESTS)

pythontest:
	bin/qpidd >> qpidd.log &
	cd ../python ; ./run-tests -v -I cpp_failing.txt	

runtests:
	$(MAKE) -k unittest pythontest

all:
	@for DIR in $(SUBDIRS) ; do $(MAKE) -C $$DIR all ; done

clean:
	@for DIR in $(SUBDIRS) ; do $(MAKE) -C $$DIR clean ; done
	@$(MAKE) -C doxygen clean
	-@rm qpidd.log 

doxygen:
	@$(MAKE) -C doxygen all
