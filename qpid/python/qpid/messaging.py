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

import connection, time, socket, sys, traceback
from codec010 import StringCodec
from datatypes import timestamp, uuid4, RangedSet, Message as Message010, Serial
from exceptions import Timeout
from logging import getLogger
from ops import PRIMITIVE, delivery_mode
from session import Client, INCOMPLETE, SessionDetached
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

class ConnectionError(Exception):
  pass

class ConnectError(ConnectionError):
  pass

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
    self.id = str(uuid4())
    self.session_counter = 0
    self.sessions = {}
    self.reconnect = False
    self._connected = False
    self._lock = RLock()
    self._condition = Condition(self._lock)
    self._modcount = Serial(0)
    self.error = None
    self._driver = Driver(self)
    self._driver.start()

  def wakeup(self):
    self._modcount += 1
    self._driver.wakeup()

  def catchup(self, exc=ConnectionError):
    mc = self._modcount
    self.wait(lambda: not self._driver._modcount < mc)
    self.check_error(exc)

  def check_error(self, exc=ConnectionError):
    if self.error:
      raise exc(*self.error)

  def ewait(self, predicate, timeout=None, exc=ConnectionError):
    result = self.wait(lambda: self.error or predicate(), timeout)
    self.check_error(exc)
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
      self.wakeup()
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
    self.wakeup()
    self.ewait(lambda: self._driver._connected, exc=ConnectError)

  @synchronized
  def disconnect(self):
    """
    Disconnect from the remote endpoint.
    """
    self._connected = False
    self.wakeup()
    self.ewait(lambda: not self._driver._connected)

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

FILTER_DEFAULTS = {
  "topic": Pattern("*")
  }

def delegate(handler, session):
  class Delegate(Client):

    def message_transfer(self, cmd):
      handler._message_transfer(session, cmd)
  return Delegate

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

class Session(Lockable):

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

    self.closing = False
    self.closed = False

    self._lock = connection._lock
    self._condition = connection._condition
    self.thread = Thread(target = self.run)
    self.thread.setDaemon(True)
    self.thread.start()

  def __repr__(self):
    return "<Session %s>" % self.name

  def wakeup(self):
    self.connection.wakeup()

  def catchup(self, exc=SessionError):
    self.connection.catchup(exc)

  def check_error(self, exc=SessionError):
    self.connection.check_error(exc)

  def ewait(self, predicate, timeout=None, exc=SessionError):
    return self.connection.ewait(predicate, timeout, exc)

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
    self.wakeup()
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
    self.wakeup()
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
        msg._receiver.returned += 1
        self.unacked.append(msg)
        log.debug("RETR [%s] %s", self, msg)
        return msg
    return None

  @synchronized
  def acknowledge(self, message=None):
    """
    Acknowledge the given L{Message}. If message is None, then all
    unacknowledged messages on the session are acknowledged.

    @type message: Message
    @param message: the message to acknowledge or None
    """
    if message is None:
      messages = self.unacked[:]
    else:
      messages = [message]

    for m in messages:
      self.unacked.remove(m)
      self.acked.append(m)

    self.wakeup()
    self.wait(lambda: self.connection.error or not [m for m in messages if m in self.acked])
    self.check_error()

  @synchronized
  def commit(self):
    """
    Commit outstanding transactional work. This consists of all
    message sends and receives since the prior commit or rollback.
    """
    if not self.transactional:
      raise NontransactionalSession()
    self.committing = True
    self.wakeup()
    self.ewait(lambda: not self.committing)
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
    self.wakeup()
    self.ewait(lambda: not self.aborting)
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
    self.wakeup()
    self.catchup()
    self.wait(lambda: self.closed)
    while self.thread.isAlive():
      self.thread.join(3)
    self.thread = None
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

