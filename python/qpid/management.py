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
import struct
import socket
from threading    import Thread
from message      import Message
from time         import time
from qpid.client  import Client
from qpid.content import Content
from cStringIO    import StringIO
from codec        import Codec, EOF
from threading    import Lock, Condition


class SequenceManager:
  """ Manage sequence numbers for asynchronous method calls """
  def __init__ (self):
    self.lock     = Lock ()
    self.sequence = 0
    self.pending  = {}

  def reserve (self, data):
    """ Reserve a unique sequence number """
    self.lock.acquire ()
    result = self.sequence
    self.sequence = self.sequence + 1
    self.pending[result] = data
    self.lock.release ()
    return result

  def release (self, seq):
    """ Release a reserved sequence number """
    data = None
    self.lock.acquire ()
    if seq in self.pending:
      data = self.pending[seq]
      del self.pending[seq]
    self.lock.release ()
    return data


class mgmtObject (object):
  """ Generic object that holds the contents of a management object with its
      attributes set as object attributes. """

  def __init__ (self, classKey, timestamps, row):
    self.classKey   = classKey
    self.timestamps = timestamps
    for cell in row:
      setattr (self, cell[0], cell[1])

class methodResult:
  """ Object that contains the result of a method call """

  def __init__ (self, status, sText, args):
    self.status     = status
    self.statusText = sText
    for arg in args:
      setattr (self, arg, args[arg])

class managementChannel:
  """ This class represents a connection to an AMQP broker. """

  def __init__ (self, ch, topicCb, replyCb, cbContext, _detlife=0):
    """ Given a channel on an established AMQP broker connection, this method
    opens a session and performs all of the declarations and bindings needed
    to participate in the management protocol. """
    response         = ch.session_open (detached_lifetime=_detlife)
    self.sessionId   = response.session_id
    self.topicName   = "mgmt-%08x-%04x-%04x-%04x-%04x%08x" % struct.unpack ("!LHHHHL", response.session_id)
    self.replyName   = "repl-%08x-%04x-%04x-%04x-%04x%08x" % struct.unpack ("!LHHHHL", response.session_id)
    self.qpidChannel = ch
    self.tcb         = topicCb
    self.rcb         = replyCb
    self.context     = cbContext
    self.reqsOutstanding = 0

    ch.queue_declare (queue=self.topicName, exclusive=True, auto_delete=True)
    ch.queue_declare (queue=self.replyName, exclusive=True, auto_delete=True)

    ch.queue_bind (exchange="qpid.management",
                   queue=self.topicName, routing_key="mgmt.#")
    ch.queue_bind (exchange="amq.direct",
                   queue=self.replyName, routing_key=self.replyName)
    ch.message_subscribe (queue=self.topicName, destination="tdest")
    ch.message_subscribe (queue=self.replyName, destination="rdest")

    ch.client.queue ("tdest").listen (self.topicCb)
    ch.client.queue ("rdest").listen (self.replyCb)

    ch.message_flow_mode (destination="tdest", mode=1)
    ch.message_flow (destination="tdest", unit=0, value=0xFFFFFFFF)
    ch.message_flow (destination="tdest", unit=1, value=0xFFFFFFFF)

    ch.message_flow_mode (destination="rdest", mode=1)
    ch.message_flow (destination="rdest", unit=0, value=0xFFFFFFFF)
    ch.message_flow (destination="rdest", unit=1, value=0xFFFFFFFF)

  def topicCb (self, msg):
    """ Receive messages via the topic queue on this channel. """
    self.tcb (self, msg)

  def replyCb (self, msg):
    """ Receive messages via the reply queue on this channel. """
    self.rcb (self, msg)

  def send (self, exchange, msg):
    self.qpidChannel.message_transfer (destination=exchange, content=msg)


