#!/usr/bin/env python
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

import errno, os, time
from brokertest import *
from qpid import compat, session
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import Message, uuid4
from qpid.queue import Empty

class StoreTests(BrokerTest):

  XA_RBROLLBACK = 1
  XA_RBTIMEOUT = 2
  XA_OK = 0
  tx_counter = 0

  def configure(self, config):
    self.config = config
    self.defines = self.config.defines
    BrokerTest.configure(self, config)

  def setup_connection(self):
    socket = connect(self._broker.host(), self._broker.port())
    return Connection(sock=socket)

  def setup_session(self):
    self.conn.start()
    return self.conn.session(str(uuid4()))

  def start_session(self):
    self.conn = self.setup_connection()
    self.ssn = self.setup_session()

  def setUp(self):
    BrokerTest.setUp(self)
    self._broker = self.broker()
    self.start_session()

  def cycle_broker(self):
    # tearDown resets working dir; change it back after.
    d = os.getcwd()
    BrokerTest.tearDown(self)
    os.chdir(d)
    self._broker = None
    self._broker = self.broker()
    self.conn = self.setup_connection()
    self.ssn = self.setup_session()

  def xid(self, txid):
    StoreTests.tx_counter += 1
    branchqual = "v%s" % StoreTests.tx_counter
    return self.ssn.xid(format=0, global_id=txid, branch_id=branchqual)

  def testDurableExchange(self):
    try:
      self.ssn.exchange_delete(exchange="DE1")
    except:
      # restart the session busted from the exception
      self.start_session()

    self.ssn.exchange_declare(exchange="DE1", type="direct", durable=True)
    response = self.ssn.exchange_query(name="DE1")
    self.assert_(response.durable)
    self.assert_(not response.not_found)

    # Cycle the broker and make sure the exchange recovers
    self.cycle_broker()
    response = self.ssn.exchange_query(name="DE1")
    self.assert_(response.durable)
    self.assert_(not response.not_found)

    self.ssn.exchange_delete(exchange="DE1")

  def testDurableQueues(self):
    try:
      self.ssn.queue_delete(queue="DQ1")
    except:
      self.start_session()

    self.ssn.queue_declare(queue="DQ1", durable=True)
    response = self.ssn.queue_query(queue="DQ1")
    self.assertEqual("DQ1", response.queue)
    self.assert_(response.durable)

    # Cycle the broker and make sure the queue recovers
    self.cycle_broker()
    response = self.ssn.queue_query(queue="DQ1")
    self.assertEqual("DQ1", response.queue)
    self.assert_(response.durable)

    self.ssn.queue_delete(queue="DQ1")

  def testDurableBindings(self):
    try:
      self.ssn.exchange_unbind(queue="DB_Q1", exchange="DB_E1", binding_key="K1")
    except:
      self.start_session()
    try:
      self.ssn.exchange_delete(exchange="DB_E1")
    except:
      self.start_session()
    try:
      self.ssn.queue_delete(queue="DB_Q1")
    except:
      self.start_session()

    self.ssn.queue_declare(queue="DB_Q1", durable=True)
    self.ssn.exchange_declare(exchange="DB_E1", type="direct", durable=True)
    self.ssn.exchange_bind(exchange="DB_E1", queue="DB_Q1", binding_key="K1")

    # Queue up 2 messages, one with non-zero body, one with zero-length.
    # 2 = delivery_mode.persistent
    dp = self.ssn.delivery_properties(routing_key="DB_Q1", delivery_mode=2)
    self.ssn.message_transfer(message=Message(dp, "normal message"))
    self.ssn.message_transfer(message=Message(dp, ""))

    # Cycle the broker and make sure the binding recovers
    self.cycle_broker()
    response = self.ssn.exchange_bound(exchange="DB_E1", queue="DB_Q1", binding_key="K1")
    self.assert_(not response.exchange_not_found)
    self.assert_(not response.queue_not_found)
    self.assert_(not response.queue_not_matched)
    self.assert_(not response.key_not_matched)

    # Are the messages still there?
    self.ssn.message_subscribe(destination="msgs", queue="DB_Q1", accept_mode=1, acquire_mode=0)
    self.ssn.message_flow(unit = 1, value = 0xFFFFFFFFL, destination = "msgs")
    self.ssn.message_flow(unit = 0, value = 10, destination = "msgs")
    message_arrivals = self.ssn.incoming("msgs")
    try:
      message_arrivals.get(timeout=1)
      message_arrivals.get(timeout=1)
    except Empty:
      assert False, 'Durable message(s) not recovered'

    self.ssn.exchange_unbind(queue="DB_Q1", exchange="DB_E1", binding_key="K1")
    self.ssn.exchange_delete(exchange="DB_E1")
    self.ssn.queue_delete(queue="DB_Q1")

  def testDtxRecoverPrepared(self):
    try:
      self.ssn.exchange_unbind(queue="Dtx_Q", exchange="Dtx_E", binding_key="Dtx")
    except:
      self.start_session()
    try:
      self.ssn.exchange_delete(exchange="Dtx_E")
    except:
      self.start_session()
    try:
      self.ssn.queue_delete(queue="Dtx_Q")
    except:
      self.start_session()

    self.ssn.queue_declare(queue="Dtx_Q", auto_delete=False, durable=True)
    self.ssn.exchange_declare(exchange="Dtx_E", type="direct", durable=True)
    self.ssn.exchange_bind(exchange="Dtx_E", queue="Dtx_Q", binding_key="Dtx")
    txid = self.xid("DtxRecoverPrepared")
    self.ssn.dtx_select()
    self.ssn.dtx_start(xid=txid)
    # 2 = delivery_mode.persistent
    dp = self.ssn.delivery_properties(routing_key="Dtx_Q", delivery_mode=2)
    self.ssn.message_transfer(message=Message(dp, "transactional message"))
    self.ssn.dtx_end(xid=txid)
    self.assertEqual(self.XA_OK, self.ssn.dtx_prepare(xid=txid).status)
    # Cycle the broker and make sure the xid is there, the message is not
    # queued.
    self.cycle_broker()
    # The txid should be recovered and in doubt
    xids = self.ssn.dtx_recover().in_doubt
    xid_matched = False
    for x in xids:
      self.assertEqual(txid.format, x.format)
      self.assertEqual(txid.global_id, x.global_id)
      self.assertEqual(txid.branch_id, x.branch_id)
      xid_matched = True
    self.assert_(xid_matched)
    self.ssn.message_subscribe(destination="dtx_msgs", queue="Dtx_Q", accept_mode=1, acquire_mode=0)
    self.ssn.message_flow(unit = 1, value = 0xFFFFFFFFL, destination = "dtx_msgs")
    self.ssn.message_flow(unit = 0, value = 10, destination = "dtx_msgs")
    message_arrivals = self.ssn.incoming("dtx_msgs")
    try:
      message_arrivals.get(timeout=1)
      assert False, 'Message present in queue before commit'
    except Empty: pass
    self.ssn.dtx_select()
    self.assertEqual(self.XA_OK, self.ssn.dtx_commit(xid=txid, one_phase=False).status)
    try:
      msg = message_arrivals.get(timeout=1)
      self.assertEqual("transactional message", msg.body)
    except Empty:
      assert False, 'Message should be present after dtx commit but is not'

    self.ssn.exchange_unbind(queue="Dtx_Q", exchange="Dtx_E", binding_key="Dtx")
    self.ssn.exchange_delete(exchange="Dtx_E")
    self.ssn.queue_delete(queue="Dtx_Q")
