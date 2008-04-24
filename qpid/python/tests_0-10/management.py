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

from qpid.datatypes import Message, RangedSet
from qpid.testlib import TestBase010
from qpid.management import managementChannel, managementClient

class ManagementTest (TestBase010):
    """
    Tests for the management hooks
    """

    def test_broker_connectivity (self):
        """
        Call the "echo" method on the broker to verify it is alive and talking.
        """
        session = self.session
 
        mc  = managementClient (session.spec)
        mch = mc.addChannel (session)

        mc.syncWaitForStable (mch)
        brokers = mc.syncGetObjects (mch, "broker")
        self.assertEqual (len (brokers), 1)
        broker = brokers[0]
        args = {}
        body = "Echo Message Body"
        args["body"] = body

        for seq in range (1, 5):
            args["sequence"] = seq
            res = mc.syncCallMethod (mch, broker.id, broker.classKey, "echo", args)
            self.assertEqual (res.status,     0)
            self.assertEqual (res.statusText, "OK")
            self.assertEqual (res.sequence,   seq)
            self.assertEqual (res.body,       body)
        mc.removeChannel (mch)

    def test_system_object (self):
        session = self.session
 
        mc  = managementClient (session.spec)
        mch = mc.addChannel (session)

        mc.syncWaitForStable (mch)
        systems = mc.syncGetObjects (mch, "system")
        self.assertEqual (len (systems), 1)
        mc.removeChannel (mch)

    def test_standard_exchanges (self):
        session = self.session
 
        mc  = managementClient (session.spec)
        mch = mc.addChannel (session)

        mc.syncWaitForStable (mch)
        exchanges = mc.syncGetObjects (mch, "exchange")
        exchange = self.findExchange (exchanges, "")
        self.assertEqual (exchange.type, "direct")
        exchange = self.findExchange (exchanges, "amq.direct")
        self.assertEqual (exchange.type, "direct")
        exchange = self.findExchange (exchanges, "amq.topic")
        self.assertEqual (exchange.type, "topic")
        exchange = self.findExchange (exchanges, "amq.fanout")
        self.assertEqual (exchange.type, "fanout")
        exchange = self.findExchange (exchanges, "amq.match")
        self.assertEqual (exchange.type, "headers")
        exchange = self.findExchange (exchanges, "qpid.management")
        self.assertEqual (exchange.type, "topic")
        mc.removeChannel (mch)

    def findExchange (self, exchanges, name):
        for exchange in exchanges:
            if exchange.name == name:
                return exchange
        return None
