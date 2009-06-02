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
A candidate high level messaging API for python.

Areas that still need work:

  - asynchronous send
  - asynchronous error notification
  - definition of the arguments for L{Session.sender} and L{Session.receiver}
  - standard L{Message} properties
  - L{Message} content encoding
  - protocol negotiation/multiprotocol impl
"""

import connection, time, sys, traceback
from codec010 import StringCodec
from datatypes import timestamp, uuid4, RangedSet, Message as Message010
from logging import getLogger
from session import Client, INCOMPLETE
from spec import SPEC
from threading import Thread, RLock, Condition
from util import connect

log = getLogger("qpid.messaging")

static = staticmethod

def synchronized(meth):
  def sync_wrapper(self, *args, **kwargs):
    self.lock()
    try:
      return meth(self, *args, **kwargs)
    finally:
      self.unlock()
  return sync_wrapper

class Lockable(object):

  def lock(self):
    self._lock.acquire()

  def unlock(self):
    self._lock.release()

  def wait(self, predicate, timeout=None):
    passed = 0
    start = time.time()
    while not predicate():
      if timeout is None:
        # using the timed wait prevents keyboard interrupts from being
        # blocked while waiting
        self._condition.wait(3)
      elif passed < timeout:
        self._condition.wait(timeout - passed)
      else:
        return False
      passed = time.time() - start
    return True

  def notify(self):
    self._condition.notify()

  def notifyAll(self):
    self._condition.notifyAll()

def default(value, default):
  if value is None:
    return default
  else:
    return value

AMQP_PORT = 5672
AMQPS_PORT = 5671

class Constant:

  def __init__(self, name, value=None):
    self.name = name
    self.value = value

  def __repr__(self):
    return self.name

UNLIMITED = Constant("UNLIMITED", 0xFFFFFFFFL)

class Connection(Lockable):

  """
  A Connection manages a group of L{Sessions<Session>} and connects
  them with a remote endpoint.
  """

  @static
  def open(host, port=None):
    """
    Creates an AMQP connection and connects it to the given host and port.

    @type host: str
    @param host: the name or ip address of the remote host
    @type port: int
    @param port: the port number of the remote host
    @rtype: Connection
    @return: a connected Connection
    """
    conn = Connection(host, port)
    conn.connect()
    return conn

  def __init__(self, host, port=None):
    """
    Creates a connection. A newly created connection must be connected
    with the Connection.connect() method before it can be started.

    @type host: str
    @param host: the name or ip address of the remote host
    @type port: int
    @param port: the port number of the remote host
    @rtype: Connection
    @return: a disconnected Connection
    """
    self.host = host
    self.port = default(port, AMQP_PORT)
    self.started = False
    self._conn = None
    self.id = str(uuid4())
    self.session_counter = 0
    self.sessions = {}
    self._lock = RLock()
    self._condition = Condition(self._lock)

  @synchronized
  def session(self, name=None):
    """
    Creates or retrieves the named session. If the name is omitted or
    None, then a unique name is chosen based on a randomly generated
    uuid.

    @type name: str
    @param name: the session name
    @rtype: Session
    @return: the named Session
    """

    if name is None:
      name = "%s:%s" % (self.id, self.session_counter)
      self.session_counter += 1
    else:
      name = "%s:%s" % (self.id, name)

    if self.sessions.has_key(name):
      return self.sessions[name]
    else:
      ssn = Session(self, name, self.started)
      self.sessions[name] = ssn
      if self._conn is not None:
        ssn._attach()
      return ssn

  @synchronized
  def _remove_session(self, ssn):
    del self.sessions[ssn.name]

  @synchronized
  def connect(self):
    """
    Connect to the remote endpoint.
    """
    if self._conn is not None:
      return
    self._socket = connect(self.host, self.port)
    self._conn = connection.Connection(self._socket)
    self._conn.start()

    for ssn in self.sessions.values():
      ssn._attach()

  @synchronized
  def disconnect(self):
    """
    Disconnect from the remote endpoint.
    """
    if self._conn is not None:
      self._conn.close()
      self._conn = None
    for ssn in self.sessions.values():
      ssn._disconnected()

  @synchronized
  def connected(self):
    """
    Return true if the connection is connected, false otherwise.
    """
    return self._conn is not None

  @synchronized
  def start(self):
    """
    Start incoming message delivery for all sessions.
    """
    self.started = True
    for ssn in self.sessions.values():
      ssn.start()

  @synchronized
  def stop(self):
    """
    Stop incoming message deliveries for all sessions.
    """
    for ssn in self.sessions.values():
      ssn.stop()
    self.started = False

  @synchronized
  def close(self):
    """
    Close the connection and all sessions.
    """
    for ssn in self.sessions.values():
      ssn.close()
    self.disconnect()

class Pattern:
  """
  The pattern filter matches the supplied wildcard pattern against a
  message subject.
  """

  def __init__(self, value):
    self.value = value

  def _bind(self, ssn, exchange, queue):
    ssn.exchange_bind(exchange=exchange, queue=queue,
                      binding_key=self.value.replace("*", "#"))

FILTER_DEFAULTS = {
  "topic": Pattern("*")
  }

def delegate(session):
  class Delegate(Client):

    def message_transfer(self, cmd, headers, body):
      session._message_transfer(cmd, headers, body)
  return Delegate

class Session(Lockable):

  """
  Sessions provide a linear context for sending and receiving
  messages, and manage various Senders and Receivers.
  """

  def __init__(self, connection, name, started):
    self.connection = connection
    self.name = name
    self.started = started
    self._ssn = None
    self.senders = []
    self.receivers = []
    self.closing = False
    self.incoming = []
    self.closed = False
    self.unacked = []
    self._lock = RLock()
    self._condition = Condition(self._lock)
    self.thread = Thread(target = self.run)
    self.thread.setDaemon(True)
    self.thread.start()

  def __repr__(self):
    return "<Session %s>" % self.name

  def _attach(self):
    self._ssn = self.connection._conn.session(self.name, delegate=delegate(self))
    self._ssn.auto_sync = False
    self._ssn.invoke_lock = self._lock
    self._ssn.lock = self._lock
    self._ssn.condition = self._condition
    for link in self.senders + self.receivers:
      link._link()

  def _disconnected(self):
    self._ssn = None
    for link in self.senders + self.receivers:
      link._disconnected()

  @synchronized
  def _message_transfer(self, cmd, headers, body):
    m = Message010(body)
    m.headers = headers
    m.id = cmd.id
    msg = self._decode(m)
    rcv = self.receivers[int(cmd.destination)]
    msg._receiver = rcv
    log.debug("RECV [%s] %s", self, msg)
    self.incoming.append(msg)
    self.notifyAll()
    return INCOMPLETE

  def _decode(self, message):
    dp = message.get("delivery_properties")
    mp = message.get("message_properties")
    ap = mp.application_headers
    enc, dec = get_codec(mp.content_type)
    content = dec(message.body)
    msg = Message(content)
    msg.id = mp.message_id
    if ap is not None:
      msg.to = ap.get("to")
      msg.subject = ap.get("subject")
    msg.user_id = mp.user_id
    if mp.reply_to is not None:
      msg.reply_to = reply_to2addr(mp.reply_to)
    msg.correlation_id = mp.correlation_id
    msg.properties = mp.application_headers
    msg.content_type = mp.content_type
    msg._transfer_id = message.id
    return msg

  def _exchange_query(self, address):
    # XXX: auto sync hack is to avoid deadlock on future
    result = self._ssn.exchange_query(name=address, sync=True)
    self._ssn.sync()
    return result.get()

  @synchronized
  def sender(self, target):
    """
    Creates a L{Sender} that may be used to send L{Messages<Message>}
    to the specified target.

    @type target: str
    @param target: the target to which messages will be sent
    @rtype: Sender
    @return: a new Sender for the specified target
    """
    sender = Sender(self, len(self.senders), target)
    self.senders.append(sender)
    if self._ssn is not None:
      sender._link()
    return sender

  @synchronized
  def receiver(self, source, filter=None):
    """
    Creates a receiver that may be used to actively fetch or to listen
    for the arrival of L{Messages<Message>} from the specified source.

    @type source: str
    @param source: the source of L{Messages<Message>}
    @rtype: Receiver
    @return: a new Receiver for the specified source
    """
    receiver = Receiver(self, len(self.receivers), source, filter,
                        self.started)
    self.receivers.append(receiver)
    if self._ssn is not None:
      receiver._link()
    return receiver

  @synchronized
  def _count(self, predicate):
    result = 0
    for msg in self.incoming:
      if predicate(msg):
        result += 1
    return result

  def _peek(self, predicate):
    for msg in self.incoming:
      if predicate(msg):
        return msg

  def _pop(self, predicate):
    i = 0
    while i < len(self.incoming):
      msg = self.incoming[i]
      if predicate(msg):
        del self.incoming[i]
        return msg
      else:
        i += 1

  @synchronized
  def _get(self, predicate, timeout=None):
    if self.wait(lambda: ((self._peek(predicate) is not None) or self.closing),
                 timeout):
      msg = self._pop(predicate)
      if msg is not None:
        self.unacked.append(msg)
        log.debug("RETR [%s] %s", self, msg)
        return msg
    return None

  @synchronized
  def acknowledge(self, message=None):
    """
    Acknowledge the given L{Message}. If message is None, then all
    unackednowledged messages on the session are acknowledged.

    @type message: Message
    @param message: the message to acknowledge or None
    """
    if message is None:
      messages = self.unacked
    else:
      messages = [message]

    ids = RangedSet(*[m._transfer_id for m in self.unacked])
    for range in ids:
      self._ssn.receiver._completed.add_range(range)
    self._ssn.channel.session_completed(self._ssn.receiver._completed)
    self._ssn.message_accept(ids, sync=True)
    self._ssn.sync()

    for m in messages:
      try:
        self.unacked.remove(m)
      except ValueError:
        pass

  @synchronized
  def start(self):
    """
    Start incoming message delivery for the session.
    """
    self.started = True
    for rcv in self.receivers:
      rcv.start()

  @synchronized
  def stop(self):
    """
    Stop incoming message delivery for the session.
    """
    for rcv in self.receivers:
      rcv.stop()
    # TODO: think about stopping individual receivers in listen mode
    self.wait(lambda: self._peek(self._pred) is None)
    self.started = False

  def _pred(self, m):
    return m._receiver.listener is not None

  @synchronized
  def run(self):
    try:
      while True:
        msg = self._get(self._pred)
        if msg is None:
          break;
        else:
          msg._receiver.listener(msg)
          if self._peek(self._pred) is None:
            self.notifyAll()
    finally:
      self.closed = True
      self.notifyAll()

  @synchronized
  def close(self):
    """
    Close the session.
    """
    for link in self.receivers + self.senders:
      link.close()

    self.closing = True
    self.notifyAll()
    self.wait(lambda: self.closed)
    while self.thread.isAlive():
      self.thread.join(3)
    self.thread = None
    self._ssn.close()
    self._ssn = None
    self.connection._remove_session(self)

def parse_addr(address):
  parts = address.split("/", 1)
  if len(parts) == 1:
    return parts[0], None
  else:
    return parts[0], parts[i1]

def reply_to2addr(reply_to):
  if reply_to.routing_key is None:
    return reply_to.exchange
  elif reply_to.exchange in (None, ""):
    return reply_to.routing_key
  else:
    return "%s/%s" % (reply_to.exchange, reply_to.routing_key)

class Disconnected(Exception):
  """
  Exception raised when an operation is attempted that is illegal when
  disconnected.
  """
  pass

class Sender(Lockable):

  """
  Sends outgoing messages.
  """

  def __init__(self, session, index, target):
    self.session = session
    self.index = index
    self.target = target
    self.closed = False
    self._ssn = None
    self._exchange = None
    self._routing_key = None
    self._subject = None
    self._lock = self.session._lock
    self._condition = self.session._condition

  def _link(self):
    self._ssn = self.session._ssn
    node, self._subject = parse_addr(self.target)
    result = self.session._exchange_query(node)
    if result.not_found:
      # XXX: should check 'create' option
      self._ssn.queue_declare(queue=node, durable=False, sync=True)
      self._ssn.sync()
      self._exchange = ""
      self._routing_key = node
    else:
      self._exchange = node
      self._routing_key = self._subject

  def _disconnected(self):
    self._ssn = None

  @synchronized
  def send(self, object):
    """
    Send a message. If the object passed in is of type L{unicode},
    L{str}, L{list}, or L{dict}, it will automatically be wrapped in a
    L{Message} and sent. If it is of type L{Message}, it will be sent
    directly.

    @type object: unicode, str, list, dict, Message
    @param object: the message or content to send
    """

    if self._ssn is None:
      raise Disconnected()

    if isinstance(object, Message):
      message = object
    else:
      message = Message(object)
    # XXX: what if subject is specified for a normal queue?
    if self._routing_key is None:
      rk = message.subject
    else:
      rk = self._routing_key
    # XXX: do we need to query to figure out how to create the reply-to interoperably?
    if message.reply_to:
      rt = self._ssn.reply_to(*parse_addr(message.reply_to))
    else:
      rt = None
    dp = self._ssn.delivery_properties(routing_key=rk)
    mp = self._ssn.message_properties(message_id=message.id,
                                      user_id=message.user_id,
                                      reply_to=rt,
                                      correlation_id=message.correlation_id,
                                      content_type=message.content_type,
                                      application_headers=message.properties)
    if message.subject is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers["subject"] = message.subject
    if message.to is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers["to"] = message.to
    enc, dec = get_codec(message.content_type)
    body = enc(message.content)
    self._ssn.message_transfer(destination=self._exchange,
                               message=Message010(dp, mp, body),
                               sync=True)
    log.debug("SENT [%s] %s", self.session, message)
    self._ssn.sync()

  @synchronized
  def close(self):
    """
    Close the Sender.
    """
    if not self.closed:
      self.session.senders.remove(self)
      self.closed = True

class Empty(Exception):
  """
  Exception raised by L{Receiver.fetch} when there is no message
  available within the alloted time.
  """
  pass

class Receiver(Lockable):

  """
  Receives incoming messages from a remote source. Messages may be
  actively fetched with L{fetch} or a listener may be installed with
  L{listen}.
  """

  def __init__(self, session, index, source, filter, started):
    self.session = session
    self.index = index
    self.destination = str(self.index)
    self.source = source
    self.filter = filter
    self.started = started
    self.capacity = UNLIMITED
    self.closed = False
    self.listener = None
    self._ssn = None
    self._queue = None
    self._lock = self.session._lock
    self._condition = self.session._condition

  def _link(self):
    self._ssn = self.session._ssn
    result = self.session._exchange_query(self.source)
    if result.not_found:
      self._queue = self.source
      # XXX: should check 'create' option
      self._ssn.queue_declare(queue=self._queue, durable=False)
    else:
      self._queue = "%s.%s" % (self.session.name, self.destination)
      self._ssn.queue_declare(queue=self._queue, durable=False, exclusive=True, auto_delete=True)
      if self.filter is None:
        f = FILTER_DEFAULTS[result.type]
      else:
        f = self.filter
      f._bind(self._ssn, self.source, self._queue)
    self._ssn.message_subscribe(queue=self._queue, destination=self.destination,
                                sync=True)
    self._ssn.message_set_flow_mode(self.destination, self._ssn.flow_mode.credit)
    self._ssn.sync()
    if self.started:
      self._start()

  def _disconnected(self):
    self._ssn = None

  @synchronized
  def pending(self):
    return self.session._count(self._pred)

  def _capacity(self):
    if not self.started:
      return 0
    elif self.capacity is UNLIMITED:
      return self.capacity.value
    else:
      return self.capacity

  @synchronized
  def listen(self, listener=None):
    """
    Sets the message listener for this receiver.

    @type listener: callable
    @param listener: a callable object to be notified on message arrival
    """
    self.listener = listener

  def _pred(self, msg):
    return msg._receiver == self

  @synchronized
  def fetch(self, timeout=None):
    """
    Fetch and return a single message. A timeout of None will block
    forever waiting for a message to arrive, a timeout of zero will
    return immediately if no messages are available.

    @type timeout: float
    @param timeout: the time to wait for a message to be available
    """
    if self.capacity is not UNLIMITED or not self.started:
      self._ssn.message_flow(self.destination, self._ssn.credit_unit.byte,
                             UNLIMITED.value)
      self._ssn.message_flow(self.destination, self._ssn.credit_unit.message, 1)
    msg = self.session._get(self._pred, timeout=timeout)
    if msg is None:
      self._ssn.message_flush(self.destination)
      self._start()
      self._ssn.sync()
      msg = self.session._get(self._pred, timeout=0)
      if msg is None:
        raise Empty()
    return msg

  def _start(self):
    self._ssn.message_flow(self.destination, self._ssn.credit_unit.byte, UNLIMITED.value)
    self._ssn.message_flow(self.destination, self._ssn.credit_unit.message, self._capacity())

  @synchronized
  def start(self):
    """
    Start incoming message delivery for this receiver.
    """
    self.started = True
    if self._ssn is not None:
      self._start()

  def _stop(self):
    self._ssn.message_stop(self.destination)

  @synchronized
  def stop(self):
    """
    Stop incoming message delivery for this receiver.
    """
    if self._ssn is not None:
      self._stop()
    self.started = False

  @synchronized
  def close(self):
    """
    Close the receiver.
    """
    if not self.closed:
      self.closed = True
      self._ssn.message_cancel(self.destination, sync=True)
      self._ssn.sync()
      self.session.receivers.remove(self)



def codec(name):
  type = SPEC.named[name]

  def encode(x):
    sc = StringCodec(SPEC)
    type.encode(sc, x)
    return sc.encoded

  def decode(x):
    sc = StringCodec(SPEC, x)
    return type.decode(sc)

  return encode, decode

TYPE_MAPPINGS={
  dict: "amqp/map",
  list: "amqp/list",
  unicode: "text/plain; charset=utf8",
  buffer: None,
  str: None,
  None.__class__: None
  }

TYPE_CODEC={
  "amqp/map": codec("map"),
  "amqp/list": codec("list"),
  "text/plain; charset=utf8": (lambda x: x.encode("utf8"), lambda x: x.decode("utf8")),
  None: (lambda x: x, lambda x: x)
  }

def get_type(content):
  return TYPE_MAPPINGS[content.__class__]

def get_codec(content_type):
  return TYPE_CODEC[content_type]

class Message:

  """
  A message consists of a standard set of fields, an application
  defined set of properties, and some content.

  @type id: str
  @ivar id: the message id
  @type user_id: ???
  @ivar user_id: the user-id of the message producer
  @type to: ???
  @ivar to: ???
  @type reply_to: ???
  @ivar reply_to: ???
  @type correlation_id: str
  @ivar correlation_id: a correlation-id for the message
  @type properties: dict
  @ivar properties: application specific message properties
  @type content_type: str
  @ivar content_type: the content-type of the message
  @type content: str, unicode, buffer, dict, list
  @ivar content: the message content
  """

  def __init__(self, content=None):
    """
    Construct a new message with the supplied content. The
    content-type of the message will be automatically inferred from
    type of the content parameter.

    @type content: str, unicode, buffer, dict, list
    @param content: the message content
    """
    self.id = None
    self.subject = None
    self.user_id = None
    self.to = None
    self.reply_to = None
    self.correlation_id = None
    self.properties = {}
    self.content_type = get_type(content)
    self.content = content

  def __repr__(self):
    return "Message(%r)" % self.content

__all__ = ["Connection", "Pattern", "Session", "Sender", "Receiver", "Message",
           "Empty", "timestamp", "uuid4"]
