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

  - definition of the arguments for L{Session.sender} and L{Session.receiver}
  - standard L{Message} properties
  - L{Message} content encoding
  - protocol negotiation/multiprotocol impl
"""

from logging import getLogger
from qpid.codec010 import StringCodec
from qpid.concurrency import synchronized, Waiter, Condition
from qpid.datatypes import Serial, uuid4
from qpid.messaging.constants import *
from qpid.messaging.exceptions import *
from qpid.messaging.message import *
from qpid.ops import PRIMITIVE
from qpid.util import default
from threading import Thread, RLock

log = getLogger("qpid.messaging")

static = staticmethod

class Connection:

  """
  A Connection manages a group of L{Sessions<Session>} and connects
  them with a remote endpoint.
  """

  @static
  def open(host, port=None, username="guest", password="guest", **options):
    """
    Creates an AMQP connection and connects it to the given host and port.

    @type host: str
    @param host: the name or ip address of the remote host
    @type port: int
    @param port: the port number of the remote host
    @rtype: Connection
    @return: a connected Connection
    """
    conn = Connection(host, port, username, password, **options)
    conn.connect()
    return conn

  def __init__(self, host, port=None, username="guest", password="guest", **options):
    """
    Creates a connection. A newly created connection must be connected
    with the Connection.connect() method before it can be used.

    @type host: str
    @param host: the name or ip address of the remote host
    @type port: int
    @param port: the port number of the remote host
    @rtype: Connection
    @return: a disconnected Connection
    """
    self.host = host
    self.port = default(port, AMQP_PORT)
    self.username = username
    self.password = password
    self.mechanisms = options.get("mechanisms")
    self.heartbeat = options.get("heartbeat")
    self.reconnect = options.get("reconnect", False)
    self.reconnect_delay = options.get("reconnect_delay", 3)
    self.reconnect_limit = options.get("reconnect_limit")
    self.backups = options.get("backups", [])
    self.options = options

    self.id = str(uuid4())
    self.session_counter = 0
    self.sessions = {}
    self._connected = False
    self._transport_connected = False
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
      ssn = Session(self, name, transactional)
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
    self._ewait(lambda: self._transport_connected and not self._unlinked(),
                exc=ConnectError)

  def _unlinked(self):
    return [l
            for ssn in self.sessions.values()
            for l in ssn.senders + ssn.receivers
            if not (l.linked or l.error or l.closed)]

  @synchronized
  def disconnect(self):
    """
    Disconnect from the remote endpoint.
    """
    self._connected = False
    self._wakeup()
    self._ewait(lambda: not self._transport_connected)

  @synchronized
  def connected(self):
    """
    Return true if the connection is connected, false otherwise.
    """
    return self._connected

  @synchronized
  def close(self):
    """
    Close the connection and all sessions.
    """
    for ssn in self.sessions.values():
      ssn.close()
    self.disconnect()

class Session:

  """
  Sessions provide a linear context for sending and receiving
  L{Messages<Message>}. L{Messages<Message>} are sent and received
  using the L{Sender.send} and L{Receiver.fetch} methods of the
  L{Sender} and L{Receiver} objects associated with a Session.

  Each L{Sender} and L{Receiver} is created by supplying either a
  target or source address to the L{sender} and L{receiver} methods of
  the Session. The address is supplied via a string syntax documented
  below.

  Addresses
  =========

  An address identifies a source or target for messages. In its
  simplest form this is just a name. In general a target address may
  also be used as a source address, however not all source addresses
  may be used as a target, e.g. a source might additionally have some
  filtering criteria that would not be present in a target.

  A subject may optionally be specified along with the name. When an
  address is used as a target, any subject specified in the address is
  used as the default subject of outgoing messages for that target.
  When an address is used as a source, any subject specified in the
  address is pattern matched against the subject of available messages
  as a filter for incoming messages from that source.

  The options map contains additional information about the address
  including:

    - policies for automatically creating, and deleting the node to
      which an address refers

    - policies for asserting facts about the node to which an address
      refers

    - extension points that can be used for sender/receiver
      configuration

  Mapping to AMQP 0-10
  --------------------
  The name is resolved to either an exchange or a queue by querying
  the broker.

  The subject is set as a property on the message. Additionally, if
  the name refers to an exchange, the routing key is set to the
  subject.

  Syntax
  ------
  The following regular expressions define the tokens used to parse
  addresses::
    LBRACE: \\{
    RBRACE: \\}
    LBRACK: \\[
    RBRACK: \\]
    COLON:  :
    SEMI:   ;
    SLASH:  /
    COMMA:  ,
    NUMBER: [+-]?[0-9]*\\.?[0-9]+
    ID:     [a-zA-Z_](?:[a-zA-Z0-9_-]*[a-zA-Z0-9_])?
    STRING: "(?:[^\\\\"]|\\\\.)*"|\'(?:[^\\\\\']|\\\\.)*\'
    ESC:    \\\\[^ux]|\\\\x[0-9a-fA-F][0-9a-fA-F]|\\\\u[0-9a-fA-F][0-9a-fA-F][0-9a-fA-F][0-9a-fA-F]
    SYM:    [.#*%@$^!+-]
    WSPACE: [ \\n\\r\\t]+

  The formal grammar for addresses is given below::
    address = name [ "/" subject ] [ ";" options ]
       name = ( part | quoted )+
    subject = ( part | quoted | "/" )*
     quoted = STRING / ESC
       part = LBRACE / RBRACE / COLON / COMMA / NUMBER / ID / SYM
    options = map
        map = "{" ( keyval ( "," keyval )* )? "}"
     keyval = ID ":" value
      value = NUMBER / STRING / ID / map / list
       list = "[" ( value ( "," value )* )? "]"

  This grammar resuls in the following informal syntax::

    <name> [ / <subject> ] [ ; <options> ]

  Where options is::

    { <key> : <value>, ... }

  And values may be:
    - numbers
    - single, double, or non quoted strings
    - maps (dictionaries)
    - lists

  Options
  -------
  The options map permits the following parameters::

    <name> [ / <subject> ] ; {
      create: <create-policy>,
      delete: <delete-policy>,
      assert: <assert-policy>,
      node-properties: {
        type: <node-type>,
        durable: <node-durability>,
        x-properties: {
          bindings: ["<exchange>/<key>", ...],
          <passthrough-key>: <passthrough-value>
        }
      }
    }

  The create, delete, and assert policies specify who should perfom
  the associated action:

   - I{always}: the action will always be performed
   - I{sender}: the action will only be performed by the sender
   - I{receiver}: the action will only be performed by the receiver
   - I{never}: the action will never be performed (this is the default)

  The node-type is one of:

    - I{topic}: a topic node will default to the topic exchange,
      x-properties may be used to specify other exchange types
    - I{queue}: this is the default node-type

  The x-properties map permits arbitrary additional keys and values to
  be specified. These keys and values are passed through when creating
  a node or asserting facts about an existing node. Any passthrough
  keys and values that do not match a standard field of the underlying
  exchange or queue declare command will be sent in the arguments map.

  Examples
  --------
  A simple name resolves to any named node, usually a queue or a
  topic::

    my-queue-or-topic

  A simple name with a subject will also resolve to a node, but the
  presence of the subject will cause a sender using this address to
  set the subject on outgoing messages, and receivers to filter based
  on the subject::

    my-queue-or-topic/my-subject

  A subject pattern can be used and will cause filtering if used by
  the receiver. If used for a sender, the literal value gets set as
  the subject::

    my-queue-or-topic/my-*

  In all the above cases, the address is resolved to an existing node.
  If you want the node to be auto-created, then you can do the
  following. By default nonexistent nodes are assumed to be queues::

    my-queue; {create: always}

  You can customize the properties of the queue::

    my-queue; {create: always, node-properties: {durable: True}}

  You can create a topic instead if you want::

    my-queue; {create: always, node-properties: {type: topic}}

  You can assert that the address resolves to a node with particular
  properties::

    my-transient-topic; {
      assert: always,
      node-properties: {
        type: topic,
        durable: False
      }
    }
 """

  def __init__(self, connection, name, transactional):
    self.connection = connection
    self.name = name
    self.log_id = "%x" % id(self)

    self.transactional = transactional

    self.committing = False
    self.committed = True
    self.aborting = False
    self.aborted = False

    self.next_sender_id = 0
    self.senders = []
    self.next_receiver_id = 0
    self.receivers = []
    self.outgoing = []
    self.incoming = []
    self.unacked = []
    self.acked = []
    # XXX: I hate this name.
    self.ack_capacity = UNLIMITED

    self.error = None
    self.closing = False
    self.closed = False

    self._lock = connection._lock

  def __repr__(self):
    return "<Session %s>" % self.name

  def _wait(self, predicate, timeout=None):
    return self.connection._wait(predicate, timeout=timeout)

  def _wakeup(self):
    self.connection._wakeup()

  def _check_error(self, exc=SessionError):
    self.connection._check_error(exc)
    if self.error:
      raise exc(*self.error)

  def _ewait(self, predicate, timeout=None, exc=SessionError):
    result = self.connection._ewait(lambda: self.error or predicate(), timeout, exc)
    self._check_error(exc)
    return result

  @synchronized
  def sender(self, target, **options):
    """
    Creates a L{Sender} that may be used to send L{Messages<Message>}
    to the specified target.

    @type target: str
    @param target: the target to which messages will be sent
    @rtype: Sender
    @return: a new Sender for the specified target
    """
    sender = Sender(self, self.next_sender_id, target, options)
    self.next_sender_id += 1
    self.senders.append(sender)
    if not self.closed and self.connection._connected:
      self._wakeup()
      try:
        sender._ewait(lambda: sender.linked)
      except SendError, e:
        sender.close()
        raise e
    return sender

  @synchronized
  def receiver(self, source, **options):
    """
    Creates a receiver that may be used to fetch L{Messages<Message>}
    from the specified source.

    @type source: str
    @param source: the source of L{Messages<Message>}
    @rtype: Receiver
    @return: a new Receiver for the specified source
    """
    receiver = Receiver(self, self.next_receiver_id, source, options)
    self.next_receiver_id += 1
    self.receivers.append(receiver)
    if not self.closed and self.connection._connected:
      self._wakeup()
      try:
        receiver._ewait(lambda: receiver.linked)
      except ReceiveError, e:
        receiver.close()
        raise e
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
    if self._ewait(lambda: ((self._peek(predicate) is not None) or self.closing),
                   timeout):
      msg = self._pop(predicate)
      if msg is not None:
        msg._receiver.returned += 1
        self.unacked.append(msg)
        log.debug("RETR[%s]: %s", self.log_id, msg)
        return msg
    return None

  @synchronized
  def next_receiver(self, timeout=None):
    if self._ewait(lambda: self.incoming, timeout):
      return self.incoming[0]._receiver
    else:
      raise Empty

  @synchronized
  def acknowledge(self, message=None, disposition=None, sync=True):
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
      m._disposition = disposition
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
  def close(self):
    """
    Close the session.
    """
    # XXX: should be able to express this condition through API calls
    self._ewait(lambda: not self.outgoing and not self.acked)

    for link in self.receivers + self.senders:
      link.close()

    self.closing = True
    self._wakeup()
    self._ewait(lambda: self.closed)
    self.connection._remove_session(self)

class Sender:

  """
  Sends outgoing messages.
  """

  def __init__(self, session, id, target, options):
    self.session = session
    self.id = id
    self.target = target
    self.options = options
    self.capacity = options.get("capacity", UNLIMITED)
    self.durable = options.get("durable")
    self.queued = Serial(0)
    self.acked = Serial(0)
    self.error = None
    self.linked = False
    self.closing = False
    self.closed = False
    self._lock = self.session._lock

  def _wakeup(self):
    self.session._wakeup()

  def _check_error(self, exc=SendError):
    self.session._check_error(exc)
    if self.error:
      raise exc(*self.error)

  def _ewait(self, predicate, timeout=None, exc=SendError):
    result = self.session._ewait(lambda: self.error or predicate(), timeout, exc)
    self._check_error(exc)
    return result

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

    self._ewait(lambda: self.linked)

    if isinstance(object, Message):
      message = object
    else:
      message = Message(object)

    if message.durable is None:
      message.durable = self.durable

    if self.capacity is not UNLIMITED:
      if self.capacity <= 0:
        raise InsufficientCapacity("capacity = %s" % self.capacity)
      if not self._ewait(lambda: self.pending() < self.capacity, timeout=timeout):
        raise InsufficientCapacity("capacity = %s" % self.capacity)

    # XXX: what if we send the same message to multiple senders?
    message._sender = self
    self.session.outgoing.append(message)
    self.queued += 1

    self._wakeup()

    if sync:
      self.sync()
      assert message not in self.session.outgoing

  @synchronized
  def sync(self):
    mno = self.queued
    self._ewait(lambda: self.acked >= mno)

  @synchronized
  def close(self):
    """
    Close the Sender.
    """
    self.closing = True
    self._wakeup()
    try:
      self.session._ewait(lambda: self.closed)
    finally:
      self.session.senders.remove(self)

class Receiver(object):

  """
  Receives incoming messages from a remote source. Messages may be
  fetched with L{fetch}.
  """

  def __init__(self, session, id, source, options):
    self.session = session
    self.id = id
    self.source = source
    self.options = options

    self.granted = Serial(0)
    self.draining = False
    self.impending = Serial(0)
    self.received = Serial(0)
    self.returned = Serial(0)

    self.error = None
    self.linked = False
    self.closing = False
    self.closed = False
    self._lock = self.session._lock
    self._capacity = 0
    self._set_capacity(options.get("capacity", 0), False)

  @synchronized
  def _set_capacity(self, c, wakeup=True):
    if c is UNLIMITED:
      self._capacity = c.value
    else:
      self._capacity = c
    self._grant()
    if wakeup:
      self._wakeup()

  def _get_capacity(self):
    if self._capacity == UNLIMITED.value:
      return UNLIMITED
    else:
      return self._capacity

  capacity = property(_get_capacity, _set_capacity)

  def _wakeup(self):
    self.session._wakeup()

  def _check_error(self, exc=ReceiveError):
    self.session._check_error(exc)
    if self.error:
      raise exc(*self.error)

  def _ewait(self, predicate, timeout=None, exc=ReceiveError):
    result = self.session._ewait(lambda: self.error or predicate(), timeout, exc)
    self._check_error(exc)
    return result

  @synchronized
  def pending(self):
    """
    Returns the number of messages available to be fetched by the
    application.

    @rtype: int
    @return: the number of available messages
    """
    return self.received - self.returned

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

    self._ewait(lambda: self.linked)

    if self._capacity == 0:
      self.granted = self.returned + 1
      self._wakeup()
    self._ewait(lambda: self.impending >= self.granted)
    msg = self.session._get(self._pred, timeout=timeout)
    if msg is None:
      self.draining = True
      self._wakeup()
      self._ewait(lambda: not self.draining)
      self._grant()
      self._wakeup()
      msg = self.session._get(self._pred, timeout=0)
      if msg is None:
        raise Empty()
    elif self._capacity not in (0, UNLIMITED.value):
      self.granted += 1
      self._wakeup()
    return msg

  def _grant(self):
    if self._capacity == UNLIMITED.value:
      self.granted = UNLIMITED
    else:
      self.granted = self.received + self._capacity

  @synchronized
  def close(self):
    """
    Close the receiver.
    """
    self.closing = True
    self._wakeup()
    try:
      self.session._ewait(lambda: self.closed)
    finally:
      self.session.receivers.remove(self)

__all__ = ["Connection", "Session", "Sender", "Receiver"]
