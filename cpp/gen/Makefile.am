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
include gen-src.mk

BUILT_SOURCES = $(generated_sources) $(generated_headers)
pkginclude_HEADERS=$(generated_headers)

# Distribute the generated sources, at least for now, since
# the generator code is in java.
EXTRA_DIST = $(BUILT_SOURCES)
DISTCLEANFILES = $(BUILT_SOURCES) timestamp gen-src.mk

# Don't attempt to run the code generator unless configure has set
# CAN_GENERATE_CODE, indicating that the amqp.xml and tools needed
# to run the code generator are available.
#
if CAN_GENERATE_CODE


gentools_dir = $(srcdir)/../../gentools
spec_dir = $(srcdir)/../../specs
spec = $(spec_dir)/amqp.0-8.xml
gentools_srcdir = $(gentools_dir)/src/org/apache/qpid/gentools
gentools_libs = $(gentools_dir)/lib/velocity-1.4.jar:$(gentools_dir)/lib/velocity-dep-1.4.jar

$(BUILT_SOURCES) timestamp: $(spec) $(java_sources) $(cxx_templates)
	rm -f $(generated_sources)
	cd $(gentools_srcdir) && rm -f *.class
	$(JAVAC) -cp $(gentools_libs) -sourcepath $(gentools_srcdir) -d $(gentools_dir)/src $(gentools_srcdir)/*.java
	$(JAVA) -cp $(gentools_dir)/src:$(gentools_libs) org.apache.qpid.gentools.Main \
	        -c -o . -t $(gentools_dir)/templ.cpp $(spec)
	echo $(JAVA) -cp $(gentools_dir)/src:$(gentools_libs) org.apache.qpid.gentools.Main \
	        -c -o . -t $(gentools_dir)/templ.cpp $(spec) >> debug
	touch timestamp

gen-src.mk: timestamp
	./make-gen-src-mk.sh $(gentools_dir) $(gentools_srcdir) > $@-t
	mv $@-t $@
endif
