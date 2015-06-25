#!/usr/bin/env python
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

from inspect import stack
from qpid import messaging
from qpid.messaging import Message
from qpid.messaging.exceptions import Empty
from qpid.testlib import TestBase010
from random import randint
from sys import stdout
from time import sleep


class Enum(object):
    def __init__(self, **entries):
        self.__dict__.update(entries)
    def __repr__(self):
        args = ['%s=%s' % (k, repr(v)) for (k,v) in vars(self).items()]
        return 'Enum(%s)' % ', '.join(args)


class QmfTestBase010(TestBase010):

    _brokers = []
    _links = []
    _bridges = []
    _alt_exch_ops = Enum(none=0, create=1, delete=2)

    class _Broker(object):
        """
        This broker proxy object holds the Qmf proxy to a broker of known address as well as the QMF broker
        object, connection and sessions to the broker.
        """
        def __init__(self, url):
            self.url = url # format: "host:port"
            url_parts =  url.split(':')
            self.host = url_parts[0]
            self.port = int(url_parts[1])
            self.qmf_broker = None
            self.connection = messaging.Connection.establish(self.url)
            self.sessions = []
        def __str__(self):
            return "_Broker %s:%s (%d open sessions)" % (self.host, self.port, len(self.sessions))
        def destroy(self, qmf = None):
            if qmf is not None:
                qmf.delBroker(self.qmf_broker.getBroker())
            for session in self.sessions:
                try: # Session may have been closed by broker error
                    session.close()
                except Exception, e: print "WARNING: %s: Unable to close session %s (%s): %s %s" % (self, session, hex(id(session)), type(e), e)
            try: # Connection may have been closed by broker error
                self.connection.close()
            except Exception, e: print "WARNING: %s: Unable to close connection %s (%s): %s %s" % (self, self.connection, hex(id(self.connection)), type(e), e)
        def session(self, name, transactional_flag = False):
            session = self.connection.session(name, transactional_flag)
            self.sessions.append(session)
            return session

    def setUp(self):
        """
        Called one before each test starts
        """
        TestBase010.setUp(self)
        self.startQmf();

    def tearDown(self):
        """
        Called once after each test competes. Close all Qmf objects (bridges, links and brokers)
        """
        while len(self._bridges):
            self._bridges.pop().close()
        while len(self._links):
            self._links.pop().close()
        while len(self._brokers):
            b = self._brokers.pop()
            if len(self._brokers) <= 1:
                b.destroy(None)
            else:
                b.destroy(self.qmf)
        TestBase010.tearDown(self)
        self.qmf.close()

    #--- General test utility functions

    def _get_name(self):
        """
        Return the name of method which called this method stripped of "test_" prefix. Used for naming
        queues and exchanges on a per-test basis.
        """
        return stack()[1][3][5:]

    def _get_broker_port(self, key):
        """
        Get the port of a broker defined in the environment using -D<key>=portno
        """
        return int(self.defines[key])

    def _get_send_address(self, exch_name, queue_name):
        """
        Get an address to which to send messages based on the exchange name and queue name, but taking into account
        that the exchange name may be "" (the default exchange), in whcih case the format changes slightly.
        """
        if len(exch_name) == 0: # Default exchange
            return queue_name
        return "%s/%s" % (exch_name, queue_name)

    def _get_broker(self, broker_port_key):
        """
        Read the port numbers for pre-started brokers from the environment using keys, then find or create and return
        the Qmf broker proxy for the appropriate broker
        """
        port = self._get_broker_port(broker_port_key)
        return self._find_create_broker("localhost:%s" % port)
       ################
    def _get_msg_subject(self, topic_key):
        """
        Return an appropriate subject for sending a message to a known topic. Return None if there is no topic.
        """
        if len(topic_key) == 0: return None
        if "*" in topic_key: return topic_key.replace("*", "test")
        if "#" in topic_key: return topic_key.replace("#", "multipart.test")
        return topic_key

    def _send_msgs(self, session_name, broker, addr, msg_count, msg_content = "Message_%03d", topic_key = "",
                   msg_durable_flag = False, enq_txn_size = 0):
        """
        Send messages to a broker using address addr
        """
        send_session = broker.session(session_name, transactional_flag = enq_txn_size > 0)
        sender = send_session.sender(addr)
        txn_cnt = 0
        for i in range(0, msg_count):
            sender.send(Message(msg_content % (i + 1), subject = self._get_msg_subject(topic_key), durable = msg_durable_flag))
            if enq_txn_size > 0:
                txn_cnt += 1
                if txn_cnt >= enq_txn_size:
                    send_session.commit()
                    txn_cnt = 0
        if enq_txn_size > 0 and txn_cnt > 0:
            send_session.commit()
        sender.close()
        send_session.close()

    def _receive_msgs(self, session_name, broker, addr, msg_count, msg_content = "Message_%03d", deq_txn_size = 0,
                      timeout = 0):
        """
        Receive messages from a broker
        """
        receive_session = broker.session(session_name, transactional_flag = deq_txn_size > 0)
        receiver = receive_session.receiver(addr)
        txn_cnt = 0
        for i in range(0, msg_count):
            try:
                msg = receiver.fetch(timeout = timeout)
                if deq_txn_size > 0:
                    txn_cnt += 1
                    if txn_cnt >= deq_txn_size:
                        receive_session.commit()
                        txn_cnt = 0
                receive_session.acknowledge()
            except Empty:
                if deq_txn_size > 0: receive_session.rollback()
                receiver.close()
                receive_session.close()
                if i == 0:
                    self.fail("Broker %s queue \"%s\" is empty" % (broker.qmf_broker.getBroker().getUrl(), addr))
                else:
                    self.fail("Unable to receive message %d from broker %s queue \"%s\"" % (i, broker.qmf_broker.getBroker().getUrl(), addr))
            if msg.content != msg_content % (i + 1):
                receiver.close()
                receive_session.close()
                self.fail("Unexpected message \"%s\", was expecting \"%s\"." % (msg.content, msg_content % (i + 1)))
        try:
            msg = receiver.fetch(timeout = 0)
            if deq_txn_size > 0: receive_session.rollback()
            receiver.close()
            receive_session.close()
            self.fail("Extra message \"%s\" found on broker %s address \"%s\"" % (msg.content, broker.qmf_broker.getBroker().getUrl(), addr))
        except Empty:
            pass
        if deq_txn_size > 0 and txn_cnt > 0:
            receive_session.commit()
        receiver.close()
        receive_session.close()

    #--- QMF-specific utility functions

    def _get_qmf_property(self, props, key):
        """
        Get the value of a named property key kj from a property list [(k0, v0), (k1, v1), ... (kn, vn)].
        """
        for k,v in props:
            if k.name == key:
                return v
        return None

    def _check_qmf_return(self, method_result):
        """
        Check the result of a Qmf-defined method call
        """
        self.assertTrue(method_result.status == 0, method_result.text)

    def _check_optional_qmf_property(self, qmf_broker, type, qmf_object, key, expected_val, obj_ref_flag):
        """
        Optional Qmf properties don't show up in the properties list when they are not specified. Checks for
        these property types involve searching the properties list and making sure it is present or not as
        expected.
        """
        val = self._get_qmf_property(qmf_object.getProperties(), key)
        if val is None:
            if len(expected_val) > 0:
                self.fail("%s %s exists, but has does not have %s property. Expected value: \"%s\"" %
                          (type, qmf_object.name, key, expected_val))
        else:
            if len(expected_val) > 0:
                if obj_ref_flag:
                    obj = self.qmf.getObjects(_objectId = val, _broker = qmf_broker.getBroker())
                    self.assertEqual(len(obj), 1, "More than one object with the same objectId: %s" % obj)
                    val = obj[0].name
                self.assertEqual(val, expected_val, "%s %s exists, but has incorrect %s property. Found \"%s\", expected \"%s\"" %
                                 (type, qmf_object.name, key, val, expected_val))
            else:
                self.fail("%s %s exists, but has an unexpected %s property \"%s\" set." % (type, qmf_object.name, key, val))

    #--- Find/create Qmf broker objects

    def _find_qmf_broker(self, url):
        """
        Find the Qmf broker object for the given broker URL. The broker must have been previously added to Qmf through
        addBroker()
        """
        for b in self.qmf.getObjects(_class="broker"):
            if b.getBroker().getUrl() == url:
                return b
        return None

    def _find_create_broker(self, url):
        """
        Find a running broker through Qmf. If it does not exist, add it (assuming the broker is already running).
        """
        broker = self._Broker(url)
        self._brokers.append(broker)
        if self.qmf is not None:
            qmf_broker = self._find_qmf_broker(broker.url)
            if qmf_broker is None:
                self.qmf.addBroker(broker.url)
                broker.qmf_broker = self._find_qmf_broker(broker.url)
            else:
                broker.qmf_broker = qmf_broker
        return broker

    #--- Find/create/delete exchanges

    def _find_qmf_exchange(self, qmf_broker, name, type, alternate, durable, auto_delete):
        """
        Find Qmf exchange object
        """
        for e in self.qmf.getObjects(_class="exchange", _broker = qmf_broker.getBroker()):
            if e.name == name:
                if len(name) == 0 or (len(name) >= 4 and name[:4] == "amq."): return e # skip checks for special exchanges
                self.assertEqual(e.type, type,
                                 "Exchange \"%s\" exists, but is of unexpected type %s; expected type %s." %
                                 (name, e.type, type))
                self._check_optional_qmf_property(qmf_broker, "Exchange", e, "altExchange", alternate, True)
                self.assertEqual(e.durable, durable,
                                 "Exchange \"%s\" exists, but has incorrect durability. Found durable=%s, expected durable=%s" %
                                 (name, e.durable, durable))
                self.assertEqual(e.autoDelete, auto_delete,
                                 "Exchange \"%s\" exists, but has incorrect auto-delete property. Found %s, expected %s" %
                                 (name, e.autoDelete, auto_delete))
                return e
        return None

    def _find_create_qmf_exchange(self, qmf_broker, name, type, alternate, durable, auto_delete, args):
        """
        Find Qmf exchange object if exchange exists, create exchange and return its Qmf object if not
        """
        e = self._find_qmf_exchange(qmf_broker, name, type, alternate, durable, auto_delete)
        if e is not None: return e
        # Does not exist, so create it
        props = dict({"exchange-type": type, "type": type, "durable": durable, "auto-delete": auto_delete, "alternate-exchange": alternate}, **args)
        self._check_qmf_return(qmf_broker.create(type="exchange", name=name, properties=props, strict=True))
        e = self._find_qmf_exchange(qmf_broker, name, type, alternate, durable, auto_delete)
        self.assertNotEqual(e, None, "Creation of exchange %s on broker %s failed" % (name, qmf_broker.getBroker().getUrl()))
        return e

    def _find_delete_qmf_exchange(self, qmf_broker, name, type, alternate, durable, auto_delete):
        """
        Find and delete Qmf exchange object if it exists
        """
        e = self._find_qmf_exchange(qmf_broker, name, type, alternate, durable, auto_delete)
        if e is not None and not auto_delete:
            self._check_qmf_return(qmf_broker.delete(type="exchange", name=name, options={}))

    #--- Find/create/delete queues

    def _find_qmf_queue(self, qmf_broker, name, alternate_exchange, durable, exclusive, auto_delete):
        """
        Find a Qmf queue object
        """
        for q in self.qmf.getObjects(_class="queue", _broker = qmf_broker.getBroker()):
            if q.name == name:
                self._check_optional_qmf_property(qmf_broker, "Queue", q, "altExchange", alternate_exchange, True)
                self.assertEqual(q.durable, durable,
                                 "Queue \"%s\" exists, but has incorrect durable property. Found %s, expected %s" %
                                 (name, q.durable, durable))
                self.assertEqual(q.exclusive, exclusive,
                                 "Queue \"%s\" exists, but has incorrect exclusive property. Found %s, expected %s" %
                                 (name, q.exclusive, exclusive))
                self.assertEqual(q.autoDelete, auto_delete,
                                 "Queue \"%s\" exists, but has incorrect auto-delete property. Found %s, expected %s" %
                                 (name, q.autoDelete, auto_delete))
                return q
        return None

    def _find_create_qmf_queue(self, qmf_broker, name, alternate_exchange, durable, exclusive, auto_delete, args):
        """
        Find Qmf queue object if queue exists, create queue and return its Qmf object if not
        """
        q = self._find_qmf_queue(qmf_broker, name, alternate_exchange, durable, exclusive, auto_delete)
        if q is not None: return q
        # Queue does not exist, so create it
        props = dict({"durable": durable, "auto-delete": auto_delete, "exclusive": exclusive, "alternate-exchange": alternate_exchange}, **args)
        self._check_qmf_return(qmf_broker.create(type="queue", name=name, properties=props, strict=True))
        q = self._find_qmf_queue(qmf_broker, name, alternate_exchange, durable, exclusive, auto_delete)
        self.assertNotEqual(q, None, "Creation of queue %s on broker %s failed" % (name, qmf_broker.getBroker().getUrl()))
        return q

    def _find_delete_qmf_queue(self, qmf_broker, name, alternate_exchange, durable, exclusive, auto_delete, args):
        """
        Find and delete Qmf queue object if it exists
        """
        q = self._find_qmf_queue(qmf_broker, name, alternate_exchange, durable, exclusive, auto_delete)
        if q is not None and not auto_delete:
            self._check_qmf_return(qmf_broker.delete(type="queue", name=name, options={}))

    #--- Find/create/delete bindings (between an exchange and a queue)

    def _find_qmf_binding(self, qmf_broker, qmf_exchange, qmf_queue, binding_key, binding_args):
        """
        Find a Qmf binding object
        """
        for b in self.qmf.getObjects(_class="binding", _broker = qmf_broker.getBroker()):
            if b.exchangeRef == qmf_exchange.getObjectId() and b.queueRef == qmf_queue.getObjectId():
                if qmf_exchange.type != "fanout": # Fanout ignores the binding key, and always returns "" as the key
                    self.assertEqual(b.bindingKey, binding_key,
                                     "Binding between exchange %s and queue %s exists, but has mismatching binding key: Found %s, expected %s." %
                                     (qmf_exchange.name, qmf_queue.name, b.bindingKey, binding_key))
                self.assertEqual(b.arguments, binding_args,
                                 "Binding between exchange %s and queue %s exists, but has mismatching arguments: Found %s, expected %s" %
                                 (qmf_exchange.name, qmf_queue.name, b.arguments, binding_args))
                return b
        return None

    def _find_create_qmf_binding(self, qmf_broker, qmf_exchange, qmf_queue, binding_key, binding_args):
        """
        Find Qmf binding object if it exists, create binding and return its Qmf object if not
        """
        b = self._find_qmf_binding(qmf_broker, qmf_exchange, qmf_queue, binding_key, binding_args)
        if b is not None: return b
        # Does not exist, so create it
        self._check_qmf_return(qmf_broker.create(type="binding", name="%s/%s/%s" % (qmf_exchange.name, qmf_queue.name, binding_key), properties=binding_args, strict=True))
        b = self._find_qmf_binding(qmf_broker, qmf_exchange, qmf_queue, binding_key, binding_args)
        self.assertNotEqual(b, None, "Creation of binding between exchange %s and queue %s with key %s failed" %
                            (qmf_exchange.name, qmf_queue.name, binding_key))
        return b

    def _find_delete_qmf_binding(self, qmf_broker, qmf_exchange, qmf_queue, binding_key, binding_args):
        """
        Find and delete Qmf binding object if it exists
        """
        b = self._find_qmf_binding(qmf_broker, qmf_exchange, qmf_queue, binding_key, binding_args)
        if b is not None:
            if len(qmf_exchange.name) > 0: # not default exchange
                self._check_qmf_return(qmf_broker.delete(type="binding", name="%s/%s/%s" % (qmf_exchange.name, qmf_queue.name, binding_key), options={}))

    #--- Find/create a link

    def _find_qmf_link(self, qmf_from_broker_proxy, host, port):
        """
        Find a Qmf link object
        """
        for l in self.qmf.getObjects(_class="link", _broker=qmf_from_broker_proxy):
            if l.host == host and l.port == port:
                return l
        return None

    def _find_create_qmf_link(self, qmf_from_broker, qmf_to_broker_proxy, link_durable_flag, auth_mechanism, user_id,
                              password, transport, pause_interval, link_ready_timeout):
        """
        Find a Qmf link object if it exists, create it and return its Qmf link object if not
        """
        to_broker_host = qmf_to_broker_proxy.host
        to_broker_port = qmf_to_broker_proxy.port
        l = self._find_qmf_link(qmf_from_broker.getBroker(), to_broker_host,  to_broker_port)
        if l is not None: return l
        # Does not exist, so create it
        self._check_qmf_return(qmf_from_broker.connect(to_broker_host, to_broker_port, link_durable_flag, auth_mechanism, user_id, password, transport))
        l =  self._find_qmf_link(qmf_from_broker.getBroker(), to_broker_host,  to_broker_port)
        self.assertNotEqual(l, None, "Creation of link from broker %s to broker %s failed" %
                             (qmf_from_broker.getBroker().getUrl(), qmf_to_broker_proxy.getUrl()))
        self._wait_for_link(l, pause_interval, link_ready_timeout)
        return l

    def _wait_for_link(self, link, pause_interval, link_ready_timeout):
        """
        Wait for link to become active (state=Operational)
        """
        tot_time = 0
        link.update()
        while link.state != "Operational" and tot_time < link_ready_timeout:
            sleep(pause_interval)
            tot_time += pause_interval
            link.update()
        self.assertEqual(link.state, "Operational", "Timeout: Link not operational, state=%s" % link.state)

    #--- Find/create a bridge

    def _find_qmf_bridge(self, qmf_broker_proxy, qmf_link, source, destination, key):
        """
        Find a Qmf link object
        """
        for b in self.qmf.getObjects(_class="bridge", _broker=qmf_broker_proxy):
            if b.linkRef == qmf_link.getObjectId() and b.src == source and b.dest == destination and b.key == key:
                return b
        return None

    def _find_create_qmf_bridge(self, qmf_broker_proxy, qmf_link, queue_name, exch_name, topic_key,
                                queue_route_type_flag, bridge_durable_flag):
        """
        Find a Qmf bridge object if it exists, create it and return its Qmf object if not
        """
        if queue_route_type_flag:
            src = queue_name
            dest = exch_name
            key = ""
        else:
            src = exch_name
            dest = exch_name
            if len(topic_key) > 0:
                key = topic_key
            else:
                key = queue_name
        b = self._find_qmf_bridge(qmf_broker_proxy, qmf_link, src, dest, key)
        if b is not None:
            return b
        # Does not exist, so create it
        self._check_qmf_return(qmf_link.bridge(bridge_durable_flag, src, dest, key, "", "", queue_route_type_flag, False, False, 1, 0))
        b = self._find_qmf_bridge(qmf_broker_proxy, qmf_link, src, dest, key)
        self.assertNotEqual(b, None, "Bridge creation failed: src=%s dest=%s key=%s" % (src, dest, key))
        return b

    def _wait_for_bridge(self, bridge, src_broker, dest_broker, exch_name, queue_name, topic_key, pause_interval,
                         bridge_ready_timeout):
        """
        Wait for bridge to become active by sending messages over the bridge at 1 sec intervals until they are
        observed at the destination.
        """
        tot_time = 0
        active = False
        send_session = src_broker.session("tx")
        sender = send_session.sender(self._get_send_address(exch_name, queue_name))
        src_receive_session = src_broker.session("src_rx")
        src_receiver = src_receive_session.receiver(queue_name)
        dest_receive_session = dest_broker.session("dest_rx")
        dest_receiver = dest_receive_session.receiver(queue_name)
        while not active and tot_time < bridge_ready_timeout:
            sender.send(Message("xyz123", subject = self._get_msg_subject(topic_key)))
            try:
                src_receiver.fetch(timeout = 0)
                src_receive_session.acknowledge()
                # Keep receiving msgs, as several may have accumulated
                while True:
                    dest_receiver.fetch(timeout = 0)
                    dest_receive_session.acknowledge()
                    sleep(1)
                    active = True
            except Empty:
                sleep(pause_interval)
                tot_time += pause_interval
        dest_receiver.close()
        dest_receive_session.close()
        src_receiver.close()
        src_receive_session.close()
        sender.close()
        send_session.close()
        self.assertTrue(active, "Bridge failed to become active after %ds: %s" % (bridge_ready_timeout, bridge))

    #--- Find/create/delete utility functions

    def _create_and_bind(self, qmf_broker, exchange_args, queue_args, binding_args):
        """
        Create a binding between a named exchange and queue on a broker
        """
        e = self._find_create_qmf_exchange(qmf_broker, **exchange_args)
        q = self._find_create_qmf_queue(qmf_broker, **queue_args)
        return self._find_create_qmf_binding(qmf_broker, e, q, **binding_args)

    def _check_alt_exchange(self, qmf_broker, alt_exch_name, alt_exch_type, alt_exch_op):
        """
        Check for existence of alternate exchange. Return the Qmf exchange proxy object for the alternate exchange
        """
        if len(alt_exch_name) == 0: return None
        if alt_exch_op == _alt_exch_ops.create:
            return self._find_create_qmf_exchange(qmf_broker=qmf_broker, name=alt_exch_name, type=alt_exch_type,
                                                  alternate="", durable=False, auto_delete=False, args={})
        if alt_exch_op == _alt_exch_ops.delete:
            return self._find_delete_qmf_exchange(qmf_broker=qmf_broker, name=alt_exch_name, type=alt_exch_type,
                                                  alternate="", durable=False, auto_delete=False)
        return self._find_qmf_exchange(qmf_broker=qmf_broker, name=alt_exchange_name, type=alt_exchange_type,
                                       alternate="", durable=False, auto_delete=False)

    def _delete_queue_binding(self, qmf_broker, exchange_args, queue_args, binding_args):
        """
        Delete a queue and the binding between it and the exchange
        """
        e = self._find_qmf_exchange(qmf_broker, exchange_args["name"], exchange_args["type"], exchange_args["alternate"], exchange_args["durable"], exchange_args["auto_delete"])
        q = self._find_qmf_queue(qmf_broker, queue_args["name"], queue_args["alternate_exchange"], queue_args["durable"], queue_args["exclusive"], queue_args["auto_delete"])
        self._find_delete_qmf_binding(qmf_broker, e, q, **binding_args)
        self._find_delete_qmf_queue(qmf_broker, **queue_args)

    def _create_route(self, queue_route_type_flag, src_broker, dest_broker, exch_name, queue_name, topic_key,
                      link_durable_flag, bridge_durable_flag, auth_mechanism, user_id, password, transport,
                      pause_interval = 1, link_ready_timeout = 20, bridge_ready_timeout = 20):
        """
        Create a route from a source broker to a destination broker
        """
        l = self._find_create_qmf_link(dest_broker.qmf_broker, src_broker.qmf_broker.getBroker(), link_durable_flag,
                                       auth_mechanism, user_id, password, transport, pause_interval, link_ready_timeout)
        self._links.append(l)
        b = self._find_create_qmf_bridge(dest_broker.qmf_broker.getBroker(), l, queue_name, exch_name, topic_key,
                                         queue_route_type_flag, bridge_durable_flag)
        self._bridges.append(b)
        self._wait_for_bridge(b, src_broker, dest_broker, exch_name, queue_name, topic_key, pause_interval, bridge_ready_timeout)

    # Parameterized test - entry point for tests

    def _do_test(self,
                 test_name,                         # Name of test
                 exch_name = "amq.direct",          # Remote exchange name
                 exch_type = "direct",              # Remote exchange type
                 exch_alt_exch = "",                # Remote exchange alternate exchange
                 exch_alt_exch_type = "direct",     # Remote exchange alternate exchange type
                 exch_durable_flag = False,         # Remote exchange durability
                 exch_auto_delete_flag = False,     # Remote exchange auto-delete property
                 exch_x_args = {},                  # Remote exchange args
                 queue_alt_exch = "",               # Remote queue alternate exchange
                 queue_alt_exch_type = "direct",    # Remote queue alternate exchange type
                 queue_durable_flag = False,        # Remote queue durability
                 queue_exclusive_flag = False,      # Remote queue exclusive property
                 queue_auto_delete_flag = False,    # Remote queue auto-delete property
                 queue_x_args = {},                 # Remote queue args
                 binding_durable_flag = False,      # Remote binding durability
                 binding_x_args = {},               # Remote binding args
                 topic_key = "",                    # Binding key For remote topic exchanges only
                 msg_count = 10,                    # Number of messages to send
                 msg_durable_flag = False,          # Message durability
                 link_durable_flag = False,         # Route link durability
                 bridge_durable_flag = False,       # Route bridge durability
                 queue_route_type_flag = False,     # Route type: false = bridge route, true = queue route
                 enq_txn_size = 0,                  # Enqueue transaction size, 0 = no transactions
                 deq_txn_size = 0,                  # Dequeue transaction size, 0 = no transactions
                 alt_exch_op = _alt_exch_ops.create,# Op on alt exch [create (ensure present), delete (ensure not present), none (neither create nor delete)]
                 auth_mechanism = "",               # Authorization mechanism for linked broker
                 user_id = "",                      # User ID for authorization on linked broker
                 password = "",                     # Password for authorization on linked broker
                 transport = "tcp"                  # Transport for route to linked broker
                 ):
        """
        Parameterized federation test. Sets up a federated link between a source broker and a destination broker and
        checks that messages correctly pass over the link to the destination. Where appropriate (non-queue-routes), also
        checks for the presence of messages on the source broker.

        In these tests, the concept is to create a LOCAL broker, then create a link to a REMOTE broker using federation.
        In other words, the messages sent to the LOCAL broker will be replicated on the REMOTE broker, and tests are
        performed on the REMOTE broker to check that the required messages are present. In the case of regular routes,
        the LOCAL broker will also retain the messages, and a similar test is performed on this broker.

        TODO: There are several items to improve here:
        1. _do_test() is rather general. Rather create a version for each exchange type and test the exchange/queue
           interaction in more detail based on the exchange type
        2. Add a headers and an xml exchange type
        3. Restructure the tests to start and stop brokers directly rather than relying on previously
           started brokers. Then persistence can be checked by stopping and restarting the brokers. In particular,
           test the persistence of links and bridges, both of which take a persistence flag.
        4. Test the behavior of the alternate exchanges when messages are sourced through a link. Also check behavior
           when the alternate exchange is not present or is deleted after the reference is made.
        5. Test special queue types (eg LVQ)
        """
        local_broker = self._get_broker("local-port")
        remote_broker = self._get_broker("remote-port")

        # Check alternate exchanges exist (and create them if not) on both local and remote brokers
        self._check_alt_exchange(local_broker.qmf_broker, exch_alt_exch, exch_alt_exch_type, alt_exch_op)
        self._check_alt_exchange(local_broker.qmf_broker, queue_alt_exch, queue_alt_exch_type, alt_exch_op)
        self._check_alt_exchange(remote_broker.qmf_broker, exch_alt_exch, exch_alt_exch_type, alt_exch_op)
        self._check_alt_exchange(remote_broker.qmf_broker, queue_alt_exch, queue_alt_exch_type, alt_exch_op)

        queue_name = "queue_%s" % test_name
        exchange_args = {"name": exch_name, "type": exch_type, "alternate": exch_alt_exch,
                         "durable": exch_durable_flag, "auto_delete": exch_auto_delete_flag, "args": exch_x_args}
        queue_args = {"name": queue_name, "alternate_exchange": queue_alt_exch, "durable": queue_durable_flag,
                      "exclusive": queue_exclusive_flag, "auto_delete": queue_auto_delete_flag, "args": queue_x_args}
        binding_args = {"binding_args": binding_x_args}
        if exch_type == "topic":
            self.assertTrue(len(topic_key) > 0, "Topic exchange selected, but no topic key was set.")
            binding_args["binding_key"] = topic_key
        elif exch_type == "direct":
            binding_args["binding_key"] = queue_name
        else:
            binding_args["binding_key"] = ""
        self._create_and_bind(qmf_broker=local_broker.qmf_broker, exchange_args=exchange_args, queue_args=queue_args, binding_args=binding_args)
        self._create_and_bind(qmf_broker=remote_broker.qmf_broker, exchange_args=exchange_args, queue_args=queue_args, binding_args=binding_args)
        self._create_route(queue_route_type_flag, local_broker, remote_broker, exch_name, queue_name, topic_key,
                           link_durable_flag, bridge_durable_flag, auth_mechanism, user_id, password, transport)

        self._send_msgs("send_session", local_broker, addr = self._get_send_address(exch_name, queue_name),
                        msg_count = msg_count,  topic_key = topic_key, msg_durable_flag = msg_durable_flag, enq_txn_size = enq_txn_size)
        if not queue_route_type_flag:
            self._receive_msgs("local_receive_session", local_broker, addr = queue_name, msg_count = msg_count, deq_txn_size = deq_txn_size)
        self._receive_msgs("remote_receive_session", remote_broker, addr = queue_name, msg_count = msg_count, deq_txn_size = deq_txn_size, timeout = 5)

        # Clean up
        self._delete_queue_binding(qmf_broker=local_broker.qmf_broker, exchange_args=exchange_args, queue_args=queue_args, binding_args=binding_args)
        self._delete_queue_binding(qmf_broker=remote_broker.qmf_broker, exchange_args=exchange_args, queue_args=queue_args, binding_args=binding_args)

