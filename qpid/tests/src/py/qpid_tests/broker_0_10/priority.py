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
from qpid.compat import set
import math

class PriorityTests (Base):
    """
    Test prioritised messaging
    """ 

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def prioritised_delivery(self, priorities, levels=10, key="x-qpid-priorities"):
        """
        Test that message on a queue are delivered in priority order.
        """
        msgs = [Message(content=str(uuid4()), priority = p) for p in priorities]

        snd = self.ssn.sender("priority-queue; {create: sender, delete: receiver, node: {x-declare:{arguments:{'%s':%s}}}}" % (key, levels),
                              durable=self.durable())
        for m in msgs: snd.send(m)

        rcv = self.ssn.receiver(snd.target)
        for expected in sorted_(msgs, key=lambda m: priority_level(m.priority,levels), reverse=True):
            msg = rcv.fetch(0)
            #print "expected priority %s got %s" % (expected.priority, msg.priority)
            assert msg.content == expected.content
            self.ssn.acknowledge(msg)

    def fairshare_delivery(self, priorities, default_limit=5, limits=None, levels=10, level_key="x-qpid-priorities", fairshare_key="x-qpid-fairshare"):
        msgs = [Message(content=str(uuid4()), priority = p) for p in priorities]

        limit_policy = "'%s':%s" % (fairshare_key, default_limit)
        if limits:
            for k, v in limits.items():
                limit_policy += ", '%s-%s':%s" % (fairshare_key, k, v)

        snd = self.ssn.sender("priority-queue; {create: sender, delete: receiver, node: {x-declare:{arguments:{'%s':%s, %s}}}}"
                              % (level_key, levels, limit_policy),
                              durable=self.durable())
        for m in msgs: snd.send(m)

        rcv = self.ssn.receiver(snd.target)
        if limits:
            limit_function = lambda x : limits.get(x, 0)
        else:
            limit_function = lambda x : default_limit
        for expected in fairshare(sorted_(msgs, key=lambda m: priority_level(m.priority,levels), reverse=True), 
                                  limit_function, levels):
            msg = rcv.fetch(0)
            #print "expected priority %s got %s" % (expected.priority, msg.priority)
            assert msg.priority == expected.priority
            assert msg.content == expected.content
            self.ssn.acknowledge(msg)

    def test_prioritised_delivery_1(self):
        self.prioritised_delivery(priorities = [8,9,5,1,2,2,3,4,9,7,8,9,9,2], levels = 10)

    def test_prioritised_delivery_with_alias(self):
        self.prioritised_delivery(priorities = [8,9,5,1,2,2,3,4,15,7,8,10,10,2], levels = 10, key="qpid.priorities")

    def test_prioritised_delivery_2(self):
        self.prioritised_delivery(priorities = [8,9,5,1,2,2,3,4,9,7,8,9,9,2], levels = 5)

    def test_fairshare_1(self):
        self.fairshare_delivery(priorities = [4,5,3,6,10,10,2,10,2,10,10,1,10,10,10,3,3,3,10,10,3,10,3,10,10,10,10,10,10,2,3])

    def test_fairshare_with_alias(self):
        self.fairshare_delivery(priorities = [4,5,3,6,10,10,2,10,2,10,10,1,10,10,10,3,3,3,10,10,2,3], level_key="qpid.priorities", fairshare_key="qpid.fairshare")

    def test_fairshare_2(self):
        self.fairshare_delivery(priorities = [10 for i in range(30)])

    def test_fairshare_3(self):
        self.fairshare_delivery(priorities = [4,5,3,7,8,8,2,8,2,8,8,16,6,6,6,6,6,6,8,3,5,8,3,5,5,3,3,8,8,3,7,3,7,7,7,8,8,8,2,3], limits={7:0,6:4,5:3,4:2,3:2,2:2,1:2}, levels=8)

    def test_browsing(self):
        priorities = [4,5,3,6,0,1,2,8,2,0,2,1,6,0,1,3,3,3,8,1,3,0,3,7,9,0,1,9,0,2,3]
        msgs = [Message(content=str(uuid4()), priority = p) for p in priorities]
        snd = self.ssn.sender("priority-queue; {create: sender, node: {x-declare:{arguments:{x-qpid-priorities:10}}}}",
                              durable=self.durable())
        for m in msgs: snd.send(m)

        rcv = self.ssn.receiver("priority-queue; {mode: browse, delete: receiver}")
        received = []
        try:
            while True: received.append(rcv.fetch(0))
        except Empty: None
        #check all messages on the queue were received by the browser; don't relay on any specific ordering at present
        assert set([m.content for m in msgs]) == set([m.content for m in received])

    def ring_queue_check(self, msgs, count=10):
        """
        Ensure that a ring queue removes lowest priority messages first.
        """
        snd = self.ssn.sender(address("priority-ring-queue", arguments="x-qpid-priorities:10, 'qpid.policy_type':ring, 'qpid.max_count':%s" % count),
                              durable=self.durable())
        for m in msgs: snd.send(m)

        rcv = self.ssn.receiver(snd.target)
        received = []
        try:
            while True: received.append(rcv.fetch(0))
        except Empty: None

        expected = sorted_(msgs, key=lambda x: priority_level(x.priority,10))[len(msgs)-count:]
        expected = sorted_(expected, key=lambda x: priority_level(x.priority,10), reverse=True)
        #print "sent %s; expected %s; got %s" % ([m.priority for m in msgs], [m.priority for m in expected], [m.priority for m in received])
        #print "sent %s; expected %s; got %s" % ([m.content for m in msgs], [m.content for m in expected], [m.content for m in received])
        assert [m.content for m in expected] == [m.content for m in received]

    def test_ring_queue_1(self):
        priorities = [4,5,3,6,9,9,2,9,2,9,9,1,9,9,9,3,3,3,9,9,3,9,3,9,9,9,9,9,9,2,3]
        seq = content("msg")
        self.ring_queue_check([Message(content=seq.next(), priority = p) for p in priorities])

    def test_ring_queue_2(self):
        priorities = [9,0,2,3,6,3,4,2,9,2,9,9,1,9,4,7,1,1,3,9,7,3,9,3,9,1,5,1,9,7,2,3,0,9]
        seq = content("msg")
        self.ring_queue_check([Message(content=seq.next(), priority = p) for p in priorities])

    def test_ring_queue_3(self):
        #test case given for QPID-3866
        priorities = [8,9,5,1,2,2,3,4,9,7,8,9,9,2]
        seq = content("msg")
        self.ring_queue_check([Message(content=seq.next(), priority = p) for p in priorities], 5)

    def test_ring_queue_4(self):
        priorities = [9,0,2,3,6,3,4,2,9,2,9,3,1,9,4,7,1,1,3,2,7,3,9,3,6,1,5,1,9,7,2,3,0,2]
        seq = content("msg")
        self.ring_queue_check([Message(content=seq.next(), priority = p) for p in priorities])

    def test_requeue(self):
        priorities = [4,5,3,6,9,9,2,9,2,9,9,1,9,9,9,3,3,3,9,9,3,9,3,9,9,9,9,9,9,2,3]
        msgs = [Message(content=str(uuid4()), priority = p) for p in priorities]

        snd = self.ssn.sender("priority-queue; {create: sender, delete: receiver, node: {x-declare:{arguments:{x-qpid-priorities:10}}}}",
                              durable=self.durable())
        #want to have some messages requeued so enable prefetch on a dummy receiver
        other = self.conn.session()
        dummy = other.receiver("priority-queue")
        dummy.capacity = 10

        for m in msgs: snd.send(m)

        #fetch some with dummy receiver on which prefetch is also enabled
        for i in range(5):
            msg = dummy.fetch(0)
        #close session without acknowledgements to requeue messages
        other.close()

        #now test delivery works as expected after that
        rcv = self.ssn.receiver(snd.target)
        for expected in sorted_(msgs, key=lambda m: priority_level(m.priority,10), reverse=True):
            msg = rcv.fetch(0)
            #print "expected priority %s got %s" % (expected.priority, msg.priority)
            #print "expected content %s got %s" % (expected.content, msg.content)
            assert msg.content == expected.content
            self.ssn.acknowledge(msg)

