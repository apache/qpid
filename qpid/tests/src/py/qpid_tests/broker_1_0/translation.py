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

class TranslationTests (VersionTest):
    """
    Testing translation of messages between 1.0 and 0-10
    """
    def send_receive_messages(self, msgs, send_version, receive_version, address):
        rcon = self.create_connection(receive_version, True)
        rcv = rcon.session().receiver(address)

        scon = self.create_connection(send_version, True)
        snd = scon.session().sender(rcv.source)

        for m in msgs: snd.send(m)

        for expected in msgs:
            msg = rcv.fetch()
            assert msg.content == expected.content, (msg.content, expected.content)
            assert msg.subject == expected.subject, (msg.subject, expected.subject)
            self.ssn.acknowledge(msg)
        scon.close()
        rcon.close()

    def send_receive(self, send_version, receive_version, address):
        self.send_receive_messages([Message(content=s, subject = s) for s in ['a','b','c','d']], send_version, receive_version, address)

    def send_receive_map(self, send_version, receive_version, address):
        self.send_receive_messages([Message(content={'s':'abc','i':10})], send_version, receive_version, address)

    def send_receive_list(self, send_version, receive_version, address):
        self.send_receive_messages([Message(content=['a', 1, 'c'])], send_version, receive_version, address)

    def test_translation_queue_1(self):
        self.send_receive("amqp0-10", "amqp1.0", '#')

    def test_translation_queue_2(self):
        self.send_receive("amqp1.0", "amqp0-10", '#')

    def test_translation_exchange_1(self):
        self.send_receive("amqp0-10", "amqp1.0", 'amq.fanout')

    def test_translation_exchange_2(self):
        self.send_receive("amqp1.0", "amqp0-10", 'amq.fanout')

    def test_send_receive_queue_1(self):
        self.send_receive("amqp1.0", "amqp1.0", '#')

    def test_send_receive_queue_2(self):
        self.send_receive("amqp0-10", "amqp0-10", '#')

    def test_send_receive_exchange_1(self):
        self.send_receive("amqp1.0", "amqp1.0", 'amq.fanout')

    def test_send_receive_exchange_2(self):
        self.send_receive("amqp0-10", "amqp0-10", 'amq.fanout')

    def test_translate_map_1(self):
        self.send_receive_map("amqp0-10", "amqp1.0", '#')

    def test_translate_map_2(self):
        self.send_receive_map("amqp1.0", "amqp0-10", '#')

    def test_translate_list_1(self):
        self.send_receive_list("amqp0-10", "amqp1.0", '#')

    def test_translate_list_2(self):
        self.send_receive_list("amqp1.0", "amqp0-10", '#')