class A_ShortTests(QmfTestBase010):

    def test_route_defaultExch(self):
        self._do_test(self._get_name())

    def test_queueRoute_defaultExch(self):
        self._do_test(self._get_name(), queue_route_type_flag=True)


class A_LongTests(QmfTestBase010):

    def test_route_amqDirectExch(self):
        self._do_test(self._get_name(), exch_name="amq.direct")

    def test_queueRoute_amqDirectExch(self):
        self._do_test(self._get_name(), exch_name="amq.direct", queue_route_type_flag=True)


    def test_route_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange")

    def test_queueRoute_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_route_type_flag=True)


    def test_route_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout")

    def test_queueRoute_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_route_type_flag=True)


    def test_route_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#")

    def test_queueRoute_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_route_type_flag=True)


class B_ShortTransactionTests(QmfTestBase010):

    def test_txEnq01_route_defaultExch(self):
        self._do_test(self._get_name(), enq_txn_size=1)

    def test_txEnq01_queueRoute_defaultExch(self):
        self._do_test(self._get_name(), queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq01_txDeq01_route_defaultExch(self):
        self._do_test(self._get_name(), enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_defaultExch(self):
        self._do_test(self._get_name(), queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)


class B_LongTransactionTests(QmfTestBase010):

    def test_txEnq10_route_defaultExch(self):
        self._do_test(self._get_name(), enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_defaultExch(self):
        self._do_test(self._get_name(), queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)




    def test_txEnq01_route_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", enq_txn_size=1)

    def test_txEnq01_queueRoute_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq10_route_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq01_txDeq01_route_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)


    def test_txEnq01_route_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", enq_txn_size=1)

    def test_txEnq01_queueRoute_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq10_route_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq01_txDeq01_route_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)


    def test_txEnq01_route_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", enq_txn_size=1)

    def test_txEnq01_queueRoute_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq10_route_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq01_txDeq01_route_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)


