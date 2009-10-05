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

from codec010 import StringCodec
from concurrency import synchronized, Waiter
from datatypes import timestamp, uuid4, Serial
from logging import getLogger
from ops import PRIMITIVE
from threading import Thread, RLock, Condition
from util import default

log = getLogger("qpid.messaging")

static = staticmethod

AMQP_PORT = 5672
AMQPS_PORT = 5671

class Constant:

  def __init__(self, name, value=None):
    self.name = name
    self.value = value

  def __repr__(self):
    return self.name

UNLIMITED = Constant("UNLIMITED", 0xFFFFFFFFL)

class ConnectionError(Exception):
  """
  The base class for all connection related exceptions.
  """
  pass

class ConnectError(ConnectionError):
  """
  Exception raised when there is an error connecting to the remote
  peer.
  """
  pass

class Connection:

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
    self.id = str(uuid4())
    self.session_counter = 0
    self.sessions = {}
    self.reconnect = False
    self._connected = False
    self._lock = RLock()
    self._condition = Condition(self._lock)
    self._waiter = Waiter(self._condition)
    self._modcount = Serial(0)
    self.error = None
    from driver import Driver
    self._driver = Driver(self)
    self._driver.start()

  def _wait(self, predicate, timeout=None):
    return self._waiter.wait(predicate, timeout=timeout)

  def _wakeup(self):
    self._modcount += 1
    self._driver.wakeup()

  def _check_error(self, exc=ConnectionError):
    if self.error:
      raise exc(*self.error)

  def _ewait(self, predicate, timeout=None, exc=ConnectionError):
    result = self._wait(lambda: self.error or predicate(), timeout)
    self._check_error(exc)
    return result

  @synchronized
  def session(self, name=None, transactional=False):
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
      ssn = Session(self, name, self.started, transactional=transactional)
      self.sessions[name] = ssn
      self._wakeup()
      return ssn

  @synchronized
  def _remove_session(self, ssn):
    del self.sessions[ssn.name]

  @synchronized
  def connect(self):
    """
    Connect to the remote endpoint.
    """
    self._connected = True
    self._wakeup()
    self._ewait(lambda: self._driver._connected, exc=ConnectError)

  @synchronized
  def disconnect(self):
    """
    Disconnect from the remote endpoint.
    """
    self._connected = False
    self._wakeup()
    self._ewait(lambda: not self._driver._connected)

  @synchronized
  def connected(self):
    """
    Return true if the connection is connected, false otherwise.
    """
    return self._connected

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

  # XXX: this should become part of the driver
  def _bind(self, ssn, exchange, queue):
    ssn.exchange_bind(exchange=exchange, queue=queue,
                      binding_key=self.value.replace("*", "#"))

class SessionError(Exception):
  pass

class Disconnected(SessionError):
  """
  Exception raised when an operation is attempted that is illegal when
  disconnected.
  """
  pass

class NontransactionalSession(SessionError):
  """
  Exception raised when commit or rollback is attempted on a non
  transactional session.
  """
  pass

class TransactionAborted(SessionError):
  pass

