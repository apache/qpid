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

    def do_threshold_test(self, key, value, messages):
        rcv = self.ssn.receiver("qmf.default.topic/agent.ind.event.org_apache_qpid_broker.queueThresholdExceeded.#")
        snd = self.ssn.sender("ttq; {create:always, node: {x-declare:{auto_delete:True,exclusive:True,arguments:{'%s':%s}}}}" % (key, value))
        size = 0
        count = 0
        for m in messages:
            snd.send(m)
            count = count + 1
            size = size + len(m.content)
        event = rcv.fetch()
        schema = event.content[0]["_schema_id"]
        assert schema["_class_name"] == "queueThresholdExceeded"
        values = event.content[0]["_values"]
        assert values["qName"] == "ttq"
        assert values["msgDepth"] == count, "msgDepth %s, expected %s" % (values["msgDepth"], count)
        assert values["byteDepth"] == size, "byteDepth %s, expected %s" % (values["byteDepth"], size)

    def test_alert_count(self):
        self.do_threshold_test("qpid.alert_count", 5, [Message("msg-%s" % i) for i in range(5)])

    def test_alert_size(self):
        self.do_threshold_test("qpid.alert_size", 25, [Message("msg-%s" % i) for i in range(5)])

    def test_alert_count_alias(self):
        self.do_threshold_test("x-qpid-maximum-message-count", 10, [Message("msg-%s" % i) for i in range(10)])

    def test_alert_size_alias(self):
        self.do_threshold_test("x-qpid-maximum-message-size", 15, [Message("msg-%s" % i) for i in range(3)])

    def test_alert_on_alert_queue(self):
        rcv = self.ssn.receiver("qmf.default.topic/agent.ind.event.org_apache_qpid_broker.queueThresholdExceeded.#; {link:{x-declare:{arguments:{'qpid.alert_count':1}}}}")
        rcvQMFv1 = self.ssn.receiver("qpid.management/console.event.#; {link:{x-declare:{arguments:{'qpid.alert_count':1}}}}")
        snd = self.ssn.sender("ttq; {create:always, node: {x-declare:{auto_delete:True,exclusive:True,arguments:{'qpid.alert_count':1}}}}")
        snd.send(Message("my-message"))
        queues = []
        for i in range(2):
            event = rcv.fetch()
            schema = event.content[0]["_schema_id"]
            assert schema["_class_name"] == "queueThresholdExceeded"
            values = event.content[0]["_values"]
            queues.append(values["qName"])
        assert "ttq" in queues, "expected event for ttq (%s)" % (queues)