class E_ShortPersistenceTests(QmfTestBase010):

    def test_route_durQueue_defaultExch(self):
        self._do_test(self._get_name(), queue_durable_flag=True)

    def test_route_durMsg_durQueue_defaultExch(self):
        self._do_test(self._get_name(), msg_durable_flag=True, queue_durable_flag=True)

    def test_queueRoute_durQueue_defaultExch(self):
        self._do_test(self._get_name(), queue_durable_flag=True, queue_route_type_flag=True)

    def test_queueRoute_durMsg_durQueue_defaultExch(self):
        self._do_test(self._get_name(), msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True)


class E_LongPersistenceTests(QmfTestBase010):


    def test_route_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_durable_flag=True)

    def test_route_durMsg_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", msg_durable_flag=True, queue_durable_flag=True)

    def test_queueRoute_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_durable_flag=True, queue_route_type_flag=True)

    def test_queueRoute_durMsg_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True)


    def test_route_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_durable_flag=True)

    def test_route_durMsg_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", msg_durable_flag=True, queue_durable_flag=True)

    def test_queueRoute_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_durable_flag=True, queue_route_type_flag=True)

    def test_queueRoute_durMsg_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True)


    def test_route_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_durable_flag=True)

    def test_route_durMsg_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", msg_durable_flag=True, queue_durable_flag=True)

    def test_queueRoute_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_durable_flag=True, queue_route_type_flag=True)

    def test_queueRoute_durMsg_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True)


