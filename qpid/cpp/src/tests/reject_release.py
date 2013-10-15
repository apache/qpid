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

from qpid.tests.messaging.implementation import *
from qpid.tests.messaging import VersionTest

class RejectReleaseTests (VersionTest):
    """
    Tests for reject and release with qpidd
    """
    def test_reject(self):
        name = str(uuid4())
        snd = self.ssn.sender("%s; {create:always, node:{properties:{alternate-exchange:amq.fanout}}}" % name)
        rcv = self.ssn.receiver(name)
        rcv2 = self.ssn.receiver("amq.fanout")

        msgs = [Message(content=s, subject = s) for s in ['a','b','c','d']]

        for m in msgs: snd.send(m)

        for expected in msgs:
            msg = rcv.fetch(0)
            assert msg.content == expected.content
            self.ssn.reject(msg)

        for expected in msgs:
            msg = rcv2.fetch(0)
            assert msg.content == expected.content

    def test_release(self):
        snd = self.ssn.sender("#")
        rcv = self.ssn.receiver(snd.target)

        msgs = [Message(content=s, subject = s) for s in ['a','b','c','d']]

        for m in msgs: snd.send(m)

        msg = rcv.fetch(0)
        assert msg.content == "a"
        self.ssn.release(msg)

        msg = rcv.fetch(0)
        assert msg.content == "a"
        self.ssn.acknowledge(msg)

        msg = rcv.fetch(0)
        assert msg.content == "b"
        self.ssn.release(msg)

