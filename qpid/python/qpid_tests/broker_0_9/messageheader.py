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

    def test_message_with_boolean_header(self):
        """The AMQP boolean type is not officially supported until 0-91 but the 0-8/9 Java client use its field value typecode.
           Note: If you run this test with QPID_CODEC_DISABLE_0_91_BOOLEAN set, this test will still pass as the booleans are
           coerced into integer."""

        props={"headers":{"trueHeader":True, "falseHeader":False}}
        self.queue_declare(queue="q")
        q = self.consume("q")
        self.assertPublishGet(q, routing_key="q", properties=props)

    def test_message_with_negatives_longints_floats_and_None(self):
        """ Tests sending and then receiving negative integers, longs, the None (void) value, and doubles."""
        props={"headers":{"myIntMin": -2147483648,
                          "myIntMax": 2147483647,
                          "myLongMax": 9223372036854775807,
                          "myLongMin": -9223372036854775808,
                          "myNullString": None,
                          "myDouble1.1": 1.1,
                          "myDoubleMin": 4.9E-324,
                          "myDoubleMax": 1.7976931348623157E308}}

        self.queue_declare(queue="q")
        q = self.consume("q")
        self.assertPublishGet(q, routing_key="q", properties=props)

