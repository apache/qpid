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

from qpid.messaging import Message
try:
  from uuid import uuid4
except ImportError:
  from qpid.datatypes import uuid4

class BrokerAgent(object):
  def __init__(self, conn):
    self.conn = conn
    self.sess = self.conn.session()
    self.reply_to = "qmf.default.topic/direct.%s;{node:{type:topic}, link:{x-declare:{auto-delete:True,exclusive:True}}}" % \
        str(uuid4())
    self.reply_rx = self.sess.receiver(self.reply_to)
    self.reply_rx.capacity = 10
    self.tx = self.sess.sender("qmf.default.direct/broker")
    self.next_correlator = 1

  def close(self):
    self.sess.close()

  def _method(self, method, arguments, addr="org.apache.qpid.broker:broker:amqp-broker", timeout=10):
    props = {'method'             : 'request',
             'qmf.opcode'         : '_method_request',
             'x-amqp-0-10.app-id' : 'qmf2'}
    correlator = str(self.next_correlator)
    self.next_correlator += 1

    content = {'_object_id'   : {'_object_name' : addr},
               '_method_name' : method,
               '_arguments'   : arguments}

    message = Message(content, reply_to=self.reply_to, correlation_id=correlator,
                      properties=props, subject="broker")
    self.tx.send(message)
    response = self.reply_rx.fetch(timeout)
    self.sess.acknowledge()
    if response.properties['qmf.opcode'] == '_exception':
      raise Exception("Exception from Agent: %r" % response.content['_values'])
    if response.properties['qmf.opcode'] != '_method_response':
      raise Exception("bad response: %r" % response.properties)
    return response.content['_arguments']

  def _sendRequest(self, opcode, content):
    props = {'method'             : 'request',
             'qmf.opcode'         : opcode,
             'x-amqp-0-10.app-id' : 'qmf2'}
    correlator = str(self.next_correlator)
    self.next_correlator += 1
    message = Message(content, reply_to=self.reply_to, correlation_id=correlator,
                      properties=props, subject="broker")
    self.tx.send(message)
    return correlator

  def _doClassQuery(self, class_name):
    query = {'_what'      : 'OBJECT',
             '_schema_id' : {'_class_name' : class_name}}
    correlator = self._sendRequest('_query_request', query)
    response = self.reply_rx.fetch(10)
    if response.properties['qmf.opcode'] != '_query_response':
      raise Exception("bad response")
    items = []
    done = False
    while not done:
      for item in response.content:
        items.append(item)
      if 'partial' in response.properties:
        response = self.reply_rx.fetch(10)
      else:
        done = True
      self.sess.acknowledge()
    return items

  def _doNameQuery(self, class_name, object_name, package_name='org.apache.qpid.broker'):
    query = {'_what'      : 'OBJECT',
             '_object_id' : {'_object_name' : "%s:%s:%s" % (package_name, class_name, object_name)}}
    correlator = self._sendRequest('_query_request', query)
    response = self.reply_rx.fetch(10)
    if response.properties['qmf.opcode'] != '_query_response':
      raise Exception("bad response")
    items = []
    done = False
    while not done:
      for item in response.content:
        items.append(item)
      if 'partial' in response.properties:
        response = self.reply_rx.fetch(10)
      else:
        done = True
      self.sess.acknowledge()
    if len(items) == 1:
      return items[0]
    return None

  def _getAllBrokerObjects(self, cls):
    items = self._doClassQuery(cls.__name__.lower())
    objs = []
    for item in items:
      objs.append(cls(self, item))
    return objs
    
  def _getBrokerObject(self, cls, name):
    obj = self._doNameQuery(cls.__name__.lower(), name)
    if obj:
      return cls(self, obj)
    return None

  def getCluster(self):
    return self._getAllBrokerObjects(Cluster)

  def getBroker(self):
    #
    # getAllBrokerObjects is used instead of getBrokerObject(Broker, 'amqp-broker') because
    # of a bug that used to be in the broker whereby by-name queries did not return the
    # object timestamps.
    #
    brokers = self._getAllBrokerObjects(Broker)
    if brokers:
      return brokers[0]
    return None

  def getMemory(self):
    return self._getAllBrokerObjects(Memory)[0]

  def getAllConnections(self):
    return self._getAllBrokerObjects(Connection)

  def getConnection(self, name):
    return self._getBrokerObject(Connection, name)

  def getAllSessions(self):
    return self._getAllBrokerObjects(Session)

  def getSession(self, name):
    return self._getBrokerObject(Session, name)

  def getAllSubscriptions(self):
    return self._getAllBrokerObjects(Subscription)

  def getSubscription(self, name):
    return self._getBrokerObject(Subscription, name)

  def getAllExchanges(self):
    return self._getAllBrokerObjects(Exchange)

  def getExchange(self, name):
    return self._getBrokerObject(Exchange, name)

  def getAllQueues(self):
    return self._getAllBrokerObjects(Queue)

  def getQueue(self, name):
    return self._getBrokerObject(Queue, name)

  def getAllBindings(self):
    return self._getAllBrokerObjects(Binding)

  def getBinding(self, exchange=None, queue=None):
    pass

  def echo(self, sequence, body):
    """Request a response to test the path to the management broker"""
    pass

  def connect(self, host, port, durable, authMechanism, username, password, transport):
    """Establish a connection to another broker"""
    pass

  def queueMoveMessages(self, srcQueue, destQueue, qty):
    """Move messages from one queue to another"""
    pass

  def setLogLevel(self, level):
    """Set the log level"""
    pass

  def getLogLevel(self):
    """Get the log level"""
    pass

  def setTimestampConfig(self, receive):
    """Set the message timestamping configuration"""
    pass

  def getTimestampConfig(self):
    """Get the message timestamping configuration"""
    pass

