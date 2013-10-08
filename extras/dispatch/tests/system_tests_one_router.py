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

import os
import time
import unittest
import subprocess
from proton import Messenger, Message, PENDING, ACCEPTED, REJECTED

class RouterTest(unittest.TestCase):

  def setUp(self):
      if 'CTEST_SOURCE_DIR' not in os.environ:
        raise Exception("Environment variable 'CTEST_SOURCE_DIR' not set")
      srcdir = os.environ['CTEST_SOURCE_DIR']
      self.router = subprocess.Popen(['../router/dispatch-router', '-c', '%s/onerouter.conf' % srcdir],
                                     stderr=subprocess.PIPE, stdout=subprocess.PIPE)
      time.sleep(1)

  def tearDown(self):
    self.router.terminate()
    self.router.wait()

  def flush(self, messenger):
    while messenger.work(0.1):
      pass

  def subscribe(self, messenger, address):
    messenger.subscribe(address)
    self.flush(messenger)


  def test_0_discard(self):
    addr = "amqp://0.0.0.0:20000/discard/1"
    M1 = Messenger()
    M1.timeout = 1.0
    M1.start()
    tm = Message()
    tm.address = addr
    for i in range(100):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send()
    M1.stop()


  def test_1_pre_settled(self):
    addr = "amqp://0.0.0.0:20000/pre_settled/1"
    M1 = Messenger()
    M2 = Messenger()

    M1.timeout = 1.0
    M2.timeout = 1.0

    M1.start()
    M2.start()
    self.subscribe(M2, addr)

    tm = Message()
    rm = Message()

    tm.address = addr
    for i in range(100):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send()

    for i in range(100):
      M2.recv(1)
      M2.get(rm)
      self.assertEqual(i, rm.body['number'])

    M1.stop()
    M2.stop()


  def test_2_multicast(self):
    addr = "amqp://0.0.0.0:20000/pre_settled/multicast/1"
    M1 = Messenger()
    M2 = Messenger()
    M3 = Messenger()
    M4 = Messenger()

    M1.timeout = 1.0
    M2.timeout = 1.0
    M3.timeout = 1.0
    M4.timeout = 1.0

    M1.start()
    M2.start()
    M3.start()
    M4.start()
    self.subscribe(M2, addr)
    self.subscribe(M3, addr)
    self.subscribe(M4, addr)

    tm = Message()
    rm = Message()

    tm.address = addr
    for i in range(100):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send()

    for i in range(100):
      M2.recv(1)
      M2.get(rm)
      self.assertEqual(i, rm.body['number'])

      M3.recv(1)
      M3.get(rm)
      self.assertEqual(i, rm.body['number'])

      M4.recv(1)
      M4.get(rm)
      self.assertEqual(i, rm.body['number'])

    M1.stop()
    M2.stop()
    M3.stop()
    M4.stop()


  def test_2a_multicast_unsettled(self):
    addr = "amqp://0.0.0.0:20000/pre_settled/multicast/1"
    M1 = Messenger()
    M2 = Messenger()
    M3 = Messenger()
    M4 = Messenger()

    M1.timeout = 1.0
    M2.timeout = 1.0
    M3.timeout = 1.0
    M4.timeout = 1.0

    M1.outgoing_window = 5
    M2.incoming_window = 5
    M3.incoming_window = 5
    M4.incoming_window = 5

    M1.start()
    M2.start()
    M3.start()
    M4.start()
    self.subscribe(M2, addr)
    self.subscribe(M3, addr)
    self.subscribe(M4, addr)

    tm = Message()
    rm = Message()

    tm.address = addr
    for i in range(2):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send(0)

    for i in range(2):
      M2.recv(1)
      trk = M2.get(rm)
      M2.accept(trk)
      M2.settle(trk)
      self.assertEqual(i, rm.body['number'])

      M3.recv(1)
      trk = M3.get(rm)
      M3.accept(trk)
      M3.settle(trk)
      self.assertEqual(i, rm.body['number'])

      M4.recv(1)
      trk = M4.get(rm)
      M4.accept(trk)
      M4.settle(trk)
      self.assertEqual(i, rm.body['number'])

    M1.stop()
    M2.stop()
    M3.stop()
    M4.stop()


  def test_3_propagated_disposition(self):
    addr = "amqp://0.0.0.0:20000/unsettled/1"
    M1 = Messenger()
    M2 = Messenger()

    M1.timeout = 1.0
    M2.timeout = 1.0
    M1.outgoing_window = 5
    M2.incoming_window = 5

    M1.start()
    M2.start()
    self.subscribe(M2, addr)

    tm = Message()
    rm = Message()

    tm.address = addr
    tm.body = {'number': 0}

    ##
    ## Test ACCEPT
    ##
    tx_tracker = M1.put(tm)
    M1.send(0)
    M2.recv(1)
    rx_tracker = M2.get(rm)
    self.assertEqual(0, rm.body['number'])
    self.assertEqual(PENDING, M1.status(tx_tracker))

    M2.accept(rx_tracker)
    M2.settle(rx_tracker)

    self.flush(M2)
    self.flush(M1)

    self.assertEqual(ACCEPTED, M1.status(tx_tracker))

    ##
    ## Test REJECT
    ##
    tx_tracker = M1.put(tm)
    M1.send(0)
    M2.recv(1)
    rx_tracker = M2.get(rm)
    self.assertEqual(0, rm.body['number'])
    self.assertEqual(PENDING, M1.status(tx_tracker))

    M2.reject(rx_tracker)
    M2.settle(rx_tracker)

    self.flush(M2)
    self.flush(M1)

    self.assertEqual(REJECTED, M1.status(tx_tracker))

    M1.stop()
    M2.stop()


  def test_4_unsettled_undeliverable(self):
    addr = "amqp://0.0.0.0:20000/unsettled_undeliverable/1"
    M1 = Messenger()

    M1.timeout = 1.0
    M1.outgoing_window = 5

    M1.start()
    tm = Message()
    tm.address = addr
    tm.body = {'number': 200}

    tx_tracker = M1.put(tm)
    M1.send(0)
    self.flush(M1)
    self.assertEqual(PENDING, M1.status(tx_tracker)) ## Is this right???

    M1.stop()


  def test_5_three_ack(self):
    addr = "amqp://0.0.0.0:20000/three_ack/1"
    M1 = Messenger()
    M2 = Messenger()

    M1.timeout = 1.0
    M2.timeout = 1.0
    M1.outgoing_window = 5
    M2.incoming_window = 5

    M1.start()
    M2.start()
    self.subscribe(M2, addr)

    tm = Message()
    rm = Message()

    tm.address = addr
    tm.body = {'number': 200}

    tx_tracker = M1.put(tm)
    M1.send(0)
    M2.recv(1)
    rx_tracker = M2.get(rm)
    self.assertEqual(200, rm.body['number'])
    self.assertEqual(PENDING, M1.status(tx_tracker))

    M2.accept(rx_tracker)

    self.flush(M2)
    self.flush(M1)

    self.assertEqual(ACCEPTED, M1.status(tx_tracker))

    M1.settle(tx_tracker)

    self.flush(M1)
    self.flush(M2)

    ##
    ## We need a way to verify on M2 (receiver) that the tracker has been
    ## settled on the M1 (sender).  [ See PROTON-395 ]
    ##

    M2.settle(rx_tracker)

    self.flush(M2)
    self.flush(M1)

    M1.stop()
    M2.stop()


