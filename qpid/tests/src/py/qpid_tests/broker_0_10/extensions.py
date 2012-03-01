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
from qpid.client import Client, Closed
from qpid.queue import Empty
from qpid.content import Content
from qpid.testlib import TestBase010
from qpid.session import SessionException
from qpid.datatypes import uuid4
from time import sleep

class ExtensionTests(TestBase010):
    """Tests for various extensions to AMQP 0-10"""

    def test_timed_autodelete(self):
        session = self.session
        session2 = self.conn.session("another-session")
        name=str(uuid4())
        session2.queue_declare(queue=name, exclusive=True, auto_delete=True, arguments={"qpid.auto_delete_timeout":3})
        session2.close()
        result = session.queue_query(queue=name)
        self.assertEqual(name, result.queue)
        sleep(5)
        result = session.queue_query(queue=name)
        self.assert_(not result.queue)

    def valid_policy_args(self, args, name="test-queue"):
        try:
            self.session.queue_declare(queue=name, arguments=args)
            self.session.queue_delete(queue=name) # cleanup
        except SessionException, e:
            self.fail("declare with valid policy args failed: %s" % (args))
            self.session = self.conn.session("replacement", 2)

    def invalid_policy_args(self, args, name="test-queue"):
        # go through invalid declare attempts twice to make sure that
        # the queue doesn't actually get created first time around
        # even if exception is thrown
        for i in range(1, 3):
            try:
                self.session.queue_declare(queue=name, arguments=args)
                self.session.queue_delete(queue=name) # cleanup
                self.fail("declare with invalid policy args suceeded: %s (iteration %d)" % (args, i))
            except SessionException, e:
                self.session = self.conn.session(str(uuid4()))

    def test_policy_max_size_as_valid_string(self):
        self.valid_policy_args({"qpid.max_size":"3"})

    def test_policy_max_count_as_valid_string(self):
        self.valid_policy_args({"qpid.max_count":"3"})

    def test_policy_max_count_and_size_as_valid_strings(self):
        self.valid_policy_args({"qpid.max_count":"3","qpid.max_size":"0"})

    def test_policy_negative_count(self):
        self.invalid_policy_args({"qpid.max_count":-1})

    def test_policy_negative_size(self):
        self.invalid_policy_args({"qpid.max_size":-1})

    def test_policy_size_as_invalid_string(self):
        self.invalid_policy_args({"qpid.max_size":"foo"})

    def test_policy_count_as_invalid_string(self):
        self.invalid_policy_args({"qpid.max_count":"foo"})

    def test_policy_size_as_float(self):
        self.invalid_policy_args({"qpid.max_size":3.14159})

    def test_policy_count_as_float(self):
        self.invalid_policy_args({"qpid.max_count":"2222222.22222"})