def content(base, counter=1):
    while True:
        yield "%s-%s" % (base, counter)        
        counter += 1

def address(name, create_policy="sender", delete_policy="receiver", arguments=None):
    if arguments: node = "node: {x-declare:{arguments:{%s}}}" % arguments
    else: node = "node: {}"
    return "%s; {create: %s, delete: %s, %s}" % (name, create_policy, delete_policy, node)

def fairshare(msgs, limit, levels):
    """
    Generator to return prioritised messages in expected order for a given fairshare limit
    """
    count = 0
    last_priority = None
    postponed = []
    while msgs or postponed:
        if not msgs: 
            msgs = postponed
            count = 0
            last_priority = None
            postponed = []            
        msg = msgs.pop(0)
        if last_priority and priority_level(msg.priority, levels) == last_priority:
            count += 1
        else:
            last_priority = priority_level(msg.priority, levels)
            count = 1
        l = limit(last_priority)
        if (l and count > l):
            postponed.append(msg)
        else:
            yield msg         
    return

def effective_priority(value, levels):
    """
    Method to determine effective priority given a distinct number of
    levels supported. Returns the lowest priority value that is of
    equivalent priority to the value passed in.
    """
    if value <= 5-math.ceil(levels/2.0): return 0
    if value >= 4+math.floor(levels/2.0): return 4+math.floor(levels/2.0)
    return value

def priority_level(value, levels):
    """
    Method to determine which of a distinct number of priority levels
    a given value falls into.
    """
    offset = 5-math.ceil(levels/2.0)
    return min(max(value - offset, 0), levels-1)

def sorted_(msgs, key=None, reverse=False):
    """
    Workaround lack of sorted builtin function in python 2.3 and lack
    of keyword arguments to list.sort()
    """
    temp = [m for m in msgs]
    temp.sort(key_to_cmp(key, reverse=reverse))
    return temp

def key_to_cmp(key, reverse=False):
    if key:
        if reverse: return lambda a, b: cmp(key(b), key(a))
        else: return lambda a, b: cmp(key(a), key(b))  
    else:
        return None
