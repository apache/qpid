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

class HeadersFederationTests(TestBase010):

    def remote_host(self):
        return self.defines.get("remote-host", "localhost")

    def remote_port(self):
        return int(self.defines["remote-port"])

    def verify_cleanup(self):
        attempts = 0
        total = len(self.qmf.getObjects(_class="bridge")) + len(self.qmf.getObjects(_class="link"))
        while total > 0:
            attempts += 1
            if attempts >= 10:
                self.fail("Bridges and links didn't clean up")
                return
            sleep(1)
            total = len(self.qmf.getObjects(_class="bridge")) + len(self.qmf.getObjects(_class="link"))

    def test_dynamic_headers_unbind(self):
        session = self.session
        r_conn = self.connect(host=self.remote_host(), port=self.remote_port())
        r_session = r_conn.session("test_dynamic_headers_unbind")

        session.exchange_declare(exchange="fed.headers_unbind", type="headers")
        r_session.exchange_declare(exchange="fed.headers_unbind", type="headers")

        self.startQmf()
        qmf = self.qmf

        broker = qmf.getObjects(_class="broker")[0]
        result = broker.connect(self.remote_host(), self.remote_port(), False, "PLAIN", "guest", "guest", "tcp")
        self.assertEqual(result.status, 0)

        link = qmf.getObjects(_class="link")[0]
        result = link.bridge(False, "fed.headers_unbind", "fed.headers_unbind", "", "", "", False, False, True, 0)
        self.assertEqual(result.status, 0)
        bridge = qmf.getObjects(_class="bridge")[0]
        sleep(5)

        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        queue = qmf.getObjects(_class="queue", name="fed1")[0]
        queue.update()
        self.assertEqual(queue.bindingCount, 1,
                         "bindings not accounted for (expected 1, got %d)" % queue.bindingCount)

        session.exchange_bind(queue="fed1", exchange="fed.headers_unbind", binding_key="key1", arguments={'x-match':'any', 'class':'first'})
        queue.update()
        self.assertEqual(queue.bindingCount, 2,
                         "bindings not accounted for (expected 2, got %d)" % queue.bindingCount)

        session.exchange_unbind(queue="fed1", exchange="fed.headers_unbind", binding_key="key1")
        queue.update()
        self.assertEqual(queue.bindingCount, 1,
                         "bindings not accounted for (expected 1, got %d)" % queue.bindingCount)

        result = bridge.close()
        self.assertEqual(result.status, 0)
        result = link.close()
        self.assertEqual(result.status, 0)

        self.verify_cleanup()

    def getProperty(self, msg, name):
        for h in msg.headers:
            if hasattr(h, name): return getattr(h, name)
        return None            

    def getAppHeader(self, msg, name):
        headers = self.getProperty(msg, "application_headers")
        if headers:
            return headers[name]
        return None
