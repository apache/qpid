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

QPID_HOME = ../../../..
include ${QPID_HOME}/cpp/options.mk

STYLESHEET_DIR = stylesheets
JAVA = java
XSLTP = ${TOOLS_DIR}/saxon8.jar

SPEC = ${SPEC_DIR}/amqp-8.0.xml
STYLESHEETS = $(wildcard stylesheets/*.xsl)

GENERATED_SOURCES=amqp_methods.cpp # Seed generation

.PHONY: all clean 

all: ${GENERATED_SOURCES}

clean :
	-@rm -f  *.cpp *.h

${GENERATED_SOURCES}: ${STYLESHEETS} ${SPEC}
	${JAVA} -jar ${XSLTP} -o results.out ${SPEC} ${STYLESHEET_DIR}/code_gen.xsl
	${JAVA} -jar ${XSLTP} -o results.out ${SPEC} ${STYLESHEET_DIR}/framing.xsl

-include $(GENERATED_SOURCES:.cpp=.d)