#  def test_6_link_route_sender(self):
#    pass 

#  def test_7_link_route_receiver(self):
#    pass 


  def test_8_delivery_annotations(self):
    addr = "amqp://0.0.0.0:20000/da/1"
    M1 = Messenger()
    M2 = Messenger()

    M1.timeout = 1.0
    M2.timeout = 1.0

    M1.start()
    M2.start()
    self.subscribe(M2, addr)

    tm = Message()
    rm = Message()

    tm.address = addr


    ##
    ## No inbound delivery annotations
    ##
    for i in range(10):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send()

    for i in range(10):
      M2.recv(1)
      M2.get(rm)
      self.assertEqual(i, rm.body['number'])
      da = rm.instructions
      self.assertEqual(da.__class__, dict)
      self.assertEqual(da['qdx.ingress'], '_topo/area/Qpid.Dispatch.Router.A/')
      self.assertFalse('qdx.trace' in da)

    ##
    ## Pre-existing ingress
    ##
    tm.instructions = {'qdx.ingress': 'ingress-router'}
    for i in range(10):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send()

    for i in range(10):
      M2.recv(1)
      M2.get(rm)
      self.assertEqual(i, rm.body['number'])
      da = rm.instructions
      self.assertEqual(da.__class__, dict)
      self.assertEqual(da['qdx.ingress'], 'ingress-router')
      self.assertFalse('qdx.trace' in da)

    ##
    ## Invalid trace type
    ##
    tm.instructions = {'qdx.trace' : 45}
    for i in range(10):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send()

    for i in range(10):
      M2.recv(1)
      M2.get(rm)
      self.assertEqual(i, rm.body['number'])
      da = rm.instructions
      self.assertEqual(da.__class__, dict)
      self.assertEqual(da['qdx.ingress'], '_topo/area/Qpid.Dispatch.Router.A/')
      self.assertFalse('qdx.trace' in da)

    ##
    ## Empty trace
    ##
    tm.instructions = {'qdx.trace' : []}
    for i in range(10):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send()

    for i in range(10):
      M2.recv(1)
      M2.get(rm)
      self.assertEqual(i, rm.body['number'])
      da = rm.instructions
      self.assertEqual(da.__class__, dict)
      self.assertEqual(da['qdx.ingress'], '_topo/area/Qpid.Dispatch.Router.A/')
      self.assertEqual(da['qdx.trace'], ['_topo/area/Qpid.Dispatch.Router.A/'])

    ##
    ## Non-empty trace
    ##
    tm.instructions = {'qdx.trace' : ['first.hop']}
    for i in range(10):
      tm.body = {'number': i}
      M1.put(tm)
    M1.send()

    for i in range(10):
      M2.recv(1)
      M2.get(rm)
      self.assertEqual(i, rm.body['number'])
      da = rm.instructions
      self.assertEqual(da.__class__, dict)
      self.assertEqual(da['qdx.ingress'], '_topo/area/Qpid.Dispatch.Router.A/')
      self.assertEqual(da['qdx.trace'], ['first.hop', '_topo/area/Qpid.Dispatch.Router.A/'])

    M1.stop()
    M2.stop()


if __name__ == '__main__':
  unittest.main()
