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

PREFIX=/usr/local
EXEC_PREFIX=$(PREFIX)/bin
DATA_DIR=$(PREFIX)/share

PYTHON_LIB=$(shell python -c "from distutils.sysconfig import get_python_lib; print get_python_lib(prefix='$(PREFIX)')")
PYTHON_VERSION=$(shell python -c "from distutils.sysconfig import get_python_version; print get_python_version()")
AMQP_SPEC_DIR=$(DATA_DIR)/amqp

DIRS=qmf qpid mllib models examples tests tests_0-8 tests_0-9 tests_0-10
SRCS=$(shell find $(DIRS) -name "*.py") qpid_config.py
BUILD=build
TARGETS=$(SRCS:%.py=$(BUILD)/%.py) $(SRCS:%.py=$(BUILD)/%.pyc)

all: build

$(BUILD)/%.py: %.py
	@mkdir -p $(shell dirname $@)
	./preppy $(PYTHON_VERSION) < $< > $@

$(BUILD)/%.pyc: $(BUILD)/%.py
	python -m py_compile $<

$(BUILD)/qpid_config.py: qpid_config.py
	@mkdir -p $(BUILD)
	sed s@AMQP_SPEC_DIR=.*@AMQP_SPEC_DIR='"$(AMQP_SPEC_DIR)"'@ < $< > $@

build: $(TARGETS)

install: build
	install -d $(PYTHON_LIB)

	install -d $(PYTHON_LIB)/mllib
	install -pm 0644 LICENSE.txt NOTICE.txt $(BUILD)/mllib/*.* $(PYTHON_LIB)/mllib

	install -d $(PYTHON_LIB)/qpid
	install -pm 0644 LICENSE.txt NOTICE.txt README.txt $(BUILD)/qpid/*.* $(PYTHON_LIB)/qpid
	install -pm 0644 $(BUILD)/qpid_config.py $(PYTHON_LIB)

	install -d $(PYTHON_LIB)/qpid/tests
	install -pm 0644 $(BUILD)/qpid/tests/*.* $(PYTHON_LIB)/qpid/tests

	install -d $(PYTHON_LIB)/qmf
	install -pm 0644 LICENSE.txt NOTICE.txt qmf/*.* $(PYTHON_LIB)/qmf

	install -d $(PYTHON_LIB)/tests
	install -pm 0644 $(BUILD)/tests/*.* $(PYTHON_LIB)/tests

	install -d $(PYTHON_LIB)/tests_0-8
	install -pm 0644 $(BUILD)/tests_0-8/*.* $(PYTHON_LIB)/tests_0-8

	install -d $(PYTHON_LIB)/tests_0-9
	install -pm 0644 $(BUILD)/tests_0-9/*.* $(PYTHON_LIB)/tests_0-9

	install -d $(PYTHON_LIB)/tests_0-10
	install -pm 0644 $(BUILD)/tests_0-10/*.* $(PYTHON_LIB)/tests_0-10

	install -d $(EXEC_PREFIX)
	install -pm 0755 qpid-python-test commands/* $(EXEC_PREFIX)

clean:
	rm -rf $(BUILD)
