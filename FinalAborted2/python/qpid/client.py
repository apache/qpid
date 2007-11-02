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

"""
An AQMP client implementation that uses a custom delegate for
interacting with the server.
"""

import threading
from peer import Peer, Closed
from delegate import Delegate
from connection import Connection, Frame
from spec import load
from queue import Queue


class Client:

  def __init__(self, host, port, spec, vhost = None):
    self.host = host
    self.port = port
    self.spec = spec

    self.mechanism = None
    self.response = None
    self.locale = None

    self.vhost = vhost
    if self.vhost == None:
      self.vhost = self.host

    self.queues = {}
    self.lock = threading.Lock()

    self.closed = False
    self.started = threading.Event()

    self.conn = Connection(self.host, self.port, self.spec)
    self.peer = Peer(self.conn, ClientDelegate(self))

  def wait(self):
    self.started.wait()
    if self.closed:
      raise EOFError()

  def queue(self, key):
    self.lock.acquire()
    try:
      try:
        q = self.queues[key]
      except KeyError:
        q = Queue(0)
        self.queues[key] = q
    finally:
      self.lock.release()
    return q

  def start(self, response, mechanism="AMQPLAIN", locale="en_US"):
    self.mechanism = mechanism
    self.response = response
    self.locale = locale

    self.conn.connect()
    self.conn.init()
    self.peer.start()
    self.wait()
    self.channel(0).connection_open(self.vhost)

  def channel(self, id):
    return self.peer.channel(id)

class ClientDelegate(Delegate):

  def __init__(self, client):
    Delegate.__init__(self)
    self.client = client

  def connection_start(self, ch, msg):
    ch.connection_start_ok(mechanism=self.client.mechanism,
                           response=self.client.response,
                           locale=self.client.locale)

  def connection_tune(self, ch, msg):
    ch.connection_tune_ok(*msg.fields)
    self.client.started.set()

  def basic_deliver(self, ch, msg):
    self.client.queue(msg.consumer_tag).put(msg)

  def channel_close(self, ch, msg):
    ch.close(msg)

  def connection_close(self, ch, msg):
    self.client.peer.close(msg)

  def close(self, reason):
    self.client.closed = True
    self.client.started.set()
