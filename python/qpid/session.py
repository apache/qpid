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

from threading import Event, RLock
from invoker import Invoker
from datatypes import RangeSet, Struct, Future
from codec010 import StringCodec
from assembler import Segment
from queue import Queue
from datatypes import Message
from logging import getLogger

class SessionDetached(Exception): pass

def client(*args):
  return Client(*args)

def server(*args):
  return Server(*args)

class Session(Invoker):

  def __init__(self, name, spec, sync=True, timeout=10, delegate=client):
    self.name = name
    self.spec = spec
    self.sync = sync
    self.timeout = timeout
    self.channel = None
    self.opened = Event()
    self.closed = Event()
    self.receiver = Receiver(self)
    self.sender = Sender(self)
    self.delegate = delegate(self)
    self.send_id = True
    self.results = {}
    self.lock = RLock()
    self._incoming = {}
    self.assembly = None

  def incoming(self, destination):
    self.lock.acquire()
    try:
      queue = self._incoming.get(destination)
      if queue == None:
        queue = Queue()
        self._incoming[destination] = queue
      return queue
    finally:
      self.lock.release()

  def close(self, timeout=None):
    self.channel.session_detach(self.name)
    self.closed.wait(timeout=timeout)

  def resolve_method(self, name):
    cmd = self.spec.instructions.get(name)
    if cmd is not None and cmd.track == self.spec["track.command"].value:
      return cmd
    else:
      return None

  def invoke(self, type, args, kwargs):
    if self.channel == None:
      raise SessionDetached()

    if type.segments:
      if len(args) == len(type.fields) + 1:
        message = args[-1]
        args = args[:-1]
      else:
        message = kwargs.pop("message", None)
    else:
      message = None

    cmd = type.new(args, kwargs)
    sc = StringCodec(self.spec)
    sc.write_command(type, cmd)

    seg = Segment(True, (message == None or
                         (message.headers == None and message.body == None)),
                  type.segment_type, type.track, self.channel.id, sc.encoded)

    if type.result:
      result = Future()
      self.results[self.sender.next_id] = result

    self.send(seg)

    if message != None:
      if message.headers != None:
        sc = StringCodec(self.spec)
        for st in message.headers:
          sc.write_struct32(st.type, st)
        seg = Segment(False, message.body == None, self.spec["segment_type.header"].value,
                      type.track, self.channel.id, sc.encoded)
        self.send(seg)
      if message.body != None:
        seg = Segment(False, True, self.spec["segment_type.body"].value,
                      type.track, self.channel.id, message.body)
        self.send(seg)

    if type.result:
      if self.sync:
        return result.get(self.timeout)
      else:
        return result

  def received(self, seg):
    self.receiver.received(seg)
    if seg.first:
      assert self.assembly == None
      self.assembly = []
    self.assembly.append(seg)
    if seg.last:
      self.dispatch(self.assembly)
      self.assembly = None

  def dispatch(self, assembly):
    cmd = assembly.pop(0).decode(self.spec)
    args = []

    for st in cmd.type.segments:
      if assembly:
        seg = assembly[0]
        if seg.type == st.segment_type:
          args.append(seg.decode(self.spec))
          assembly.pop(0)
          continue
      args.append(None)

    assert len(assembly) == 0

    attr = cmd.type.qname.replace(".", "_")
    result = getattr(self.delegate, attr)(cmd, *args)

    if cmd.type.result:
      self.execution_result(cmd.id, result)

    for seg in assembly:
      self.receiver.completed(seg)

  def send(self, seg):
    self.sender.send(seg)

  def __str__(self):
    return '<Session: %s, %s>' % (self.name, self.channel)

  def __repr__(self):
    return str(self)

class Receiver:

  def __init__(self, session):
    self.session = session
    self.next_id = None
    self.next_offset = None
    self._completed = RangeSet()

  def received(self, seg):
    if self.next_id == None or self.next_offset == None:
      raise Exception("todo")
    seg.id = self.next_id
    seg.offset = self.next_offset
    if seg.last:
      self.next_id += 1
      self.next_offset = 0
    else:
      self.next_offset += len(seg.payload)

  def completed(self, seg):
    if seg.id == None:
      raise ValueError("cannot complete unidentified segment")
    if seg.last:
      self._completed.add(seg.id)

class Sender:

  def __init__(self, session):
    self.session = session
    self.next_id = 0
    self.next_offset = 0
    self.segments = []

  def send(self, seg):
    seg.id = self.next_id
    seg.offset = self.next_offset
    if seg.last:
      self.next_id += 1
      self.next_offset = 0
    else:
      self.next_offset += len(seg.payload)
    self.segments.append(seg)
    if self.session.send_id:
      self.session.send_id = False
      self.session.channel.session_command_point(seg.id, seg.offset)
    self.session.channel.connection.write_segment(seg)

  def completed(self, commands):
    idx = 0
    while idx < len(self.segments):
      seg = self.segments[idx]
      if seg.id in commands:
        del self.segments[idx]
      else:
        idx += 1

from queue import Queue, Closed, Empty

class Delegate:

  def __init__(self, session):
    self.session = session

  def execution_result(self, er):
    future = self.session.results[er.command_id]
    future.set(er.value)

msg = getLogger("qpid.ssn.msg")

class Client(Delegate):

  def message_transfer(self, cmd, headers, body):
    m = Message(body)
    m.headers = headers
    messages = self.session.incoming(cmd.destination)
    messages.put(m)
    msg.debug("RECV: %s", m)
