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

from threading import *
from unittest import TestCase
from qpid.util import connect, listen
from qpid.connection import *
from qpid.datatypes import Message
from qpid.testlib import testrunner
from qpid.delegates import Server
from qpid.queue import Queue
from qpid.spec010 import load
from qpid.session import Delegate

PORT = 1234

class TestServer:

  def __init__(self, queue):
    self.queue = queue

  def connection(self, connection):
    return Server(connection, delegate=self.session)

  def session(self, session):
    session.auto_sync = False
    return TestSession(session, self.queue)

class TestSession(Delegate):

  def __init__(self, session, queue):
    self.session = session
    self.queue = queue

  def queue_query(self, qq):
    return qq._type.result.type.new((qq.queue,), {})

  def message_transfer(self, cmd, header, body):
    self.queue.put((cmd, header, body))

class ConnectionTest(TestCase):

  def setUp(self):
    self.spec = load(testrunner.get_spec_file("amqp.0-10.xml"))
    self.queue = Queue()
    self.running = True
    started = Event()

    def run():
      ts = TestServer(self.queue)
      for s in listen("0.0.0.0", PORT, lambda: self.running, lambda: started.set()):
        conn = Connection(s, self.spec, ts.connection)
        try:
          conn.start(5)
        except Closed:
          pass

    self.server = Thread(target=run)
    self.server.setDaemon(True)
    self.server.start()

    started.wait(3)

  def tearDown(self):
    self.running = False
    connect("0.0.0.0", PORT).close()
    self.server.join(3)

  def test(self):
    c = Connection(connect("0.0.0.0", PORT), self.spec)
    c.start(10)

    ssn1 = c.session("test1", timeout=10)
    ssn2 = c.session("test2", timeout=10)

    assert ssn1 == c.sessions["test1"]
    assert ssn2 == c.sessions["test2"]
    assert ssn1.channel != None
    assert ssn2.channel != None
    assert ssn1 in c.attached.values()
    assert ssn2 in c.attached.values()

    ssn1.close(5)

    assert ssn1.channel == None
    assert ssn1 not in c.attached.values()
    assert ssn2 in c.sessions.values()

    ssn2.close(5)

    assert ssn2.channel == None
    assert ssn2 not in c.attached.values()
    assert ssn2 not in c.sessions.values()

    ssn = c.session("session", timeout=10)

    assert ssn.channel != None
    assert ssn in c.sessions.values()

    destinations = ("one", "two", "three")

    for d in destinations:
      ssn.message_transfer(d)

    for d in destinations:
      cmd, header, body = self.queue.get(10)
      assert cmd.destination == d
      assert header == None
      assert body == None

    msg = Message("this is a test")
    ssn.message_transfer("four", message=msg)
    cmd, header, body = self.queue.get(10)
    assert cmd.destination == "four"
    assert header == None
    assert body == msg.body

    qq = ssn.queue_query("asdf")
    assert qq.queue == "asdf"
    c.close(5)
