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
Management classes for AMQP
"""

import qpid
import base64
import socket
from threading    import Thread
from message      import Message
from time         import sleep
from qpid.client  import Client
from qpid.content import Content
from cStringIO    import StringIO
from codec        import Codec, EOF

#===================================================================
# ManagementMetadata
#
#    One instance of this class is created for each ManagedBroker.  It
#    is used to store metadata from the broker which is needed for the
#    proper interpretation of recevied management content.
#
#===================================================================
class ManagementMetadata:

  def parseSchema (self, cls, oid, len, codec):
    #print "Schema Record: objId=", oid

    config = []
    inst   = []
    while 1:
      flags = codec.decode_octet ()
      if flags == 0x80:
        break

      tc   = codec.decode_octet ()
      name = codec.decode_shortstr ()
      desc = codec.decode_shortstr ()

      if flags & 1: # TODO: Define constants for these
        config.append ((tc, name, desc))
      if (flags & 1) == 0 or (flags & 2) == 2:
        inst.append   ((tc, name, desc))

    # TODO: Handle notification of schema change outbound
    self.schema[(oid,'C')] = config
    self.schema[(oid,'I')] = inst

  def parseContent (self, cls, oid, len, codec):
    #print "Content Record: Class=", cls, ", objId=", oid

    if cls == 'C' and self.broker.config_cb == None:
      return
    if cls == 'I' and self.broker.inst_cb == None:
      return

    if (oid,cls) not in self.schema:
      return

    row        = []
    timestamps = []

    timestamps.append (codec.decode_longlong ()); # Current Time
    timestamps.append (codec.decode_longlong ()); # Create Time
    timestamps.append (codec.decode_longlong ()); # Delete Time

    for element in self.schema[(oid,cls)][:]:
      tc   = element[0]
      name = element[1]
      if   tc == 1: # TODO: Define constants for these
        data = codec.decode_octet ()
      elif tc == 2:
        data = codec.decode_short ()
      elif tc == 3:
        data = codec.decode_long ()
      elif tc == 4:
        data = codec.decode_longlong ()
      elif tc == 5:
        data = codec.decode_octet ()
      elif tc == 6:
        data = codec.decode_shortstr ()
      row.append ((name, data))

    if cls == 'C':
      self.broker.config_cb[1] (self.broker.config_cb[0], oid, row, timestamps)
    if cls == 'I':
      self.broker.inst_cb[1]   (self.broker.inst_cb[0], oid, row, timestamps)

  def parse (self, codec):
    try:
      opcode = chr (codec.decode_octet ())
    except EOF:
      return 0

    cls = chr (codec.decode_octet ())
    oid = codec.decode_short ()
    len = codec.decode_long  ()

    if len < 8:
      raise ValueError ("parse error: value of length field too small")

    if opcode == 'S':
      self.parseSchema (cls, oid, len, codec)

    if opcode == 'C':
      self.parseContent (cls, oid, len, codec)

    return 1

  def __init__ (self, broker):
    self.broker = broker
    self.schema = {}


#===================================================================
# ManagedBroker
#
#    An object of this class represents a connection (over AMQP) to a
#    single managed broker.
#
#===================================================================
class ManagedBroker:

  exchange = "qpid.management"

  def checkHeader (self, codec):
    octet = chr (codec.decode_octet ())
    if octet != 'A':
      return 0
    octet = chr (codec.decode_octet ())
    if octet != 'M':
      return 0
    octet = chr (codec.decode_octet ())
    if octet != '0':
      return 0
    octet = chr (codec.decode_octet ())
    if octet != '1':
      return 0
    return 1

  def receive_cb (self, msg):
    codec = Codec (StringIO (msg.content.body), self.spec)

    if self.checkHeader (codec) == 0:
      raise ValueError ("outer header invalid");

    while self.metadata.parse (codec):
      pass

    msg.complete ()

  def __init__ (self, host = "localhost", port = 5672,
                username = "guest", password = "guest"):

    self.spec = qpid.spec.load ("../specs/amqp.0-10-preview.xml")
    self.client   = None
    self.channel  = None
    self.queue    = None
    self.qname    = None
    self.metadata = ManagementMetadata (self)

    #  Initialize the callback records
    self.schema_cb = None
    self.config_cb = None
    self.inst_cb   = None

    self.host     = host
    self.port     = port
    self.username = username
    self.password = password

  def schemaListener (self, context, callback):
    self.schema_cb = (context, callback)

  def configListener (self, context, callback):
    self.config_cb = (context, callback)

  def instrumentationListener (self, context, callback):
    self.inst_cb = (context, callback)

  def start (self):
    print "Connecting to broker", self.host

    try:
      self.client = Client (self.host, self.port, self.spec)
      self.client.start ({"LOGIN": self.username, "PASSWORD": self.password})
      self.channel = self.client.channel (1)
      response = self.channel.session_open (detached_lifetime=300)
      self.qname = "mgmt-" + base64.urlsafe_b64encode(response.session_id)

      self.channel.queue_declare (queue=self.qname, exclusive=1, auto_delete=1)
      self.channel.queue_bind (exchange=ManagedBroker.exchange, queue=self.qname,
                               routing_key="mgmt")
      self.channel.message_subscribe (queue=self.qname, destination="dest")
      self.queue = self.client.queue ("dest")
      self.queue.listen (self.receive_cb)

      self.channel.message_flow_mode (destination="dest", mode=1)
      self.channel.message_flow (destination="dest", unit=0, value=0xFFFFFFFF)
      self.channel.message_flow (destination="dest", unit=1, value=0xFFFFFFFF)

    except socket.error, e:
      print "Socket Error Detected:", e[1]
      raise
    except:
      raise
