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

import time
from qpid.messaging import *
from qpid.tests import Test

class Base(Test):

  def setup_connection(self):
    return None

  def setup_session(self):
    return None

  def setup_sender(self):
    return None

  def setup_receiver(self):
    return None

  def setup(self):
    self.test_id = uuid4()
    self.broker = self.config.broker
    try:
      self.conn = self.setup_connection()
    except ConnectError, e:
      raise Skipped(e)
    self.ssn = self.setup_session()
    self.snd = self.setup_sender()
    if self.snd is not None:
      self.snd.durable = self.durable()
    self.rcv = self.setup_receiver()

  def teardown(self):
    if self.conn is not None and self.conn.connected():
      self.conn.close()

  def content(self, base, count = None):
    if count is None:
      return "%s[%s]" % (base, self.test_id)
    else:
      return "%s[%s, %s]" % (base, count, self.test_id)

  def ping(self, ssn):
    PING_Q = 'ping-queue; {create: always, delete: always}'
    # send a message
    sender = ssn.sender(PING_Q, durable=self.durable())
    content = self.content("ping")
    sender.send(content)
    receiver = ssn.receiver(PING_Q)
    msg = receiver.fetch(0)
    ssn.acknowledge()
    assert msg.content == content, "expected %r, got %r" % (content, msg.content)

  def drain(self, rcv, limit=None, timeout=0, expected=None):
    contents = []
    try:
      while limit is None or len(contents) < limit:
        contents.append(rcv.fetch(timeout=timeout).content)
    except Empty:
      pass
    if expected is not None:
      assert expected == contents, "expected %s, got %s" % (expected, contents)
    return contents

  def assertEmpty(self, rcv):
    contents = self.drain(rcv)
    assert len(contents) == 0, "%s is supposed to be empty: %s" % (rcv, contents)

  def assertPending(self, rcv, expected):
    p = rcv.pending()
    assert p == expected, "expected %s, got %s" % (expected, p)

  def sleep(self):
    time.sleep(self.delay())

  def delay(self):
    return float(self.config.defines.get("delay", "2"))

  def get_bool(self, name):
    return self.config.defines.get(name, "false").lower() in ("true", "yes", "1")

  def durable(self):
    return self.get_bool("durable")

  def reconnect(self):
    return self.get_bool("reconnect")

import address, endpoints, message
