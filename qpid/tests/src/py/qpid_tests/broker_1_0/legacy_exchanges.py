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

class LegacyExchangeTests (VersionTest):
    """
    Tests for the legacy (i.e. pre 1.0) AMQP exchanges and the filters
    defined for them and registered for AMQP 1.0.
    """
    def test_fanout(self):
        msgs = [Message(content=s, subject = s) for s in ['a','b','c','d']]

        snd = self.ssn.sender("amq.fanout")
        rcv = self.ssn.receiver("amq.fanout")

        for m in msgs: snd.send(m)

        for expected in msgs:
            msg = rcv.fetch(0)
            assert msg.content == expected.content
            self.ssn.acknowledge(msg)
        rcv.close()

    def test_direct(self):
        msgs = [Message(content=c, subject=s) for s, c in [('a', 'one'), ('b', 'two'),('a', 'three'),('b', 'four')]]

        snd = self.ssn.sender("amq.direct")
        rcv_a = self.ssn.receiver("amq.direct/a")
        rcv_b = self.ssn.receiver("amq.direct/b")

        for m in msgs: snd.send(m)

        for expected in ['one', 'three']:
            msg = rcv_a.fetch(0)
            assert msg.content == expected, (msg, expected)
            self.ssn.acknowledge(msg)

        for expected in ['two', 'four']:
            msg = rcv_b.fetch(0)
            assert msg.content == expected
            self.ssn.acknowledge(msg), (msg, expected)

    def test_topic(self):
        msgs = [Message(content=s, subject=s) for s in ['red.dog', 'black.cat', 'red.squirrel', 'grey.squirrel']]

        snd = self.ssn.sender("amq.topic")
        rcv_a = self.ssn.receiver("amq.topic/red.*")
        rcv_b = self.ssn.receiver("amq.topic/*.squirrel")

        for m in msgs: snd.send(m)

        for expected in ['red.dog', 'red.squirrel']:
            msg = rcv_a.fetch(0)
            assert msg.content == expected, (msg, expected)
            self.ssn.acknowledge(msg)

        for expected in ['red.squirrel', 'grey.squirrel']:
            msg = rcv_b.fetch(0)
            assert msg.content == expected
            self.ssn.acknowledge(msg), (msg, expected)

    def test_headers(self):
        msgs = [Message(content="%s.%s" % (colour, creature), properties={'creature':creature,'colour':colour}) for colour, creature in [('red','dog'), ('black', 'cat'), ('red', 'squirrel'), ('grey', 'squirrel')]]

        snd = self.ssn.sender("amq.match")
        rcv_a = self.ssn.receiver("amq.match; {link:{filter:{descriptor:'apache.org:legacy-amqp-headers-binding:map',name:'red-things',value:{'colour':'red','x-match':'all'}}}}")
        rcv_b = self.ssn.receiver("amq.match; {link:{filter:{descriptor:'apache.org:legacy-amqp-headers-binding:map',name:'cats-and-squirrels',value:{'creature':'squirrel','colour':'black','x-match':'any'}}}}")
        for m in msgs: snd.send(m)

        for expected in ['red.dog', 'red.squirrel']:
            msg = rcv_a.fetch(0)
            assert msg.content == expected, (msg, expected)
            self.ssn.acknowledge(msg)

        for expected in ['black.cat', 'red.squirrel', 'grey.squirrel']:
            msg = rcv_b.fetch(0)
            assert msg.content == expected
            self.ssn.acknowledge(msg), (msg, expected)
