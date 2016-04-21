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

from qpid.tests.messaging.implementation import *
from qpid.tests.messaging import VersionTest

class SelectorTests (VersionTest):
    """
    Tests for the selector filter registered for AMQP 1.0 under the
    apache namespace.
    """
    def basic_selection_test(self, node):
        properties = [(1, 'red','dog'), (2, 'black', 'cat'), (3, 'red', 'squirrel'), (4, 'grey', 'squirrel')]
        msgs = [Message(content="%s.%s" % (colour, creature), properties={'sequence':sequence,'colour':colour}) for sequence, colour, creature in properties]

        snd = self.ssn.sender(node)
        rcv = self.ssn.receiver("%s; {link:{selector:\"colour IN ('red', 'grey') AND (sequence > 3 OR sequence = 1)\"}}" % snd.target)

        for m in msgs: snd.send(m)

        for expected in ["red.dog", "grey.squirrel"]:
            msg = rcv.fetch(0)
            assert msg.content == expected
            self.ssn.acknowledge(msg)

    def test_topic(self):
        self.basic_selection_test(self.config.defines.get("topic_name", "amq.fanout"))

    def test_queue(self):
        self.basic_selection_test("#")

    def test_special_fields(self):
        msgs = [Message(content=i, id=i, correlation_id=i, priority=p+1) for p, i in enumerate(['a', 'b', 'c', 'd'])]

        snd = self.ssn.sender("#")
        rcv_1 = self.ssn.receiver("%s; {link:{selector:\"amqp.message_id = 'c'\"}}" % snd.target)
        rcv_2 = self.ssn.receiver("%s; {link:{selector:\"amqp.correlation_id = 'b'\"}}" % snd.target)
        rcv_3 = self.ssn.receiver("%s; {link:{selector:\"amqp.priority = 1\"}}" % snd.target)

        for m in msgs: snd.send(m)

        msg = rcv_1.fetch(0)
        assert msg.content == 'c', msg
        self.ssn.acknowledge(msg)

        msg = rcv_2.fetch(0)
        assert msg.content == 'b', msg
        self.ssn.acknowledge(msg)

        msg = rcv_3.fetch(0)
        assert msg.content == 'a', msg
        self.ssn.acknowledge(msg)

        rcv_4 = self.ssn.receiver(snd.target)
        msg = rcv_4.fetch(0)
        assert msg.content == 'd'
        self.ssn.acknowledge(msg)

    def check_selected(self,node, selector, expected_content):
        rcv = self.ssn.receiver("%s; {mode:browse, link:{selector:\"%s\"}}" % (node, selector))
        msg = rcv.fetch(0)
        assert msg.content == expected_content, msg
        rcv.close()

    def test_jms_header_names(self):
        """
        The new AMQP 1.0 based JMS client uses these rather than the special names above
        """
        msgs = [Message(content=i, id=i, correlation_id=i, subject=i, priority=p+1, reply_to=i, properties={'x-amqp-to':i}) for p, i in enumerate(['a', 'b', 'c', 'd'])]

        snd = self.ssn.sender("#")
        for m in msgs: snd.send(m)

        self.check_selected(snd.target, "JMSMessageID = 'a'", 'a')
        self.check_selected(snd.target, "JMSCorrelationID = 'b'", 'b')
        self.check_selected(snd.target, "JMSPriority = 3", 'c')
        self.check_selected(snd.target, "JMSDestination = 'a'", 'a')
        self.check_selected(snd.target, "JMSReplyTo = 'b'", 'b')
        self.check_selected(snd.target, "JMSType = 'c'", 'c')
