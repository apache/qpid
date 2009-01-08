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

from threading import Condition, RLock, Lock, currentThread
from spec import SPEC
from generator import command_invoker
from datatypes import RangedSet, Struct, Future
from codec010 import StringCodec
from assembler import Segment
from queue import Queue
from datatypes import Message, serial
from util import wait, notify
from exceptions import *
from logging import getLogger

log = getLogger("qpid.io.cmd")
msg = getLogger("qpid.io.msg")

class SessionException(Exception): pass
class SessionClosed(SessionException): pass
class SessionDetached(SessionException): pass

def client(*args):
  return Client(*args)

def server(*args):
  return Server(*args)

INCOMPLETE = object()

class Session(command_invoker(SPEC)):

  def __init__(self, name, auto_sync=True, timeout=10, delegate=client):
    self.name = name
    self.auto_sync = auto_sync
    self.timeout = timeout
    self.channel = None
    self.invoke_lock = Lock()
    self._closing = False
    self._closed = False

    self.condition = Condition()

    self.send_id = True
    self.receiver = Receiver(self)
    self.sender = Sender(self)

    self.lock = RLock()
    self._incoming = {}
    self.results = {}
    self.exceptions = []

    self.assembly = None

    self.delegate = delegate(self)

  def incoming(self, destination):
    self.lock.acquire()
    try:
      queue = self._incoming.get(destination)
      if queue == None:
        queue = Incoming(self, destination)
        self._incoming[destination] = queue
      return queue
    finally:
      self.lock.release()

  def error(self):
    exc = self.exceptions[:]
    if len(exc) == 0:
      return None
    elif len(exc) == 1:
      return exc[0]
    else:
      return tuple(exc)

  def sync(self, timeout=None):
    ch = self.channel
    if ch is not None and currentThread() == ch.connection.thread:
      raise SessionException("deadlock detected")
    if not self.auto_sync:
      self.execution_sync(sync=True)
    last = self.sender.next_id - 1
    if not wait(self.condition, lambda:
                  last in self.sender._completed or self.exceptions,
                timeout):
      raise Timeout()
    if self.exceptions:
      raise SessionException(self.error())

  def close(self, timeout=None):
    self.invoke_lock.acquire()
    try:
      self._closing = True
      self.channel.session_detach(self.name)
    finally:
      self.invoke_lock.release()
    if not wait(self.condition, lambda: self._closed, timeout):
      raise Timeout()

  def closed(self):
    self.lock.acquire()
    try:
      if self._closed: return

      error = self.error()
      for id in self.results:
        f = self.results[id]
        f.error(error)
      self.results.clear()

      for q in self._incoming.values():
        q.close(error)

      self._closed = True
      notify(self.condition)
    finally:
      self.lock.release()

  def invoke(self, type, args, kwargs):
    # XXX
    if not hasattr(type, "track"):
      return type.new(args, kwargs)

    self.invoke_lock.acquire()
    try:
      return self.do_invoke(type, args, kwargs)
    finally:
      self.invoke_lock.release()

  def do_invoke(self, type, args, kwargs):
    if self._closing:
      raise SessionClosed()

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

    hdr = Struct(self.spec["session.header"])
    hdr.sync = self.auto_sync or kwargs.pop("sync", False)

    cmd = type.new(args, kwargs)
    sc = StringCodec(self.spec)
    sc.write_command(hdr, cmd)

    seg = Segment(True, (message == None or
                         (message.headers == None and message.body == None)),
                  type.segment_type, type.track, self.channel.id, sc.encoded)

    if type.result:
      result = Future(exception=SessionException)
      self.results[self.sender.next_id] = result

    self.send(seg)

    log.debug("SENT %s %s %s", seg.id, hdr, cmd)

    if message != None:
      if message.headers != None:
        sc = StringCodec(self.spec)
        for st in message.headers:
          sc.write_struct32(st)
        seg = Segment(False, message.body == None, self.spec["segment_type.header"].value,
                      type.track, self.channel.id, sc.encoded)
        self.send(seg)
      if message.body != None:
        seg = Segment(False, True, self.spec["segment_type.body"].value,
                      type.track, self.channel.id, message.body)
        self.send(seg)
      msg.debug("SENT %s", message)

    if type.result:
      if self.auto_sync:
        return result.get(self.timeout)
      else:
        return result
    elif self.auto_sync:
      self.sync(self.timeout)

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
    segments = assembly[:]

    hdr, cmd = assembly.pop(0).decode(self.spec)
    log.debug("RECV %s %s %s", cmd.id, hdr, cmd)

    args = []

    for st in cmd._type.segments:
      if assembly:
        seg = assembly[0]
        if seg.type == st.segment_type:
          args.append(seg.decode(self.spec))
          assembly.pop(0)
          continue
      args.append(None)

    assert len(assembly) == 0

    attr = cmd._type.qname.replace(".", "_")
    result = getattr(self.delegate, attr)(cmd, *args)

    if cmd._type.result:
      self.execution_result(cmd.id, result)

    if result is not INCOMPLETE:
      for seg in segments:
        self.receiver.completed(seg)
        # XXX: don't forget to obey sync for manual completion as well
        if hdr.sync:
          self.channel.session_completed(self.receiver._completed)

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
    self._completed = RangedSet()

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

  def known_completed(self, commands):
    completed = RangedSet()
    for c in self._completed.ranges:
      for kc in commands.ranges:
        if c.lower in kc and c.upper in kc:
          break
      else:
        completed.add_range(c)
    self._completed = completed

class Sender:

  def __init__(self, session):
    self.session = session
    self.next_id = serial(0)
    self.next_offset = 0
    self.segments = []
    self._completed = RangedSet()

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
    for range in commands.ranges:
      self._completed.add(range.lower, range.upper)

class Incoming(Queue):

  def __init__(self, session, destination):
    Queue.__init__(self)
    self.session = session
    self.destination = destination

  def start(self):
    self.session.message_set_flow_mode(self.destination, self.session.flow_mode.credit)
    for unit in self.session.credit_unit.values():
      self.session.message_flow(self.destination, unit, 0xFFFFFFFFL)

  def stop(self):
    self.session.message_cancel(self.destination)
    self.listen(None)

class Delegate:

  def __init__(self, session):
    self.session = session

  #XXX: do something with incoming accepts
  def message_accept(self, ma): None

  def execution_result(self, er):
    future = self.session.results.pop(er.command_id)
    future.set(er.value)

  def execution_exception(self, ex):
    self.session.exceptions.append(ex)

class Client(Delegate):

  def message_transfer(self, cmd, headers, body):
    m = Message(body)
    m.headers = headers
    m.id = cmd.id
    messages = self.session.incoming(cmd.destination)
    messages.put(m)
    msg.debug("RECV %s", m)
    return INCOMPLETE