class SendError(SessionError):
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
    self._lock = self.session._lock
    self._condition = self.session._condition

  def wakeup(self):
    self.session.wakeup()

  def catchup(self, exc=SendError):
    self.session.catchup(exc)

  def check_error(self, exc=SendError):
    self.session.check_error(exc)

  def ewait(self, predicate, timeout=None, exc=SendError):
    return self.session.ewait(predicate, timeout, exc)

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

    if not self.session.connection._connected or self.session.closing:
      raise Disconnected()

    if isinstance(object, Message):
      message = object
    else:
      message = Message(object)

    # XXX: what if we send the same message to multiple senders?
    message._sender = self
    self.session.outgoing.append(message)

    self.wakeup()
    self.ewait(lambda: message not in self.session.outgoing)

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
    self.granted = Serial(0)
    self.draining = False
    self.impending = Serial(0)
    self.received = Serial(0)
    self.returned = Serial(0)

    self.closing = False
    self.closed = False
    self.listener = None
    self._lock = self.session._lock
    self._condition = self.session._condition

  def wakeup(self):
    self.session.wakeup()

  def catchup(self, exc=ReceiveError):
    self.session.catchup()

  def check_error(self, exc=ReceiveError):
    self.session.check_error(exc)

  def ewait(self, predicate, timeout=None, exc=ReceiveError):
    return self.session.ewait(predicate, timeout, exc)

  @synchronized
  def pending(self):
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
      self.wakeup()
      self.ewait(lambda: self.impending == self.granted)
    msg = self.session._get(self._pred, timeout=timeout)
    if msg is None:
      self.draining = True
      self.wakeup()
      self.ewait(lambda: not self.draining)
      assert self.granted == self.received
      if self.capacity is not UNLIMITED:
        self.granted += self._capacity()
        self.wakeup()
      msg = self.session._get(self._pred, timeout=0)
      if msg is None:
        raise Empty()
    if self._capacity() not in (0, UNLIMITED.value):
      self.granted += 1
      self.wakeup()
    return msg

  @synchronized
  def start(self):
    """
    Start incoming message delivery for this receiver.
    """
    self.started = True
    if self.capacity is UNLIMITED:
      self.granted = UNLIMITED
    else:
      self.granted = self.received + self._capacity()
    self.wakeup()

  @synchronized
  def stop(self):
    """
    Stop incoming message delivery for this receiver.
    """
    self.granted = self.received
    self.started = False
    self.wakeup()
    self.ewait(lambda: self.impending == self.received)

  @synchronized
  def close(self):
    """
    Close the receiver.
    """
    self.closing = True
    self.wakeup()
    try:
      self.ewait(lambda: self.closed)
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

class Attachment:

  def __init__(self, target):
    self.target = target

DURABLE_DEFAULT=True

