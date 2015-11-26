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
from mgmt_1 import Mgmt

class PoliciesTests (VersionTest):
    """
    Tests for node policies with qpidd
    """

    def do_simple_queue_test(self, pattern, name, properties={}, autodeleted=True):
        mgmt = self.create_connection("amqp0-10", True)
        agent = Mgmt(mgmt)
        agent.create('QueuePolicy', pattern, properties)
        try:
            snd = self.ssn.sender(name)
            msgs = [Message(content=s, subject = s) for s in ['a','b','c','d']]
            for m in msgs: snd.send(m)
            snd.close()

            for expected in msgs:
                rcv = self.ssn.receiver(name)
                msg = rcv.fetch(0)
                assert msg.content == expected.content, (msg.content, expected.content)
                self.ssn.acknowledge()
                rcv.close() #close after each message to ensure queue isn't deleted with messages in it
            self.ssn.close()
            self.conn.close()

            matched = [q for q in agent.list("Queue") if q['name'] == name]
            if autodeleted:
                # ensure that queue is no longer there (as empty and unused)
                assert len(matched) == 0, (matched)
            else:
                # ensure that queue is still there though empty and unused
                assert len(matched) == 1, (matched)
        finally:
            agent.delete('QueuePolicy', pattern)
            mgmt.close()

    def test_queue(self):
        self.do_simple_queue_test("queue-*", "queue-1")

    def test_queue_not_autodeleted(self):
        self.do_simple_queue_test("permanent-queue-*", "permanent-queue-1", {'auto-delete':False}, False)

    def test_queue_manual_delete(self):
        self.do_simple_queue_test("permanent-queue-*", "permanent-queue-1", {'qpid.lifetime-policy':'manual'}, False)

    def test_queue_delete_if_unused_and_empty(self):
        self.do_simple_queue_test("queue-*", "queue-1", {'qpid.lifetime-policy':'delete-if-unused-and-empty'}, True)

    def do_simple_topic_test(self, pattern, name, properties={}, autodeleted=True):
        mgmt = self.create_connection("amqp0-10", True)
        agent = Mgmt(mgmt)
        agent.create('TopicPolicy', pattern, properties)
        try:
            snd = self.ssn.sender(name)
            rcv1 = self.ssn.receiver(name)
            rcv2 = self.ssn.receiver(name)

            msgs = [Message(content=s, subject = s) for s in ['a','b','c','d']]
            for m in msgs: snd.send(m)

            for rcv in [rcv1, rcv2]:
                for expected in msgs:
                    msg = rcv.fetch(0)
                    assert msg.content == expected.content, (msg.content, expected.content)
            self.ssn.acknowledge()
            rcv1.close()
            rcv2.close()
            snd.close()

            matched = [e for e in agent.list("Exchange") if e['name'] == name]
            if autodeleted:
                # ensure that exchange is no longer there (as it is now unused)
                assert len(matched) == 0, (matched)
            else:
                # ensure that exchange has not been autodeleted in spite of being unused
                assert len(matched) == 1, (matched)
        finally:
            agent.delete('TopicPolicy', pattern)
            mgmt.close()

    def test_topic(self):
        self.do_simple_topic_test('fanout-*', 'fanout-1', {'exchange-type':'fanout'})

    def test_topic_not_autodelete(self):
        self.do_simple_topic_test('permanent-fanout-*', 'permanent-fanout-1', {'exchange-type':'fanout', 'auto-delete':False}, False)

    def test_topic_manual_delete(self):
        self.do_simple_topic_test('permanent-fanout-*', 'permanent-fanout-1', {'exchange-type':'fanout', 'qpid.lifetime-policy':'manual'}, False)

    def test_topic_delete_if_unused(self):
        self.do_simple_topic_test('fanout-*', 'fanout-1', {'exchange-type':'fanout', 'qpid.lifetime-policy':'delete-if-unused'}, True)

    def test_mgmt(self):
        mgmt = self.create_connection("amqp0-10", True)
        agent = Mgmt(mgmt)
        agent.create('QueuePolicy', 'queue-*')
        agent.create('QueuePolicy', 'alt.queue.*')
        agent.create('TopicPolicy', 'topic-*')
        try:
            queues = [q['name'] for q in agent.list("QueuePolicy")]
            topics = [t['name'] for t in agent.list("TopicPolicy")]
            assert 'queue-*' in queues, (queues)
            assert 'alt.queue.*' in queues, (queues)

            try:
                agent.delete('TopicPolicy', 'queue-*')
                assert False, ('Deletion of policy using wrong type should fail')
            except: None

        finally:
            agent.delete('QueuePolicy', 'queue-*')
            agent.delete('QueuePolicy', 'alt.queue.*')
            agent.delete('TopicPolicy', 'topic-*')
            mgmt.close()