#  def addExchange(self, exchange_type, name, **kwargs):
#    pass

#  def delExchange(self, name):
#    pass

#  def addQueue(self, name, **kwargs):
#    pass

#  def delQueue(self, name):
#    pass

#  def bind(self, exchange, queue, key, **kwargs):
#    pass

#  def unbind(self, exchange, queue, key, **kwargs):
#    pass

  def create(self, _type, name, properties, strict):
    """Create an object of the specified type"""
    pass

  def delete(self, _type, name, options):
    """Delete an object of the specified type"""
    pass

  def query(self, _type, name):
    """Query the current state of an object"""
    return self._getBrokerObject(self, _type, name)


class BrokerObject(object):
  def __init__(self, broker, content):
    self.broker = broker
    self.content = content
    self.values = content['_values']

  def __getattr__(self, key):
    if key not in self.values:
      return None
    value = self.values[key]
    if value.__class__ == dict and '_object_name' in value:
      full_name = value['_object_name']
      colon = full_name.find(':')
      if colon > 0:
        full_name = full_name[colon+1:]
        colon = full_name.find(':')
        if colon > 0:
          return full_name[colon+1:]
    return value

  def getAttributes(self):
    return self.values

  def getCreateTime(self):
    return self.content['_create_ts']

  def getDeleteTime(self):
    return self.content['_delete_ts']

  def getUpdateTime(self):
    return self.content['_update_ts']

  def update(self):
    """
    Reload the property values from the agent.
    """
    refreshed = self.broker._getBrokerObject(self.__class__, self.name)
    if refreshed:
      self.content = refreshed.content
      self.values = self.content['_values']
    else:
      raise Exception("No longer exists on the broker")

class Broker(BrokerObject):
  def __init__(self, broker, values):
    BrokerObject.__init__(self, broker, values)

class Memory(BrokerObject):
  def __init__(self, broker, values):
    BrokerObject.__init__(self, broker, values)

class Connection(BrokerObject):
  def __init__(self, broker, values):
    BrokerObject.__init__(self, broker, values)

  def close(self):
    pass

class Session(BrokerObject):
  def __init__(self, broker, values):
    BrokerObject.__init__(self, broker, values)

class Subscription(BrokerObject):
  def __init__(self, broker, values):
    BrokerObject.__init__(self, broker, values)

  def __repr__(self):
    return "subscription name undefined"

class Exchange(BrokerObject):
  def __init__(self, broker, values):
    BrokerObject.__init__(self, broker, values)

class Binding(BrokerObject):
  def __init__(self, broker, values):
    BrokerObject.__init__(self, broker, values)

  def __repr__(self):
    return "Binding key: %s" % self.values['bindingKey']

class Queue(BrokerObject):
  def __init__(self, broker, values):
    BrokerObject.__init__(self, broker, values)

  def purge(self, request):
    """Discard all or some messages on a queue"""
    self.broker._method("purge", {'request':request}, "org.apache.qpid.broker:queue:%s" % self.name)

  def reroute(self, request, useAltExchange, exchange, filter={}):
    """Remove all or some messages on this queue and route them to an exchange"""
    self.broker._method("reroute", {'request':request,'useAltExchange':useAltExchange,'exchange':exchange,'filter':filter},
                        "org.apache.qpid.broker:queue:%s" % self.name)

