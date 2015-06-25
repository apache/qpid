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

from qpid.messaging import *
from qpid.tests.messaging import Base
import math

class ThresholdTests (Base):
    """
    Test queue threshold events are sent and received correctly
    """ 

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def enqueue(self, snd, count):
        for i in range(count):
            m = Message("msg-%d" % i)
            snd.send(m)

    def dequeue(self, rcv, count):
        for i in range(count):
            m = rcv.fetch(timeout=1)
        self.ssn.acknowledge()

    def check_events(self, rcv, count):
        for i in range(count):
            m = rcv.fetch(timeout=0)
        try:
            m = rcv.fetch(timeout=0)
            assert False
        except:
            pass

    def do_threshold_test(self, args, messages, drain_count, bw_compat=None):
        astr = ''
        first = True
        for key, value in args.items():
            if first:
                first = None
            else:
                astr += ','
            astr += "'%s':%s" % (key, value)
        rcvUp = self.ssn.receiver("qmf.default.topic/agent.ind.event.org_apache_qpid_broker.queueThresholdCrossedUpward.#")
        rcvDn = self.ssn.receiver("qmf.default.topic/agent.ind.event.org_apache_qpid_broker.queueThresholdCrossedDownward.#")
        rcvBw = self.ssn.receiver("qmf.default.topic/agent.ind.event.org_apache_qpid_broker.queueThresholdExceeded.#")
        snd = self.ssn.sender("ttq; {create:always, node: {x-declare:{auto_delete:True,exclusive:True,arguments:{%s}}}}" % astr)
        rcv = self.ssn.receiver("ttq")
        overhead = 29 #additional bytes in broker's view of message size from headers etc
        size = 0
        count = 0
        for m in messages:
            snd.send(m)
            count = count + 1
            size = size + len(m.content) + overhead
        event = rcvUp.fetch(timeout=1)
        schema = event.content[0]["_schema_id"]
        assert schema["_class_name"] == "queueThresholdCrossedUpward"
        values = event.content[0]["_values"]
        assert values["qName"] == "ttq"
        assert values["msgDepth"] == count, "msgDepth %s, expected %s" % (values["msgDepth"], count)
        assert values["byteDepth"] == size, "byteDepth %s, expected %s" % (values["byteDepth"], size)
        if bw_compat:
            event = rcvBw.fetch(timeout=0)

        try:
            event = rcvUp.fetch(timeout=0)
            assert False
        except:
            pass

        if drain_count > 0:
            for i in range(drain_count):
                m = rcv.fetch(timeout=1)
                self.ssn.acknowledge()
                count -= 1
                size -= (len(m.content) + overhead)
            event = rcvDn.fetch(timeout=1)
            schema = event.content[0]["_schema_id"]
            assert schema["_class_name"] == "queueThresholdCrossedDownward"
            values = event.content[0]["_values"]
            assert values["qName"] == "ttq"
            assert values["msgDepth"] == count, "msgDepth %s, expected %s" % (values["msgDepth"], count)
            assert values["byteDepth"] == size, "byteDepth %s, expected %s" % (values["byteDepth"], size)
            try:
                event = rcvUp.fetch(timeout=0)
                assert False
            except:
                pass

    def test_alert_count(self):
        a = {'qpid.alert_count':5, 'qpid.alert_count_down':3}
        self.do_threshold_test(a, [Message("msg-%s" % i) for i in range(5)], 2)

    def test_alert_size(self):
        a = {'qpid.alert_size_up':150,'qpid.alert_size_down':120}
        self.do_threshold_test(a, [Message("msg-%s" % i) for i in range(5)], 2)

    def test_alert_count_alias(self):
        a = {'x-qpid-maximum-message-count':10}
        self.do_threshold_test(a, [Message("msg-%s" % i) for i in range(10)], 0, True)

    def test_alert_size_alias(self):
        a = {'x-qpid-maximum-message-size':100}
        self.do_threshold_test(a, [Message("msg-%s" % i) for i in range(3)], 0, True)

    def test_alert_on_alert_queue(self):
        rcv = self.ssn.receiver("qmf.default.topic/agent.ind.event.org_apache_qpid_broker.queueThresholdCrossedUpward.#; {link:{x-declare:{arguments:{'qpid.alert_count':1}}}}")
        snd = self.ssn.sender("ttq; {create:always, node: {x-declare:{auto_delete:True,exclusive:True,arguments:{'qpid.alert_count':1}}}}")
        snd.send(Message("my-message"))
        queues = []
        for i in range(2):
            event = rcv.fetch(timeout=1)
            schema = event.content[0]["_schema_id"]
            assert schema["_class_name"] == "queueThresholdCrossedUpward"
            values = event.content[0]["_values"]
            queues.append(values["qName"])
        assert "ttq" in queues, "expected event for ttq (%s)" % (queues)

    def test_hysteresis(self):
        astr  = "'qpid.alert_count_up':10,'qpid.alert_count_down':5"
        rcvUp = self.ssn.receiver("qmf.default.topic/agent.ind.event.org_apache_qpid_broker.queueThresholdCrossedUpward.#")
        rcvDn = self.ssn.receiver("qmf.default.topic/agent.ind.event.org_apache_qpid_broker.queueThresholdCrossedDownward.#")
        snd   = self.ssn.sender("thq; {create:always, node: {x-declare:{auto_delete:True,exclusive:True,arguments:{%s}}}}" % astr)
        rcv   = self.ssn.receiver("thq")

        rcvUp.capacity = 5
        rcvDn.capacity = 5
        rcv.capacity   = 5

        self.enqueue(snd, 8)         # depth = 8
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.dequeue(rcv, 6)         # depth = 2
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.enqueue(snd, 8)         # depth = 10
        self.check_events(rcvUp, 1)
        self.check_events(rcvDn, 0)

        self.dequeue(rcv, 1)         # depth = 9
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.enqueue(snd, 1)         # depth = 10
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.enqueue(snd, 10)        # depth = 20
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.dequeue(rcv, 5)         # depth = 15
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.dequeue(rcv, 12)        # depth = 3
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 1)

        self.dequeue(rcv, 1)         # depth = 2
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.enqueue(snd, 6)         # depth = 8
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.enqueue(snd, 6)         # depth = 14
        self.check_events(rcvUp, 1)
        self.check_events(rcvDn, 0)

        self.dequeue(rcv, 9)         # depth = 5
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 1)

        self.enqueue(snd, 1)         # depth = 6
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.dequeue(rcv, 1)         # depth = 5
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)

        self.dequeue(rcv, 5)         # depth = 0
        self.check_events(rcvUp, 0)
        self.check_events(rcvDn, 0)