class F_ShortPersistenceTransactionTests(QmfTestBase010):

    def test_txEnq01_route_durQueue_defaultExch(self):
        self._do_test(self._get_name(), queue_durable_flag=True, enq_txn_size=1)

    def test_txEnq01_route_durMsg_durQueue_defaultExch(self):
        self._do_test(self._get_name(), msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=1)

    def test_txEnq01_queueRoute_durQueue_defaultExch(self):
        self._do_test(self._get_name(), queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq01_queueRoute_durMsg_durQueue_defaultExch(self):
        self._do_test(self._get_name(), msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq01_txDeq01_route_durQueue_defaultExch(self):
        self._do_test(self._get_name(), queue_durable_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_route_durMsg_durQueue_defaultExch(self):
        self._do_test(self._get_name(), msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_durQueue_defaultExch(self):
        self._do_test(self._get_name(), queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_durMsg_durQueue_defaultExch(self):
        self._do_test(self._get_name(), msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)


class F_LongPersistenceTransactionTests(QmfTestBase010):

    def test_txEnq10_route_durQueue_defaultExch(self):
        self._do_test(self._get_name(), queue_durable_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_route_durMsg_durQueue_defaultExch(self):
        self._do_test(self._get_name(), msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_durQueue_defaultExch(self):
        self._do_test(self._get_name(), queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_durMsg_durQueue_defaultExch(self):
        self._do_test(self._get_name(), msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)




    def test_txEnq01_route_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_durable_flag=True, enq_txn_size=1)

    def test_txEnq01_route_durMsg_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=1)

    def test_txEnq01_queueRoute_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq01_queueRoute_durMsg_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq10_route_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_durable_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_route_durMsg_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_durMsg_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq01_txDeq01_route_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_durable_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_route_durMsg_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_durMsg_durQueue_directExch(self):
        self._do_test(self._get_name(), exch_name="testDirectExchange", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)


    def test_txEnq01_route_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_durable_flag=True, enq_txn_size=1)

    def test_txEnq01_route_durMsg_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=1)

    def test_txEnq01_queueRoute_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq01_queueRoute_durMsg_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq10_route_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_durable_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_route_durMsg_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_durMsg_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq01_txDeq01_route_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_durable_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_route_durMsg_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_durMsg_durQueue_fanoutExch(self):
        self._do_test(self._get_name(), exch_name="testFanoutExchange", exch_type="fanout", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)


    def test_txEnq01_route_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_durable_flag=True, enq_txn_size=1)

    def test_txEnq01_route_durMsg_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=1)

    def test_txEnq01_queueRoute_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq01_queueRoute_durMsg_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1)

    def test_txEnq10_route_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_durable_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_route_durMsg_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq10_queueRoute_durMsg_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=10, msg_count = 103)

    def test_txEnq01_txDeq01_route_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_durable_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_route_durMsg_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", msg_durable_flag=True, queue_durable_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)

    def test_txEnq01_txDeq01_queueRoute_durMsg_durQueue_topicExch(self):
        self._do_test(self._get_name(), exch_name="testTopicExchange", exch_type="topic", topic_key=self._get_name()+".#", msg_durable_flag=True, queue_durable_flag=True, queue_route_type_flag=True, enq_txn_size=1, deq_txn_size=1)