class managementClient:
  """ This class provides an API for access to management data on the AMQP
  network.  It implements the management protocol and manages the management
  schemas as advertised by the various management agents in the network. """

  CTRL_BROKER_INFO   = 1
  CTRL_SCHEMA_LOADED = 2
  CTRL_USER          = 3

  SYNC_TIME = 10.0

  #========================================================
  # User API - interacts with the class's user
  #========================================================
  def __init__ (self, amqpSpec, ctrlCb=None, configCb=None, instCb=None, methodCb=None):
    self.spec     = amqpSpec
    self.ctrlCb   = ctrlCb
    self.configCb = configCb
    self.instCb   = instCb
    self.methodCb = methodCb
    self.schemaCb = None
    self.eventCb  = None
    self.channels = []
    self.seqMgr   = SequenceManager ()
    self.schema   = {}
    self.packages = {}
    self.cv       = Condition ()
    self.syncInFlight = False
    self.syncSequence = 0
    self.syncResult   = None

  def schemaListener (self, schemaCb):
    """ Optionally register a callback to receive details of the schema of
    managed objects in the network. """
    self.schemaCb = schemaCb

  def eventListener (self, eventCb):
    """ Optionally register a callback to receive events from managed objects
    in the network. """
    self.eventCb = eventCb

  def addChannel (self, channel, cbContext=None):
    """ Register a new channel. """
    mch = managementChannel (channel, self.topicCb, self.replyCb, cbContext)

    self.channels.append (mch)
    self.incOutstanding (mch)
    codec = Codec (StringIO (), self.spec)
    self.setHeader (codec, ord ('B'))
    msg = Content  (codec.stream.getvalue ())
    msg["content_type"] = "application/octet-stream"
    msg["routing_key"]  = "agent"
    msg["reply_to"]     = self.spec.struct ("reply_to")
    msg["reply_to"]["exchange_name"] = "amq.direct"
    msg["reply_to"]["routing_key"]   = mch.replyName
    mch.send ("qpid.management", msg)
    return mch

  def removeChannel (self, mch):
    """ Remove a previously added channel from management. """
    self.channels.remove (mch)

  def callMethod (self, channel, userSequence, objId, className, methodName, args=None):
    """ Invoke a method on a managed object. """
    self.method (channel, userSequence, objId, className, methodName, args)

  def getObjects (self, channel, userSequence, className):
    """ Request immediate content from broker """
    codec = Codec (StringIO (), self.spec)
    self.setHeader (codec, ord ('G'), userSequence)
    ft = {}
    ft["_class"] = className
    codec.encode_table (ft)
    msg = Content (codec.stream.getvalue ())
    msg["content_type"] = "application/octet-stream"
    msg["routing_key"]  = "agent"
    msg["reply_to"]     = self.spec.struct ("reply_to")
    msg["reply_to"]["exchange_name"] = "amq.direct"
    msg["reply_to"]["routing_key"]   = channel.replyName
    channel.send ("qpid.management", msg)

  def syncWaitForStable (self, channel):
    """ Synchronous (blocking) call to wait for schema stability on a channel """
    self.cv.acquire ()
    if channel.reqsOutstanding == 0:
      self.cv.release ()
      return

    self.syncInFlight = True
    starttime = time ()
    while channel.reqsOutstanding != 0:
      self.cv.wait (self.SYNC_TIME)
      if time () - starttime > self.SYNC_TIME:
        self.cv.release ()
        raise RuntimeError ("Timed out waiting for response on channel")
    self.cv.release ()

  def syncCallMethod (self, channel, objId, className, methodName, args=None):
    """ Synchronous (blocking) method call """
    self.cv.acquire ()
    self.syncInFlight = True
    self.syncResult   = None
    self.syncSequence = self.seqMgr.reserve ("sync")
    self.cv.release ()
    self.callMethod (channel, self.syncSequence, objId, className, methodName, args)
    self.cv.acquire ()
    starttime = time ()
    while self.syncInFlight:
      self.cv.wait (self.SYNC_TIME)
      if time () - starttime > self.SYNC_TIME:
        self.cv.release ()
        raise RuntimeError ("Timed out waiting for response on channel")
    result = self.syncResult
    self.cv.release ()
    return result

  def syncGetObjects (self, channel, className):
    """ Synchronous (blocking) get call """
    self.cv.acquire ()
    self.syncInFlight = True
    self.syncResult   = []
    self.syncSequence = self.seqMgr.reserve ("sync")
    self.cv.release ()
    self.getObjects (channel, self.syncSequence, className)
    self.cv.acquire ()
    starttime = time ()
    while self.syncInFlight:
      self.cv.wait (self.SYNC_TIME)
      if time () - starttime > self.SYNC_TIME:
        self.cv.release ()
        raise RuntimeError ("Timed out waiting for response on channel")
    result = self.syncResult
    self.cv.release ()
    return result

  #========================================================
  # Channel API - interacts with registered channel objects
  #========================================================
  def topicCb (self, ch, msg):
    """ Receive messages via the topic queue of a particular channel. """
    codec = Codec (StringIO (msg.content.body), self.spec)
    hdr   = self.checkHeader (codec)
    if hdr == None:
      raise ValueError ("outer header invalid");
    self.parse (ch, codec, hdr[0], hdr[1])
    msg.complete ()

  def replyCb (self, ch, msg):
    """ Receive messages via the reply queue of a particular channel. """
    codec = Codec (StringIO (msg.content.body), self.spec)
    hdr   = self.checkHeader (codec)
    if hdr == None:
      msg.complete ()
      return

    if   hdr[0] == 'm':
      self.handleMethodReply (ch, codec, hdr[1])
    elif hdr[0] == 'z':
      self.handleCommandComplete (ch, codec, hdr[1])
    elif hdr[0] == 'b':
      self.handleBrokerResponse (ch, codec)
    elif hdr[0] == 'p':
      self.handlePackageInd (ch, codec)
    elif hdr[0] == 'q':
      self.handleClassInd (ch, codec)
    else:
      self.parse (ch, codec, hdr[0], hdr[1])
    msg.complete ()

  #========================================================
  # Internal Functions
  #========================================================
  def setHeader (self, codec, opcode, seq = 0):
    """ Compose the header of a management message. """
    codec.encode_octet (ord ('A'))
    codec.encode_octet (ord ('M'))
    codec.encode_octet (ord ('1'))
    codec.encode_octet (opcode)
    codec.encode_long  (seq)

  def checkHeader (self, codec):
    """ Check the header of a management message and extract the opcode and
    class. """
    octet = chr (codec.decode_octet ())
    if octet != 'A':
      return None
    octet = chr (codec.decode_octet ())
    if octet != 'M':
      return None
    octet = chr (codec.decode_octet ())
    if octet != '1':
      return None
    opcode = chr (codec.decode_octet ())
    seq    = codec.decode_long ()
    return (opcode, seq)

  def encodeValue (self, codec, value, typecode):
    """ Encode, into the codec, a value based on its typecode. """
    if   typecode == 1:
      codec.encode_octet    (int  (value))
    elif typecode == 2:
      codec.encode_short    (int  (value))
    elif typecode == 3:
      codec.encode_long     (long (value))
    elif typecode == 4:
      codec.encode_longlong (long (value))
    elif typecode == 5:
      codec.encode_octet    (int  (value))
    elif typecode == 6:
      codec.encode_shortstr (value)
    elif typecode == 7:
      codec.encode_longstr  (value)
    elif typecode == 8:  # ABSTIME
      codec.encode_longlong (long (value))
    elif typecode == 9:  # DELTATIME
      codec.encode_longlong (long (value))
    elif typecode == 10: # REF
      codec.encode_longlong (long (value))
    elif typecode == 11: # BOOL
      codec.encode_octet    (int  (value))
    elif typecode == 12: # FLOAT
      codec.encode_float    (float (value))
    elif typecode == 13: # DOUBLE
      codec.encode_double   (double (value))
    elif typecode == 14: # UUID
      codec.encode_uuid     (value)
    elif typecode == 15: # FTABLE
      codec.encode_table    (value)
    else:
      raise ValueError ("Invalid type code: %d" % typecode)

  def decodeValue (self, codec, typecode):
    """ Decode, from the codec, a value based on its typecode. """
    if   typecode == 1:
      data = codec.decode_octet ()
    elif typecode == 2:
      data = codec.decode_short ()
    elif typecode == 3:
      data = codec.decode_long ()
    elif typecode == 4:
      data = codec.decode_longlong ()
    elif typecode == 5:
      data = codec.decode_octet ()
    elif typecode == 6:
      data = codec.decode_shortstr ()
    elif typecode == 7:
      data = codec.decode_longstr ()
    elif typecode == 8:  # ABSTIME
      data = codec.decode_longlong ()
    elif typecode == 9:  # DELTATIME
      data = codec.decode_longlong ()
    elif typecode == 10: # REF
      data = codec.decode_longlong ()
    elif typecode == 11: # BOOL
      data = codec.decode_octet ()
    elif typecode == 12: # FLOAT
      data = codec.decode_float ()
    elif typecode == 13: # DOUBLE
      data = codec.decode_double ()
    elif typecode == 14: # UUID
      data = codec.decode_uuid ()
    elif typecode == 15: # FTABLE
      data = codec.decode_table ()
    else:
      raise ValueError ("Invalid type code: %d" % typecode)
    return data

  def incOutstanding (self, ch):
    self.cv.acquire ()
    ch.reqsOutstanding = ch.reqsOutstanding + 1
    self.cv.release ()

  def decOutstanding (self, ch):
    self.cv.acquire ()
    ch.reqsOutstanding = ch.reqsOutstanding - 1
    if ch.reqsOutstanding == 0 and self.syncInFlight:
      self.syncInFlight = False
      self.cv.notify ()
    self.cv.release ()

    if ch.reqsOutstanding == 0:
      if self.ctrlCb != None:
        self.ctrlCb (ch.context, self.CTRL_SCHEMA_LOADED, None)

  def handleMethodReply (self, ch, codec, sequence):
    status = codec.decode_long ()
    sText  = codec.decode_shortstr ()

    data = self.seqMgr.release (sequence)
    if data == None:
      return

    (userSequence, classId, methodName) = data
    args = {}
    context = self.seqMgr.release (userSequence)

    if status == 0:
      schemaClass = self.schema[classId]
      ms = schemaClass['M']
      arglist = None
      for mname in ms:
        (mdesc, margs) = ms[mname]
        if mname == methodName:
          arglist = margs
      if arglist == None:
        return

      for arg in arglist:
        if arg[2].find("O") != -1:
          args[arg[0]] = self.decodeValue (codec, arg[1])

    if context == "sync" and userSequence == self.syncSequence:
      self.cv.acquire ()
      self.syncInFlight = False
      self.syncResult   = methodResult (status, sText, args)
      self.cv.notify  ()
      self.cv.release ()
    elif self.methodCb != None:
      self.methodCb (ch.context, userSequence, status, sText, args)

  def handleCommandComplete (self, ch, codec, seq):
    code = codec.decode_long ()
    text = codec.decode_shortstr ()
    data = (seq, code, text)
    context = self.seqMgr.release (seq)
    if context == "outstanding":
      self.decOutstanding (ch)
    elif context == "sync" and seq == self.syncSequence:
      self.cv.acquire ()
      self.syncInFlight = False
      self.cv.notify  ()
      self.cv.release ()
    elif self.ctrlCb != None:
      self.ctrlCb (ch.context, self.CTRL_USER, data)

  def handleBrokerResponse (self, ch, codec):
    if self.ctrlCb != None:
      uuid = codec.decode_uuid ()
      data = (uuid, ch.sessionId)
      self.ctrlCb (ch.context, self.CTRL_BROKER_INFO, data)

    # Send a package request
    sendCodec = Codec (StringIO (), self.spec)
    seq = self.seqMgr.reserve ("outstanding")
    self.setHeader (sendCodec, ord ('P'), seq)
    smsg = Content  (sendCodec.stream.getvalue ())
    smsg["content_type"] = "application/octet-stream"
    smsg["routing_key"]  = "agent"
    smsg["reply_to"]     = self.spec.struct ("reply_to")
    smsg["reply_to"]["exchange_name"] = "amq.direct"
    smsg["reply_to"]["routing_key"]   = ch.replyName
    ch.send ("qpid.management", smsg)
    
  def handlePackageInd (self, ch, codec):
    pname = codec.decode_shortstr ()
    if pname not in self.packages:
      self.packages[pname] = {}

      # Send a class request
      sendCodec = Codec (StringIO (), self.spec)
      seq = self.seqMgr.reserve ("outstanding")
      self.setHeader (sendCodec, ord ('Q'), seq)
      self.incOutstanding (ch)
      sendCodec.encode_shortstr (pname)
      smsg = Content  (sendCodec.stream.getvalue ())
      smsg["content_type"] = "application/octet-stream"
      smsg["routing_key"]  = "agent"
      smsg["reply_to"]     = self.spec.struct ("reply_to")
      smsg["reply_to"]["exchange_name"] = "amq.direct"
      smsg["reply_to"]["routing_key"]   = ch.replyName
      ch.send ("qpid.management", smsg)

  def handleClassInd (self, ch, codec):
    pname = codec.decode_shortstr ()
    cname = codec.decode_shortstr ()
    hash  = codec.decode_bin128   ()
    if pname not in self.packages:
      return

    if (cname, hash) not in self.packages[pname]:
      # Send a schema request
      sendCodec = Codec (StringIO (), self.spec)
      seq = self.seqMgr.reserve ("outstanding")
      self.setHeader (sendCodec, ord ('S'), seq)
      self.incOutstanding (ch)
      sendCodec.encode_shortstr (pname)
      sendCodec.encode_shortstr (cname)
      sendCodec.encode_bin128   (hash)
      smsg = Content  (sendCodec.stream.getvalue ())
      smsg["content_type"] = "application/octet-stream"
      smsg["routing_key"]  = "agent"
      smsg["reply_to"]     = self.spec.struct ("reply_to")
      smsg["reply_to"]["exchange_name"] = "amq.direct"
      smsg["reply_to"]["routing_key"]   = ch.replyName
      ch.send ("qpid.management", smsg)

  def parseSchema (self, ch, codec):
    """ Parse a received schema-description message. """
    self.decOutstanding (ch)
    packageName = codec.decode_shortstr ()
    className   = codec.decode_shortstr ()
    hash        = codec.decode_bin128 ()
    configCount = codec.decode_short ()
    instCount   = codec.decode_short ()
    methodCount = codec.decode_short ()
    eventCount  = codec.decode_short ()

    if packageName not in self.packages:
      return
    if (className, hash) in self.packages[packageName]:
      return

    classKey = (packageName, className, hash)
    if classKey in self.schema:
      return

    configs = []
    insts   = []
    methods = {}
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

    for idx in range (methodCount):
      ft = codec.decode_table ()
      mname    = ft["name"]
      argCount = ft["argCount"]
      if "desc" in ft:
        mdesc = ft["desc"]
      else:
        mdesc = None

      args = []
      for aidx in range (argCount):
        ft = codec.decode_table ()
        name    = ft["name"]
        type    = ft["type"]
        dir     = ft["dir"].upper ()
        unit    = None
        min     = None
        max     = None
        maxlen  = None
        desc    = None
        default = None

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
          elif key == "default":
            default = value

        arg = (name, type, dir, unit, desc, min, max, maxlen, default)
        args.append (arg)
      methods[mname] = (mdesc, args)

    schemaClass = {}
    schemaClass['C'] = configs
    schemaClass['I'] = insts
    schemaClass['M'] = methods
    schemaClass['E'] = events
    self.schema[classKey] = schemaClass

    if self.schemaCb != None:
      self.schemaCb (ch.context, classKey, configs, insts, methods, events)

  def parseContent (self, ch, cls, codec, seq=0):
    """ Parse a received content message. """
    if (cls == 'C' or (cls == 'B' and seq == 0)) and self.configCb == None:
      return
    if cls == 'I' and self.instCb == None:
      return

    packageName = codec.decode_shortstr ()
    className   = codec.decode_shortstr ()
    hash        = codec.decode_bin128 ()
    classKey    = (packageName, className, hash)

    if classKey not in self.schema:
      return

    row        = []
    timestamps = []

    timestamps.append (codec.decode_longlong ())  # Current Time
    timestamps.append (codec.decode_longlong ())  # Create Time
    timestamps.append (codec.decode_longlong ())  # Delete Time

    schemaClass = self.schema[classKey]
    if cls == 'C' or cls == 'B':
      for element in schemaClass['C'][:]:
        tc   = element[1]
        name = element[0]
        data = self.decodeValue (codec, tc)
        row.append ((name, data))

    if cls == 'I' or cls == 'B':
      if cls == 'B':
        start = 1
      else:
        start = 0
      for element in schemaClass['I'][start:]:
        tc   = element[1]
        name = element[0]
        data = self.decodeValue (codec, tc)
        row.append ((name, data))

    if   cls == 'C' or (cls == 'B' and seq != self.syncSequence):
      self.configCb (ch.context, classKey, row, timestamps)
    elif cls == 'B' and seq == self.syncSequence:
      if timestamps[2] == 0:
        obj = mgmtObject (classKey, timestamps, row)
        self.syncResult.append (obj)
    elif cls == 'I':
      self.instCb   (ch.context, classKey, row, timestamps)

  def parse (self, ch, codec, opcode, seq):
    """ Parse a message received from the topic queue. """
    if opcode   == 's':
      self.parseSchema  (ch, codec)
    elif opcode == 'c':
      self.parseContent (ch, 'C', codec)
    elif opcode == 'i':
      self.parseContent (ch, 'I', codec)
    elif opcode == 'g':
      self.parseContent (ch, 'B', codec, seq)
    else:
      raise ValueError ("Unknown opcode: %c" % opcode);

  def method (self, channel, userSequence, objId, classId, methodName, args):
    """ Invoke a method on an object """
    codec = Codec (StringIO (), self.spec)
    sequence = self.seqMgr.reserve ((userSequence, classId, methodName))
    self.setHeader (codec, ord ('M'), sequence)
    codec.encode_longlong (objId)       # ID of object

    # Encode args according to schema
    if classId not in self.schema:
      self.seqMgr.release (sequence)
      raise ValueError ("Unknown class name: %s" % classId)
    
    schemaClass = self.schema[classId]
    ms          = schemaClass['M']
    arglist     = None
    for mname in ms:
      (mdesc, margs) = ms[mname]
      if mname == methodName:
        arglist = margs
    if arglist == None:
      self.seqMgr.release (sequence)
      raise ValueError ("Unknown method name: %s" % methodName)

    for arg in arglist:
      if arg[2].find("I") != -1:
        value = arg[8]  # default
        if arg[0] in args:
          value = args[arg[0]]
          if value == None:
            self.seqMgr.release (sequence)
            raise ValueError ("Missing non-defaulted argument: %s" % arg[0])
          self.encodeValue (codec, value, arg[1])

    packageName = classId[0]
    className   = classId[1]
    msg = Content (codec.stream.getvalue ())
    msg["content_type"] = "application/octet-stream"
    msg["routing_key"]  = "agent.method." + packageName + "." + \
        className + "." + methodName
    msg["reply_to"]     = self.spec.struct ("reply_to")
    msg["reply_to"]["exchange_name"] = "amq.direct"
    msg["reply_to"]["routing_key"]   = channel.replyName
    channel.send ("qpid.management", msg)
