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
This module contains a skeletal peer implementation useful for
implementing an AMQP server, client, or proxy. The peer implementation
sorts incoming frames to their intended channels, and dispatches
incoming method frames to a delegate.
"""

import thread, threading, traceback, socket, sys, logging
from connection import EOF, Method, Header, Body, Request, Response
from message import Message
from queue import Queue, Closed as QueueClosed
from content import Content
from cStringIO import StringIO

class Sequence:

  def __init__(self, start, step = 1):
    # we should keep start for wrap around
    self._next = start
    self.step = step
    self.lock = thread.allocate_lock()

  def next(self):
    self.lock.acquire()
    try:
      result = self._next
      self._next += self.step
      return result
    finally:
      self.lock.release()

class Peer:

  def __init__(self, conn, delegate, channel_callback=None):
    self.conn = conn
    self.delegate = delegate
    self.outgoing = Queue(0)
    self.work = Queue(0)
    self.channels = {}
    self.lock = thread.allocate_lock()
    self.channel_callback = channel_callback #notified when channels are created

  def channel(self, id):
    self.lock.acquire()
    try:
      try:
        ch = self.channels[id]
      except KeyError:
        ch = Channel(id, self.outgoing, self.conn.spec)
        self.channels[id] = ch
        if self.channel_callback:
          self.channel_callback(ch)
    finally:
      self.lock.release()
    return ch

  def start(self):
    thread.start_new_thread(self.writer, ())
    thread.start_new_thread(self.reader, ())
    thread.start_new_thread(self.worker, ())

  def fatal(self, message=None):
    """Call when an unexpected exception occurs that will kill a thread."""
    if message: print >> sys.stderr, message
    self.close("Fatal error: %s\n%s" % (message or "", traceback.format_exc()))

  def reader(self):
    try:
      while True:
        try:
          frame = self.conn.read()
        except EOF, e:
          self.work.close()
          break
        ch = self.channel(frame.channel)
        ch.receive(frame, self.work)
    except:
      self.fatal()

  def close(self, reason):
    for ch in self.channels.values():
      ch.close(reason)
    self.delegate.close(reason)

  def writer(self):
    try:
      while True:
        try:
          message = self.outgoing.get()
          self.conn.write(message)
        except socket.error, e:
          self.close(e)
          break
        self.conn.flush()
    except:
      self.fatal()

  def worker(self):
    try:
      while True:
        queue = self.work.get()
        frame = queue.get()
        channel = self.channel(frame.channel)
        if frame.method_type.content:
          content = read_content(queue)
        else:
          content = None

        self.delegate(channel, Message(channel, frame, content))
    except:
      self.fatal()

class Requester:

  def __init__(self, writer):
    self.write = writer
    self.sequence = Sequence(1)
    self.mark = 0
    # request_id -> listener
    self.outstanding = {}

  def request(self, method, listener, content = None):
    frame = Request(self.sequence.next(), self.mark, method)
    self.outstanding[frame.id] = listener
    self.write(frame, content)

  def receive(self, channel, frame):
    listener = self.outstanding.pop(frame.id)
    listener(channel, frame)

class Responder:

  def __init__(self, writer):
    self.write = writer
    self.sequence = Sequence(1)

  def respond(self, method, request):
    if isinstance(request, Method):
      self.write(method)
    else:
      # XXX: batching
      frame = Response(self.sequence.next(), request.id, 0, method)
      self.write(frame)

class Closed(Exception): pass

class Channel:

  def __init__(self, id, outgoing, spec):
    self.id = id
    self.outgoing = outgoing
    self.spec = spec
    self.incoming = Queue(0)
    self.responses = Queue(0)
    self.queue = None
    self.closed = False
    self.reason = None

    self.requester = Requester(self.write)
    self.responder = Responder(self.write)

    # XXX: better switch
    self.reliable = False
    self.synchronous = True

  def close(self, reason):
    if self.closed:
      return
    self.closed = True
    self.reason = reason
    self.incoming.close()
    self.responses.close()

  def write(self, frame, content = None):
    if self.closed:
      raise Closed(self.reason)
    frame.channel = self.id
    self.outgoing.put(frame)
    if (isinstance(frame, (Method, Request))
        and content == None
        and frame.method_type.content):
      content = Content()
    if content != None:
      self.write_content(frame.method_type.klass, content)

  def write_content(self, klass, content):
    size = content.size()
    header = Header(klass, content.weight(), size, content.properties)
    self.write(header)
    for child in content.children:
      self.write_content(klass, child)
    # should split up if content.body exceeds max frame size
    if size > 0:
      self.write(Body(content.body))

  def receive(self, frame, work):
    if isinstance(frame, Method):
      if frame.method.response:
        self.queue = self.responses
      else:
        self.queue = self.incoming
        work.put(self.incoming)
    elif isinstance(frame, Request):
      self.queue = self.incoming
      work.put(self.incoming)
    elif isinstance(frame, Response):
      self.requester.receive(self, frame)
      if frame.method_type.content:
        self.queue = self.responses
      return
    self.queue.put(frame)

  def queue_response(self, channel, frame):
    channel.responses.put(frame.method)

  def request(self, method, listener, content = None):
    self.requester.request(method, listener, content)

  def respond(self, method, request):
    self.responder.respond(method, request)

  def invoke(self, type, args, kwargs):
    content = kwargs.pop("content", None)
    frame = Method(type, type.arguments(*args, **kwargs))
    if self.reliable:
      if not self.synchronous:
        future = Future()
        self.request(frame, future.put_response, content)
        if not frame.method.responses: return None
        else: return future
      
      self.request(frame, self.queue_response, content)
      if not frame.method.responses:
        return None
      try:
        resp = self.responses.get()
        if resp.method_type.content:
          return Message(self, resp, read_content(self.responses))
        else:
          return Message(self, resp)

      except QueueClosed, e:
        if self.closed:
          raise Closed(self.reason)
        else:
          raise e
    else:
      return self.invoke_method(frame, content)

  def invoke_method(self, frame, content = None):
    self.write(frame, content)

    try:
      # here we depend on all nowait fields being named nowait
      f = frame.method.fields.byname["nowait"]
      nowait = frame.args[frame.method.fields.index(f)]
    except KeyError:
      nowait = False

    try:
      if not nowait and frame.method.responses:
        resp = self.responses.get()
        if resp.method.content:
          content = read_content(self.responses)
        else:
          content = None
        if resp.method in frame.method.responses:
          return Message(self, resp, content)
        else:
          raise ValueError(resp)
    except QueueClosed, e:
      if self.closed:
        raise Closed(self.reason)
      else:
        raise e

  def __getattr__(self, name):
    type = self.spec.method(name)
    if type == None: raise AttributeError(name)
    method = lambda *args, **kwargs: self.invoke(type, args, kwargs)
    self.__dict__[name] = method
    return method

def read_content(queue):
  header = queue.get()
  children = []
  for i in range(header.weight):
    children.append(read_content(queue))
  size = header.size
  read = 0
  buf = StringIO()
  while read < size:
    body = queue.get()
    content = body.content
    buf.write(content)
    read += len(content)
  return Content(buf.getvalue(), children, header.properties.copy())

class Future:
  def __init__(self):
    self.completed = threading.Event()

  def put_response(self, channel, response):
    self.response = response
    self.completed.set()

  def get_response(self, timeout=None):
    self.completed.wait(timeout)
    return self.response

  def is_complete(self):
    return self.completed.isSet()
