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
from qpid.client import Client, Closed
from qpid.queue import Empty
from qpid.content import Content
from qpid.testlib import TestBase010
from time import sleep

class ExtensionTests(TestBase010):
    """Tests for various extensions to AMQP 0-10"""

    def test_timed_autodelete(self):
        session = self.session
        session2 = self.conn.session("another-session")
        session2.queue_declare(queue="my-queue", exclusive=True, auto_delete=True, arguments={"qpid.auto_delete_timeout":5})
        session2.close()
        result = session.queue_query(queue="my-queue")
        self.assertEqual("my-queue", result.queue)
        sleep(5)
        result = session.queue_query(queue="my-queue")
        self.assert_(not result.queue)
