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
from qpid.assembler import *

PORT = 1234

class AssemblerTest(TestCase):

  def setUp(self):
    started = Event()
    self.running = True

    def run():
      running = True
      for s in listen("0.0.0.0", PORT, lambda: self.running, lambda: started.set()):
        asm = Assembler(s)
        try:
          asm.write_header(*asm.read_header()[-2:])
          while True:
            seg = asm.read_segment()
            asm.write_segment(seg)
        except Closed:
          pass

    self.server = Thread(target=run)
    self.server.setDaemon(True)
    self.server.start()

    started.wait(3)
    assert started.isSet()

  def tearDown(self):
    self.running = False
    self.server.join()

  def test(self):
    asm = Assembler(connect("0.0.0.0", PORT), max_payload = 1)
    asm.write_header(0, 10)
    asm.write_segment(Segment(True, False, 1, 2, 3, "TEST"))
    asm.write_segment(Segment(False, True, 1, 2, 3, "ING"))

    assert asm.read_header() == ("AMQP", 1, 1, 0, 10)

    seg = asm.read_segment()
    assert seg.first == True
    assert seg.last == False
    assert seg.type == 1
    assert seg.track == 2
    assert seg.channel == 3
    assert seg.payload == "TEST"

    seg = asm.read_segment()
    assert seg.first == False
    assert seg.last == True
    assert seg.type == 1
    assert seg.track == 2
    assert seg.channel == 3
    assert seg.payload == "ING"
