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
from unittest import TestCase
from qpid.spec import load
from qpid.testlib import testrunner

class SpecTest(TestCase):

  def check_load(self, *urls):
    spec = load(*map(testrunner.get_spec_file, urls))
    qdecl = spec.method("queue_declare")
    assert qdecl != None
    assert not qdecl.content

    queue = qdecl.fields.byname["queue"]
    assert queue != None
    assert queue.domain.name == "queue_name"
    assert queue.type == "shortstr"

    qdecl_ok = spec.method("queue_declare_ok")

    # 0-8 is actually 8-0
    if (spec.major == 8 and spec.minor == 0 or
        spec.major == 0 and spec.minor == 9):
      assert qdecl_ok != None

      assert len(qdecl.responses) == 1
      assert qdecl_ok in qdecl.responses

    publish = spec.method("basic_publish")
    assert publish != None
    assert publish.content

    if (spec.major == 0 and spec.minor == 10):
      assert qdecl_ok == None
      reply_to = spec.domains.byname["reply_to"]
      assert reply_to.type.size == 2
      assert reply_to.type.pack == 2
      assert len(reply_to.type.fields) == 2

      qq = spec.method("queue_query")
      assert qq != None
      assert qq.result.size == 4
      assert qq.result.type != None
      args = qq.result.fields.byname["arguments"]
      assert args.type == "table"

  def test_load_0_8(self):
    self.check_load("amqp.0-8.xml")

  def test_load_0_9(self):
    self.check_load("amqp.0-9.xml")

  def test_load_0_9_errata(self):
    self.check_load("amqp.0-9.xml", "amqp-errata.0-9.xml")

  def test_load_0_10(self):
    self.check_load("amqp.0-10-preview.xml")