class Session:

  """
  Sessions provide a linear context for sending and receiving
  messages, and manage various Senders and Receivers.
  """

  def __init__(self, connection, name, started, transactional):
    self.connection = connection
    self.name = name
    self.started = started

    self.transactional = transactional

    self.committing = False
    self.committed = True
    self.aborting = False
    self.aborted = False

    self.senders = []
    self.receivers = []
    self.outgoing = []
    self.incoming = []
    self.unacked = []
    self.acked = []
    # XXX: I hate this name.
    self.ack_capacity = UNLIMITED

    self.closing = False
    self.closed = False

    self._lock = connection._lock
    self.running = True
    self.thread = Thread(target = self.run)
    self.thread.setDaemon(True)
    self.thread.start()

  def __repr__(self):
    return "<Session %s>" % self.name

  def _wait(self, predicate, timeout=None):
    return self.connection._wait(predicate, timeout=timeout)

  def _wakeup(self):
    self.connection._wakeup()

  def _check_error(self, exc=SessionError):
    self.connection._check_error(exc)

  def _ewait(self, predicate, timeout=None, exc=SessionError):
    return self.connection._ewait(predicate, timeout, exc)

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
    self._wakeup()
    # XXX: because of the lack of waiting here we can end up getting
    # into the driver loop with messages sent for senders that haven't
    # been linked yet, something similar can probably happen for
    # receivers
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
    self._wakeup()
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
    if self._wait(lambda: ((self._peek(predicate) is not None) or self.closing),
                  timeout):
      msg = self._pop(predicate)
      if msg is not None:
        msg._receiver.returned += 1
        self.unacked.append(msg)
        log.debug("RETR [%s] %s", self, msg)
        return msg
    return None

  @synchronized
  def acknowledge(self, message=None, sync=True):
    """
    Acknowledge the given L{Message}. If message is None, then all
    unacknowledged messages on the session are acknowledged.

    @type message: Message
    @param message: the message to acknowledge or None
    @type sync: boolean
    @param sync: if true then block until the message(s) are acknowledged
    """
    if message is None:
      messages = self.unacked[:]
    else:
      messages = [message]

    for m in messages:
      if self.ack_capacity is not UNLIMITED:
        if self.ack_capacity <= 0:
          # XXX: this is currently a SendError, maybe it should be a SessionError?
          raise InsufficientCapacity("ack_capacity = %s" % self.ack_capacity)
        self._wakeup()
        self._ewait(lambda: len(self.acked) < self.ack_capacity)
      self.unacked.remove(m)
      self.acked.append(m)

    self._wakeup()
    if sync:
      self._ewait(lambda: not [m for m in messages if m in self.acked])

  @synchronized
  def commit(self):
    """
    Commit outstanding transactional work. This consists of all
    message sends and receives since the prior commit or rollback.
    """
    if not self.transactional:
      raise NontransactionalSession()
    self.committing = True
    self._wakeup()
    self._ewait(lambda: not self.committing)
    if self.aborted:
      raise TransactionAborted()
    assert self.committed

  @synchronized
  def rollback(self):
    """
    Rollback outstanding transactional work. This consists of all
    message sends and receives since the prior commit or rollback.
    """
    if not self.transactional:
      raise NontransactionalSession()
    self.aborting = True
    self._wakeup()
    self._ewait(lambda: not self.aborting)
    assert self.aborted

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
    self._wait(lambda: self._peek(self._pred) is None)
    self.started = False

  def _pred(self, m):
    return m._receiver.listener is not None

  @synchronized
  def run(self):
    self.running = True
    try:
      while True:
        msg = self._get(self._pred)
        if msg is None:
          break;
        else:
          msg._receiver.listener(msg)
          if self._peek(self._pred) is None:
            self.connection._waiter.notifyAll()
    finally:
      self.running = False
      self.connection._waiter.notifyAll()

  @synchronized
  def close(self):
    """
    Close the session.
    """
    for link in self.receivers + self.senders:
      link.close()

    self.closing = True
    self._wakeup()
    self._ewait(lambda: self.closed and not self.running)
    while self.thread.isAlive():
      self.thread.join(3)
    self.thread = None
    # XXX: should be able to express this condition through API calls
    self._ewait(lambda: not self.outgoing and not self.acked)
    self.connection._remove_session(self)

class SendError(SessionError):
  pass

class InsufficientCapacity(SendError):
  pass

