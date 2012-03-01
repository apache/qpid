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
import qmf.console
from time import sleep

#
# Broker tests using the new messaging API
#

class GeneralTests(Base):
    """
    Tests of the API and broker via the new API.
    """

    def assertEqual(self, left, right, text=None):
        if not left == right:
            print "assertEqual failure: %r != %r" % (left, right)
            if text:
                print "  %r" % text
            assert None

    def fail(self, text=None):
        if text:
            print "Fail: %r" % text
        assert None

    def setup_connection(self):
        return Connection.establish(self.broker, **self.connection_options())

    def setup_session(self):
        return self.conn.session()

    def test_qpid_3481_acquired_to_alt_exchange(self):
        """
        Verify that acquired messages are routed to the alternate when the queue is deleted.
        """
        sess1 = self.setup_session()
        sess2 = self.setup_session()

        tx = sess1.sender("amq.direct/key")
        rx_main = sess1.receiver("amq.direct/key;{link:{x-declare:{alternate-exchange:'amq.fanout'}}}")
        rx_alt  = sess2.receiver("amq.fanout")
        rx_alt.capacity = 10

        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")

        msg = rx_main.fetch()
        msg = rx_main.fetch()
        msg = rx_main.fetch()

        self.assertEqual(rx_alt.available(), 0, "No messages should have been routed to the alt_exchange")

        sess1.close()
        sleep(1)
        self.assertEqual(rx_alt.available(), 5, "All 5 messages should have been routed to the alt_exchange")

        sess2.close()

    def test_qpid_3481_acquired_to_alt_exchange_2_consumers(self):
        """
        Verify that acquired messages are routed to the alternate when the queue is deleted.
        """
        sess1 = self.setup_session()
        sess2 = self.setup_session()
        sess3 = self.setup_session()
        sess4 = self.setup_session()

        tx = sess1.sender("test_acquired;{create:always,delete:always,node:{x-declare:{alternate-exchange:'amq.fanout'}}}")
        rx_main1 = sess2.receiver("test_acquired")
        rx_main2 = sess3.receiver("test_acquired")
        rx_alt   = sess4.receiver("amq.fanout")
        rx_alt.capacity = 10

        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")
        tx.send("DATA")

        msg = rx_main1.fetch()
        msg = rx_main1.fetch()
        msg = rx_main1.fetch()

        self.assertEqual(rx_alt.available(), 0, "No messages should have been routed to the alt_exchange")

        # Close sess1; This will cause the queue to be deleted
        sess1.close()
        sleep(1)
        self.assertEqual(rx_alt.available(), 2, "2 of the messages should have been routed to the alt_exchange")

        # Close sess2; This will cause the acquired messages to be requeued and routed to the alternate
        sess2.close()
        for i in range(5):
            try:
                m = rx_alt.fetch(0)
            except:
                self.fail("failed to receive all 5 messages via alternate exchange")

        sess3.close()
        self.assertEqual(rx_alt.available(), 0, "No further messages should be received via the alternate exchange")

        sess4.close()
