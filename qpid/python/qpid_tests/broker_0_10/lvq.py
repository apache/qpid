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
from qpid.tests.messaging import Base
import math
import random

class LVQTests (Base):
    """
    Test last value queue behaviour
    """

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def test_simple(self):
        snd = self.ssn.sender("lvq; {create: sender, delete: sender, node: {x-declare:{arguments:{'qpid.last_value_queue_key':lvq-key}}}}",
                              durable=self.durable())
        snd.send(create_message("a", "a-1"))
        snd.send(create_message("b", "b-1"))
        snd.send(create_message("a", "a-2"))
        snd.send(create_message("a", "a-3"))
        snd.send(create_message("c", "c-1"))
        snd.send(create_message("c", "c-2"))

        rcv = self.ssn.receiver("lvq; {mode: browse}")
        assert fetch_all(rcv) == ["b-1", "a-3", "c-2"]

        snd.send(create_message("b", "b-2"))
        assert fetch_all(rcv) == ["b-2"]

        snd.send(create_message("c", "c-3"))
        snd.send(create_message("d", "d-1"))
        assert fetch_all(rcv) == ["c-3", "d-1"]

        snd.send(create_message("b", "b-3"))
        assert fetch_all(rcv) == ["b-3"]

        rcv.close()
        rcv = self.ssn.receiver("lvq; {mode: browse}")
        assert (fetch_all(rcv) == ["a-3", "c-3", "d-1", "b-3"])

    def check_ring_lvq(self, ring_size, keys, message_count):
        address = "lvq; {create: sender, delete: sender, node: {x-declare:{arguments:{'qpid.last_value_queue_key':lvq-key,'qpid.policy_type':'ring','qpid.max_count':%i}}}}" % ring_size
        snd = self.ssn.sender(address, durable=self.durable())
        counters = {}
        for k in keys:
            counters[k] = 0
        messages = []
        for i in range(message_count):
            k = random.choice(keys)
            counters[k] += 1
            messages.append(create_message(k, "%s-%i" % (k, counters[k])))
        # make sure we have sent at least one message for every key
        for k, v in counters.iteritems():
            if v == 0:
                counters[k] += 1
                messages.append(create_message(k, "%s-%i" % (k, counters[k])))

        for m in messages:
            snd.send(m)

        rcv = self.ssn.receiver("lvq; {mode: browse}")
        retrieved = fetch_all_as_tuples(rcv)
        print [v for k, v in retrieved]

        for k, v in retrieved:
            assert v == "%s-%i" % (k, counters[k])
        assert len(retrieved) <= ring_size

    def test_ring_lvq1(self):
        self.check_ring_lvq(25, ["a","b","c","d"], 50)

    def test_ring_lvq2(self):
        self.check_ring_lvq(5, ["a","b","c","d","e","f","g"], 50)

    def test_ring_lvq3(self):
        self.check_ring_lvq(49, ["a"], 50)

def create_message(key, content):
    msg = Message(content=content, properties={"lvq-key":key})
    return msg

def fetch_all(rcv):
    content = []
    while True:
        try:
            content.append(rcv.fetch(0).content)
        except Empty:
            break
    return content

def fetch_all_as_tuples(rcv):
    content = []
    while True:
        try:
            m = rcv.fetch(0)
            k = m.properties["lvq-key"]
            content.append((k, m.content))
        except Empty:
            break
    return content
