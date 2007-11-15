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
Management API for Qpid
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

  def parseSchema (self, cls, codec):
    className   = codec.decode_shortstr ()
    configCount = codec.decode_short ()
    instCount   = codec.decode_short ()
    methodCount = codec.decode_short ()
    eventCount  = codec.decode_short ()

    configs = []
    insts   = []
    methods = []
    events  = []

    configs.append (("id", 4, "", "", 1, 1, None, None, None, None, None))
    insts.append   (("id", 4, None, None))

    for idx in range (configCount):
      ft = codec.decode_table ()
      name   = ft["name"]
      type   = ft["type"]
      access = ft["access"]
      index  = ft["index"]
      unit   = None
      min    = None
      max    = None
      maxlen = None
      desc   = None

      for key, value in ft.items ():
        if   key == "unit":
          unit = value
        elif key == "min":
          min = value
        elif key == "max":
          max = value
        elif key == "maxlen":
          maxlen = value
        elif key == "desc":
          desc = value

      config = (name, type, unit, desc, access, index, min, max, maxlen)
      configs.append (config)

    for idx in range (instCount):
      ft = codec.decode_table ()
      name   = ft["name"]
      type   = ft["type"]
      unit   = None
      desc   = None

      for key, value in ft.items ():
        if   key == "unit":
          unit = value
        elif key == "desc":
          desc = value

      inst = (name, type, unit, desc)
      insts.append (inst)

    # TODO: Handle notification of schema change outbound
    self.schema[(className,'C')] = configs
    self.schema[(className,'I')] = insts
    self.schema[(className,'M')] = methods
    self.schema[(className,'E')] = events

  def parseContent (self, cls, codec):
    if cls == 'C' and self.broker.config_cb == None:
      return
    if cls == 'I' and self.broker.inst_cb == None:
      return

    className = codec.decode_shortstr ()

    if (className,cls) not in self.schema:
      return

    row        = []
    timestamps = []

    timestamps.append (codec.decode_longlong ())  # Current Time
    timestamps.append (codec.decode_longlong ())  # Create Time
    timestamps.append (codec.decode_longlong ())  # Delete Time

    for element in self.schema[(className,cls)][:]:
      tc   = element[1]
      name = element[0]
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
      elif tc == 7:
        data = codec.decode_longstr ()
      else:
        raise ValueError ("Invalid type code: %d" % tc)
      row.append ((name, data))

    if cls == 'C':
      self.broker.config_cb[1] (self.broker.config_cb[0], className, row, timestamps)
    elif cls == 'I':
      self.broker.inst_cb[1]   (self.broker.inst_cb[0], className, row, timestamps)

  def parse (self, codec):
    opcode = chr (codec.decode_octet ())
    cls    = chr (codec.decode_octet ())

    if opcode == 'S':
      self.parseSchema (cls, codec)

    elif opcode == 'C':
      self.parseContent (cls, codec)

    else:
      raise ValueError ("Unknown opcode: %c" % opcode);

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

  mExchange = "qpid.management"
  dExchange = "amq.direct"

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

  def publish_cb (self, msg):
    codec = Codec (StringIO (msg.content.body), self.spec)

    if self.checkHeader (codec) == 0:
      raise ValueError ("outer header invalid");

    self.metadata.parse (codec)
    msg.complete ()

  def reply_cb (self, msg):
    codec = Codec (StringIO (msg.content.body), self.spec)
    methodId = codec.decode_long ()
    status   = codec.decode_long ()
    sText    = codec.decode_shortstr ()

    args = {}
    if status == 0:
      args["sequence"] = codec.decode_long ()
      args["body"]     = codec.decode_longstr ()

    if self.method_cb != None:
      self.method_cb[1] (self.method_cb[0], methodId, status, sText, args)

    msg.complete ()

  def __init__ (self,
                host     = "localhost",
                port     = 5672,
                username = "guest",
                password = "guest",
                specfile = "../specs/amqp.0-10-preview.xml"):

    self.spec = qpid.spec.load (specfile)
    self.client    = None
    self.channel   = None
    self.queue     = None
    self.rqueue    = None
    self.qname     = None
    self.rqname    = None
    self.metadata  = ManagementMetadata (self)
    self.connected = 0
    self.lastConnectError = None

    #  Initialize the callback records
    self.status_cb = None
    self.schema_cb = None
    self.config_cb = None
    self.inst_cb   = None
    self.method_cb = None

    self.host     = host
    self.port     = port
    self.username = username
    self.password = password

  def statusListener (self, context, callback):
    self.status_cb = (context, callback)

  def schemaListener (self, context, callback):
    self.schema_cb = (context, callback)

  def configListener (self, context, callback):
    self.config_cb = (context, callback)

  def methodListener (self, context, callback):
    self.method_cb = (context, callback)

  def instrumentationListener (self, context, callback):
    self.inst_cb = (context, callback)

  def method (self, methodId, objId, className,
              methodName, args=None, packageName="qpid"):
    codec = Codec (StringIO (), self.spec);
    codec.encode_long     (methodId)
    codec.encode_longlong (objId)
    codec.encode_shortstr (self.rqname)

    # TODO: Encode args according to schema
    if methodName == "echo":
      codec.encode_long (args["sequence"])
      codec.encode_longstr (args["body"])

    msg = Content (codec.stream.getvalue ())
    msg["content_type"] = "application/octet-stream"
    msg["routing_key"]  = "method." + packageName + "." + className + "." + methodName
    msg["reply_to"]     = self.spec.struct ("reply_to")
    self.channel.message_transfer (destination="qpid.management", content=msg)

  def isConnected (self):
    return connected

  def start (self):
    print "Connecting to broker %s:%d" % (self.host, self.port)

    try:
      self.client = Client (self.host, self.port, self.spec)
      self.client.start ({"LOGIN": self.username, "PASSWORD": self.password})
      self.channel = self.client.channel (1)
      response = self.channel.session_open (detached_lifetime=10)
      self.qname  = "mgmt-"  + base64.urlsafe_b64encode (response.session_id)
      self.rqname = "reply-" + base64.urlsafe_b64encode (response.session_id)

      self.channel.queue_declare (queue=self.qname,  exclusive=1, auto_delete=1)
      self.channel.queue_declare (queue=self.rqname, exclusive=1, auto_delete=1)
      
      self.channel.queue_bind (exchange=ManagedBroker.mExchange, queue=self.qname,
                               routing_key="mgmt.#")
      self.channel.queue_bind (exchange=ManagedBroker.dExchange, queue=self.rqname,
                               routing_key=self.rqname)

      self.channel.message_subscribe (queue=self.qname,  destination="mdest")
      self.channel.message_subscribe (queue=self.rqname, destination="rdest")

      self.queue = self.client.queue ("mdest")
      self.queue.listen (self.publish_cb)

      self.channel.message_flow_mode (destination="mdest", mode=1)
      self.channel.message_flow (destination="mdest", unit=0, value=0xFFFFFFFF)
      self.channel.message_flow (destination="mdest", unit=1, value=0xFFFFFFFF)

      self.rqueue = self.client.queue ("rdest")
      self.rqueue.listen (self.reply_cb)

      self.channel.message_flow_mode (destination="rdest", mode=1)
      self.channel.message_flow (destination="rdest", unit=0, value=0xFFFFFFFF)
      self.channel.message_flow (destination="rdest", unit=1, value=0xFFFFFFFF)

      self.connected = 1

    except socket.error, e:
      print "Socket Error Detected:", e[1]
      self.lastConnectError = e
      raise
    except:
      raise

  def stop (self):
    pass