class Driver(Lockable):

  def __init__(self, connection):
    self.connection = connection
    self._lock = self.connection._lock
    self._condition = self.connection._condition
    self._wakeup_cond = Condition()
    self._socket = None
    self._conn = None
    self._connected = False
    self._attachments = {}
    self._modcount = self.connection._modcount
    self.thread = Thread(target=self.run)
    self.thread.setDaemon(True)
    # XXX: need to figure out how to join on this thread

  def start(self):
    self.thread.start()

  def wakeup(self):
    self._wakeup_cond.acquire()
    try:
      self._wakeup_cond.notifyAll()
    finally:
      self._wakeup_cond.release()

  def start(self):
    self.thread.start()

  def run(self):
    while True:
      self._wakeup_cond.acquire()
      try:
        if self.connection._modcount <= self._modcount:
          self._wakeup_cond.wait(10)
      finally:
        self._wakeup_cond.release()
      self.dispatch(self.connection._modcount)

  @synchronized
  def dispatch(self, modcount):
    try:
      if self._conn is None and self.connection._connected:
        self.connect()
      elif self._conn is not None and not self.connection._connected:
        self.disconnect()

      if self._conn is not None:
        for ssn in self.connection.sessions.values():
          self.attach(ssn)
          self.process(ssn)
      exi = None
    except:
      exi = sys.exc_info()

    if exi:
      msg = traceback.format_exc()
      recoverable = ["aborted", "Connection refused", "SessionDetached", "Connection reset by peer",
                     "Bad file descriptor", "start timed out", "Broken pipe"]
      for r in recoverable:
        if self.connection.reconnect and r in msg:
          print "waiting to retry"
          self.reset()
          time.sleep(3)
          print "retrying..."
          return
      else:
        self.connection.error = (msg,)

    self._modcount = modcount
    self.notifyAll()

  def connect(self):
    if self._conn is not None:
      return
    try:
      self._socket = connect(self.connection.host, self.connection.port)
    except socket.error, e:
      raise ConnectError(e)
    self._conn = connection.Connection(self._socket)
    try:
      self._conn.start(timeout=10)
      self._connected = True
    except connection.VersionError, e:
      raise ConnectError(e)
    except Timeout:
      print "start timed out"
      raise ConnectError("start timed out")

  def disconnect(self):
    self._conn.close()
    self.reset()

  def reset(self):
    self._conn = None
    self._connected = False
    self._attachments.clear()
    for ssn in self.connection.sessions.values():
      for m in ssn.acked + ssn.unacked + ssn.incoming:
        m._transfer_id = None
      for rcv in ssn.receivers:
        rcv.impending = rcv.received

  def connected(self):
    return self._conn is not None

  def attach(self, ssn):
    _ssn = self._attachments.get(ssn)
    if _ssn is None:
      _ssn = self._conn.session(ssn.name, delegate=delegate(self, ssn))
      _ssn.auto_sync = False
      _ssn.invoke_lock = self._lock
      _ssn.lock = self._lock
      _ssn.condition = self._condition
      if ssn.transactional:
        # XXX: adding an attribute to qpid.session.Session
        _ssn.acked = []
        _ssn.tx_select()
      self._attachments[ssn] = _ssn

    for snd in ssn.senders:
      self.link_out(snd)
    for rcv in ssn.receivers:
      self.link_in(rcv)

    if ssn.closing:
      _ssn.close()
      del self._attachments[ssn]

  def _exchange_query(self, ssn, address):
    # XXX: auto sync hack is to avoid deadlock on future
    result = ssn.exchange_query(name=address, sync=True)
    ssn.sync()
    return result.get()

  def link_out(self, snd):
    _ssn = self._attachments[snd.session]
    _snd = self._attachments.get(snd)
    if _snd is None:
      _snd = Attachment(snd)
      node, _snd._subject = parse_addr(snd.target)
      result = self._exchange_query(_ssn, node)
      if result.not_found:
        # XXX: should check 'create' option
        _ssn.queue_declare(queue=node, durable=DURABLE_DEFAULT, sync=True)
        _ssn.sync()
        _snd._exchange = ""
        _snd._routing_key = node
      else:
        _snd._exchange = node
        _snd._routing_key = _snd._subject
      self._attachments[snd] = _snd

    if snd.closed:
      del self._attachments[snd]

  def link_in(self, rcv):
    _ssn = self._attachments[rcv.session]
    _rcv = self._attachments.get(rcv)
    if _rcv is None:
      _rcv = Attachment(rcv)
      result = self._exchange_query(_ssn, rcv.source)
      if result.not_found:
        _rcv._queue = rcv.source
        # XXX: should check 'create' option
        _ssn.queue_declare(queue=_rcv._queue, durable=DURABLE_DEFAULT)
      else:
        _rcv._queue = "%s.%s" % (rcv.session.name, rcv.destination)
        _ssn.queue_declare(queue=_rcv._queue, durable=DURABLE_DEFAULT, exclusive=True, auto_delete=True)
        if rcv.filter is None:
          f = FILTER_DEFAULTS[result.type]
        else:
          f = rcv.filter
        f._bind(_ssn, rcv.source, _rcv._queue)
      _ssn.message_subscribe(queue=_rcv._queue, destination=rcv.destination)
      _ssn.message_set_flow_mode(rcv.destination, _ssn.flow_mode.credit, sync=True)
      self._attachments[rcv] = _rcv
      # XXX: need to kill syncs
      _ssn.sync()

    if rcv.closing:
      _ssn.message_cancel(rcv.destination, sync=True)
      # XXX: need to kill syncs
      _ssn.sync()
      del self._attachments[rcv]
      rcv.closed = True

  def process(self, ssn):
    if ssn.closing: return

    _ssn = self._attachments[ssn]

    while ssn.outgoing:
      msg = ssn.outgoing[0]
      snd = msg._sender
      self.send(snd, msg)
      ssn.outgoing.pop(0)

    for rcv in ssn.receivers:
      self.process_receiver(rcv)

    if ssn.acked:
      messages = ssn.acked[:]
      ids = RangedSet(*[m._transfer_id for m in messages if m._transfer_id is not None])
      for range in ids:
        _ssn.receiver._completed.add_range(range)
      ch = _ssn.channel
      if ch is None:
        raise SessionDetached()
      ch.session_completed(_ssn.receiver._completed)
      _ssn.message_accept(ids, sync=True)
      # XXX: really need to make this async so that we don't give up the lock
      _ssn.sync()

      for m in messages:
        ssn.acked.remove(m)
        if ssn.transactional:
          _ssn.acked.append(m)

    if ssn.committing:
      _ssn.tx_commit(sync=True)
      # XXX: need to kill syncs
      _ssn.sync()
      del _ssn.acked[:]
      ssn.committing = False
      ssn.committed = True
      ssn.aborting = False
      ssn.aborted = False

    if ssn.aborting:
      for rcv in ssn.receivers:
        _ssn.message_stop(rcv.destination)
      _ssn.sync()

      messages = _ssn.acked + ssn.unacked + ssn.incoming
      ids = RangedSet(*[m._transfer_id for m in messages])
      for range in ids:
        _ssn.receiver._completed.add_range(range)
      _ssn.channel.session_completed(_ssn.receiver._completed)
      _ssn.message_release(ids)
      _ssn.tx_rollback(sync=True)
      _ssn.sync()

      del ssn.incoming[:]
      del ssn.unacked[:]
      del _ssn.acked[:]

      for rcv in ssn.receivers:
        rcv.impending = rcv.received
        rcv.returned = rcv.received
        # XXX: do we need to update granted here as well?

      for rcv in ssn.receivers:
        self.process_receiver(rcv)

      ssn.aborting = False
      ssn.aborted = True
      ssn.committing = False
      ssn.committed = False

  def grant(self, rcv):
    _ssn = self._attachments[rcv.session]
    _rcv = self._attachments[rcv]

    if rcv.granted is UNLIMITED:
      if rcv.impending is UNLIMITED:
        delta = 0
      else:
        delta = UNLIMITED.value
    else:
      delta = rcv.granted - rcv.impending

    if delta > 0:
      _ssn.message_flow(rcv.destination, _ssn.credit_unit.byte, UNLIMITED.value)
      _ssn.message_flow(rcv.destination, _ssn.credit_unit.message, delta)
      rcv.impending += delta
    elif delta < 0:
      _ssn.message_stop(rcv.destination, sync=True)
      # XXX: need to kill syncs
      _ssn.sync()
      rcv.impending = rcv.received
      # XXX: this can recurse infinitely if granted drops below received
      self.grant(rcv)

  def process_receiver(self, rcv):
    if rcv.closed: return
    _ssn = self._attachments[rcv.session]
    _rcv = self._attachments[rcv]

    self.grant(rcv)

    if rcv.draining:
      _ssn.message_flush(rcv.destination, sync=True)
      # XXX: really need to make this async so that we don't give up the lock
      _ssn.sync()
      rcv.granted = rcv.received
      rcv.impending = rcv.received
      rcv.draining = False

  def send(self, snd, msg):
    _ssn = self._attachments[snd.session]
    _snd = self._attachments[snd]

    # XXX: what if subject is specified for a normal queue?
    if _snd._routing_key is None:
      rk = msg.subject
    else:
      rk = _snd._routing_key
    # XXX: do we need to query to figure out how to create the reply-to interoperably?
    if msg.reply_to:
      rt = _ssn.reply_to(*parse_addr(msg.reply_to))
    else:
      rt = None
    dp = _ssn.delivery_properties(routing_key=rk)
    mp = _ssn.message_properties(message_id=msg.id,
                                 user_id=msg.user_id,
                                 reply_to=rt,
                                 correlation_id=msg.correlation_id,
                                 content_type=msg.content_type,
                                 application_headers=msg.properties)
    if msg.subject is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers["subject"] = msg.subject
    if msg.to is not None:
      if mp.application_headers is None:
        mp.application_headers = {}
      mp.application_headers["to"] = msg.to
    if msg.durable:
      dp.delivery_mode = delivery_mode.persistent
    enc, dec = get_codec(msg.content_type)
    body = enc(msg.content)
    _ssn.message_transfer(destination=_snd._exchange,
                          message=Message010(dp, mp, body),
                          sync=True)
    log.debug("SENT [%s] %s", snd.session, msg)
    # XXX: really need to make this async so that we don't give up the lock
    _ssn.sync()
    # XXX: should we log the ack somehow too?

  @synchronized
  def _message_transfer(self, ssn, cmd):
    m = Message010(cmd.payload)
    m.headers = cmd.headers
    m.id = cmd.id
    msg = self._decode(m)
    rcv = ssn.receivers[int(cmd.destination)]
    msg._receiver = rcv
    rcv.received += 1
    log.debug("RECV [%s] %s", ssn, msg)
    ssn.incoming.append(msg)
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
    msg.durable = dp.delivery_mode == delivery_mode.persistent
    msg.properties = mp.application_headers
    msg.content_type = mp.content_type
    msg._transfer_id = message.id
    return msg

__all__ = ["Connection", "Pattern", "Session", "Sender", "Receiver", "Message",
           "Empty", "timestamp", "uuid4"]