class Sender:

  """
  Sends outgoing messages.
  """

  def __init__(self, session, index, target):
    self.session = session
    self.index = index
    self.target = target
    self.capacity = UNLIMITED
    self.queued = Serial(0)
    self.acked = Serial(0)
    self.closed = False
    self._lock = self.session._lock

  def _wakeup(self):
    self.session._wakeup()

  def _check_error(self, exc=SendError):
    self.session._check_error(exc)

  def _ewait(self, predicate, timeout=None, exc=SendError):
    return self.session._ewait(predicate, timeout, exc)

  @synchronized
  def pending(self):
    """
    Returns the number of messages awaiting acknowledgment.
    @rtype: int
    @return: the number of unacknowledged messages
    """
    return self.queued - self.acked

  @synchronized
  def send(self, object, sync=True, timeout=None):
    """
    Send a message. If the object passed in is of type L{unicode},
    L{str}, L{list}, or L{dict}, it will automatically be wrapped in a
    L{Message} and sent. If it is of type L{Message}, it will be sent
    directly. If the sender capacity is not L{UNLIMITED} then send
    will block until there is available capacity to send the message.
    If the timeout parameter is specified, then send will throw an
    L{InsufficientCapacity} exception if capacity does not become
    available within the specified time.

    @type object: unicode, str, list, dict, Message
    @param object: the message or content to send

    @type sync: boolean
    @param sync: if true then block until the message is sent

    @type timeout: float
    @param timeout: the time to wait for available capacity
    """

    if not self.session.connection._connected or self.session.closing:
      raise Disconnected()

    if isinstance(object, Message):
      message = object
    else:
      message = Message(object)

    if self.capacity is not UNLIMITED:
      if self.capacity <= 0:
        raise InsufficientCapacity("capacity = %s" % self.capacity)
      if not self._ewait(lambda: self.pending() < self.capacity, timeout=timeout):
        raise InsufficientCapacity("capacity = %s" % self.capacity)

    # XXX: what if we send the same message to multiple senders?
    message._sender = self
    self.session.outgoing.append(message)
    self.queued += 1
    mno = self.queued

    self._wakeup()

    if sync:
      self._ewait(lambda: self.acked >= mno)
      assert message not in self.session.outgoing

  @synchronized
  def close(self):
    """
    Close the Sender.
    """
    # XXX: should make driver do something here
    if not self.closed:
      self.session.senders.remove(self)
      self.closed = True

class ReceiveError(SessionError):
  pass

class Empty(ReceiveError):
  """
  Exception raised by L{Receiver.fetch} when there is no message
  available within the alloted time.
  """
  pass

class Receiver:

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
    self.granted = Serial(0)
    self.drain = False
    self.impending = Serial(0)
    self.received = Serial(0)
    self.returned = Serial(0)

    self.closing = False
    self.closed = False
    self.listener = None
    self._lock = self.session._lock

  def _wakeup(self):
    self.session._wakeup()

  def _check_error(self, exc=ReceiveError):
    self.session._check_error(exc)

  def _ewait(self, predicate, timeout=None, exc=ReceiveError):
    return self.session._ewait(predicate, timeout, exc)

  @synchronized
  def pending(self):
    """
    Returns the number of messages available to be fetched by the
    application.

    @rtype: int
    @return: the number of available messages
    """
    return self.received - self.returned

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
    if self._capacity() == 0:
      self.granted = self.returned + 1
      self._wakeup()
    self._ewait(lambda: self.impending >= self.granted)
    msg = self.session._get(self._pred, timeout=timeout)
    if msg is None:
      self.drain = True
      self.granted = self.received
      self._wakeup()
      self._ewait(lambda: self.impending == self.received)
      self.drain = False
      self._grant()
      self._wakeup()
      msg = self.session._get(self._pred, timeout=0)
      if msg is None:
        raise Empty()
    elif self._capacity() not in (0, UNLIMITED.value):
      self.granted += 1
      self._wakeup()
    return msg

  def _grant(self):
    if self.started:
      if self.capacity is UNLIMITED:
        self.granted = UNLIMITED
      else:
        self.granted = self.received + self._capacity()
    else:
      self.granted = self.received


  @synchronized
  def start(self):
    """
    Start incoming message delivery for this receiver.
    """
    self.started = True
    self._grant()
    self._wakeup()

  @synchronized
  def stop(self):
    """
    Stop incoming message delivery for this receiver.
    """
    self.started = False
    self._grant()
    self._wakeup()
    self._ewait(lambda: self.impending == self.received)

  @synchronized
  def close(self):
    """
    Close the receiver.
    """
    self.closing = True
    self._wakeup()
    try:
      self._ewait(lambda: self.closed)
    finally:
      self.session.receivers.remove(self)

def codec(name):
  type = PRIMITIVE[name]

  def encode(x):
    sc = StringCodec()
    sc.write_primitive(type, x)
    return sc.encoded

  def decode(x):
    sc = StringCodec(x)
    return sc.read_primitive(type)

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
    self.durable = False
    self.properties = {}
    self.content_type = get_type(content)
    self.content = content

  def __repr__(self):
    return "Message(%r)" % self.content

__all__ = ["Connection", "Session", "Sender", "Receiver", "Pattern", "Message",
           "ConnectionError", "ConnectError", "SessionError", "Disconnected",
           "SendError", "InsufficientCapacity", "ReceiveError", "Empty",
           "timestamp", "uuid4", "UNLIMITED", "AMQP_PORT", "AMQPS_PORT"]
