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
from qpid.testlib import testrunner, TestBase

class PersistenceTests(TestBase):
    def test_delete_queue_after_publish(self):
        channel = self.channel
        channel.synchronous = False

        #create queue
        channel.queue_declare(queue = "q", auto_delete=True, durable=True)

        #send message
        for i in range(1, 10):
            channel.message_transfer(content=Content(properties={'routing_key' : "q", 'delivery_mode':2}, body = "my-message"))

        channel.synchronous = True
        #explicitly delete queue
        channel.queue_delete(queue = "q")

    def test_ack_message_from_deleted_queue(self):
        channel = self.channel
        channel.synchronous = False

        #create queue
        channel.queue_declare(queue = "q", auto_delete=True, durable=True)

        #send message
        channel.message_transfer(content=Content(properties={'routing_key' : "q", 'delivery_mode':2}, body = "my-message"))

        #create consumer
        channel.message_subscribe(queue = "q", destination = "a", confirm_mode = 1, acquire_mode=0)
        channel.message_flow(unit = 1, value = 0xFFFFFFFF, destination = "a")
        channel.message_flow(unit = 0, value = 10, destination = "a")
        queue = self.client.queue("a")

        #consume the message, cancel subscription (triggering auto-delete), then ack it
        msg = queue.get(timeout = 5)
        channel.message_cancel(destination = "a")        
        msg.complete()

    def test_queue_deletion(self):
        channel = self.channel
        channel.queue_declare(queue = "durable-subscriber-queue", exclusive=True, durable=True)
        channel.queue_bind(exchange="amq.topic", queue="durable-subscriber-queue", routing_key="xyz")
        channel.message_transfer(destination= "amq.topic", content=Content(properties={'routing_key' : "xyz", 'delivery_mode':2}, body = "my-message"))
        channel.queue_delete(queue = "durable-subscriber-queue")
    
