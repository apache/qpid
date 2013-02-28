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

from qpid.testlib import TestBase

class MessageHeaderTests(TestBase):
    """Verify that messages with headers work as expected"""

    def test_message_with_integer_header(self):
        props={"headers":{"one":1, "zero":0}}
        self.queue_declare(queue="q")
        q = self.consume("q")
        self.assertPublishGet(q, routing_key="q", properties=props)

    def test_message_with_string_header(self):
        props={"headers":{"mystr":"hello world", "myempty":""}}
        self.queue_declare(queue="q")
        q = self.consume("q")
        self.assertPublishGet(q, routing_key="q", properties=props)
