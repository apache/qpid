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
from qpid.framer import *

PORT = 1234

class FramerTest(TestCase):

  def setUp(self):
    self.running = True
    started = Event()
    def run():
      for s in listen("0.0.0.0", PORT, lambda: self.running, lambda: started.set()):
        conn = Framer(s)
        try:
          conn.write_header(*conn.read_header()[-2:])
          while True:
            frame = conn.read_frame()
            conn.write_frame(frame)
            conn.flush()
        except Closed:
          pass

    self.server = Thread(target=run)
    self.server.setDaemon(True)
    self.server.start()

    started.wait(3)
    assert started.isSet()

  def tearDown(self):
    self.running = False
    self.server.join(3)

  def test(self):
    c = Framer(connect("0.0.0.0", PORT))

    c.write_header(0, 10)
    assert c.read_header() == ("AMQP", 1, 1, 0, 10)

    c.write_frame(Frame(FIRST_FRM, 1, 2, 3, "THIS"))
    c.write_frame(Frame(0, 1, 2, 3, "IS"))
    c.write_frame(Frame(0, 1, 2, 3, "A"))
    c.write_frame(Frame(LAST_FRM, 1, 2, 3, "TEST"))
    c.flush()

    f = c.read_frame()
    assert f.flags & FIRST_FRM
    assert not (f.flags & LAST_FRM)
    assert f.type == 1
    assert f.track == 2
    assert f.channel == 3
    assert f.payload == "THIS"

    f = c.read_frame()
    assert f.flags == 0
    assert f.type == 1
    assert f.track == 2
    assert f.channel == 3
    assert f.payload == "IS"

    f = c.read_frame()
    assert f.flags == 0
    assert f.type == 1
    assert f.track == 2
    assert f.channel == 3
    assert f.payload == "A"

    f = c.read_frame()
    assert f.flags & LAST_FRM
    assert not (f.flags & FIRST_FRM)
    assert f.type == 1
    assert f.track == 2
    assert f.channel == 3
    assert f.payload == "TEST"
