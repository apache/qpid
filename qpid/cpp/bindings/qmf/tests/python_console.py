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

import sys
from qpid.testlib import TestBase010
from qpid.datatypes import Message
from qpid.queue import Empty
from time import sleep

class QmfInteropTests(TestBase010):

    def test_A_agent_presence(self):
        self.startQmf();
        qmf = self.qmf

        agents = []
        count = 0
        while len(agents) == 0:
            agents = qmf.getObjects(_class="agent")
            sleep(1)
            count += 1
            if count > 5:
                self.fail("Timed out waiting for remote agent")

    def test_B_basic_types(self):
        self.startQmf();
        qmf = self.qmf

        parents = qmf.getObjects(_class="parent")
        self.assertEqual(len(parents), 1)
        self.assertEqual(parents[0].uint32val, 0xA5A5A5A5)

    def getProperty(self, msg, name):
        for h in msg.headers:
            if hasattr(h, name): return getattr(h, name)
        return None            

    def getAppHeader(self, msg, name):
        headers = self.getProperty(msg, "application_headers")
        if headers:
            return headers[name]
        return None
