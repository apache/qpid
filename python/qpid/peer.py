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

import thread, traceback, socket, sys, logging
from connection import Frame, EOF, Method, Header, Body
from message import Message
from queue import Queue, Closed as QueueClosed
from content import Content
from cStringIO import StringIO

class Peer:

  def __init__(self, conn, delegate):
    self.conn = conn
    self.delegate = delegate
    self.outgoing = Queue(0)
    self.work = Queue(0)
    self.channels = {}
    self.Channel = type("Channel%s" % conn.spec.klass.__name__,
                        (Channel, conn.spec.klass), {})
    self.lock = thread.allocate_lock()

  def channel(self, id):
    self.lock.acquire()
    try:
      try:
        ch = self.channels[id]
      except KeyError:
        ch = self.Channel(id, self.outgoing)
        self.channels[id] = ch
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
        ch.dispatch(frame, self.work)
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
        self.dispatch(self.work.get())
    except QueueClosed, e:
      self.close(e)
    except:
      self.fatal()

  def dispatch(self, queue):
    frame = queue.get()
    channel = self.channel(frame.channel)
    payload = frame.payload
    if payload.method.content:
      content = read_content(queue)
    else:
      content = None
    # Let the caller deal with exceptions thrown here.
    message = Message(payload.method, payload.args, content)
    self.delegate.dispatch(channel, message)

class Closed(Exception): pass

class Channel:

  def __init__(self, id, outgoing):
    self.id = id
    self.outgoing = outgoing
    self.incoming = Queue(0)
    self.responses = Queue(0)
    self.queue = None
    self.closed = False
    self.reason = None

  def close(self, reason):
    if self.closed:
      return
    self.closed = True
    self.reason = reason
    self.incoming.close()
    self.responses.close()

  def dispatch(self, frame, work):
    payload = frame.payload
    if isinstance(payload, Method):
      if payload.method.response:
        self.queue = self.responses
      else:
        self.queue = self.incoming
        work.put(self.incoming)
    self.queue.put(frame)

  def invoke(self, method, args, content = None):
    if self.closed:
      raise Closed(self.reason)
    frame = Frame(self.id, Method(method, *args))
    self.outgoing.put(frame)

    if method.content:
      if content == None:
        content = Content()
      self.write_content(method.klass, content, self.outgoing)

    try:
      # here we depend on all nowait fields being named nowait
      f = method.fields.byname["nowait"]
      nowait = args[method.fields.index(f)]
    except KeyError:
      nowait = False

    try:
      if not nowait and method.responses:
        resp = self.responses.get().payload
        if resp.method.content:
          content = read_content(self.responses)
        else:
          content = None
        if resp.method in method.responses:
          return Message(resp.method, resp.args, content)
        else:
          raise ValueError(resp)
    except QueueClosed, e:
      if self.closed:
        raise Closed(self.reason)
      else:
        raise e

  def write_content(self, klass, content, queue):
    size = content.size()
    header = Frame(self.id, Header(klass, content.weight(), size, **content.properties))
    queue.put(header)
    for child in content.children:
      self.write_content(klass, child, queue)
    # should split up if content.body exceeds max frame size
    if size > 0:
      queue.put(Frame(self.id, Body(content.body)))

def read_content(queue):
  frame = queue.get()
  header = frame.payload
  children = []
  for i in range(header.weight):
    children.append(read_content(queue))
  size = header.size
  read = 0
  buf = StringIO()
  while read < size:
    body = queue.get()
    content = body.payload.content
    buf.write(content)
    read += len(content)
  return Content(buf.getvalue(), children, header.properties.copy())
