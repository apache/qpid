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

import sys
import qpid
from qpid.util import connect
from qpid.connection import Connection
from qpid.datatypes import uuid4
from qpid.testlib import TestBase010
from qmf.console import Session
from qpid.datatypes import Message
import qpid.messaging
from time import sleep
from os import environ, popen

class ACLFile:
    def __init__(self, policy='data_dir/policy.acl'):
        self.f = open(policy,'w')

    def write(self,line):
        self.f.write(line)

    def close(self):
        self.f.close()

class QueueredirectTests(TestBase010):

    def get_session(self, user, passwd):
        socket = connect(self.broker.host, self.broker.port)
        connection = Connection (sock=socket, username=user, password=passwd,
                                 mechanism="PLAIN")
        connection.start()
        return connection.session(str(uuid4()))

    def reload_acl(self):
        result = None
        try:
            self.broker_access.reloadAclFile()
        except Exception, e:
            result = str(e)
        return result

    def get_acl_file(self):
        return ACLFile(self.config.defines.get("policy-file", "data_dir/policy.acl"))

    def setUp(self):
        aclf = self.get_acl_file()
        aclf.write('acl allow all all\n')
        aclf.close()
        TestBase010.setUp(self)
        self.startBrokerAccess()
        self.reload_acl()

    def tearDown(self):
        aclf = self.get_acl_file()
        aclf.write('acl allow all all\n')
        aclf.close()
        self.reload_acl()
        TestBase010.tearDown(self)


    def redirect(self, srcQueue, tgtQueue, expectPass, failMessage):
        try:
            result = {}
            result = self.broker_access.Redirect(srcQueue, tgtQueue)
            if not expectPass:
                self.fail("src:" + srcQueue + ", tgt:" + tgtQueue + " - " + failMessage)
        except Exception, e:
            if expectPass:
                self.fail("src:" + srcQueue + ", tgt:" + tgtQueue + " - " + failMessage)

    def create_queue(self, session, name, autoDelete):
        try:
            session.queue_declare(queue=name, auto_delete=autoDelete)
        except Exception, e:
            self.fail("Should allow create queue " + name)

    def _start_qpid_send(self, queue, count, content="X", capacity=100):
        """ Use the qpid-send client to generate traffic to a queue.
        """
        command = "qpid-send" + \
                   " -b" +  " %s:%s" % (self.broker.host, self.broker.port) \
                   + " -a " + str(queue) \
                   + " --messages " +  str(count) \
                   + " --content-string " + str(content) \
                   + " --capacity " + str(capacity)
        return popen(command)

    def _start_qpid_receive(self, queue, count, timeout=5):
        """ Use the qpid-receive client to consume from a queue.
        Note well: prints one line of text to stdout for each consumed msg.
        """
        command = "qpid-receive" + \
                   " -b " +  "%s:%s" % (self.broker.host, self.broker.port) \
                   + " -a " + str(queue) \
                   + " --messages " + str(count) \
                   + " --timeout " + str(timeout) \
                   + " --print-content yes"
        return popen(command)



   #=====================================
   # QT queue tests
   #=====================================

    def test_010_deny_backing_up_a_nonexistant_queue(self):
        session = self.get_session('bob','bob')
        self.redirect("A010", "A010", False, "Should not allow redirect to non-existent queue A010")
        session.close()

    def test_020_deny_destroy_redirect(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A020", False)
        self.redirect("A020", "", False, "Should not allow destroying redirect")
        session.close()

    def test_030_deny_redirecting_to_nonexistent_queue(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A030", False)
        self.redirect("A030", "Axxx", False, "Should not allow redirect with non-existent queue Axxx")
        session.close()

    def test_040_deny_queue_redirecting_to_itself(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A040", False)
        self.redirect("A040", "A040", False, "Should not allow redirect with itself")
        session.close()

    def test_050_deny_redirecting_autodelete_queue(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A050", True)
        self.create_queue(session, "B050", False)
        self.redirect("A050", "B050", False, "Should not allow redirect with autodelete source queue")
        self.redirect("B050", "A050", False, "Should not allow redirect with autodelete target queue")
        session.close()

    def test_100_create_redirect_queue_pair(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A100", False)
        self.create_queue(session, "B100", False)
        self.redirect("A100", "B100", True, "Should allow redirect")
        session.close()

    def test_110_deny_adding_second_redirect_to_queue(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A110", False)
        self.create_queue(session, "B110", False)
        self.create_queue(session, "C110", False)
        self.redirect("A110", "B110", True,  "Should allow redirect")
        self.redirect("A110", "C110", False, "Should deny second redirect")
        self.redirect("C110", "B110", False, "Should deny second redirect")
        session.close()

    def test_120_verify_redirect_to_target(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A120", False)
        self.create_queue(session, "B120", False)

        # Send messages to original queue
        sndr1 = self._start_qpid_send("A120", count=5, content="A120-before-rebind");
        sndr1.close()

        # redirect
        self.redirect("A120", "B120", True,  "Should allow redirect")

        # Send messages to original queue
        sndr2 = self._start_qpid_send("A120", count=3, content="A120-after-rebind");
        sndr2.close()

        # drain the queue
        rcvr = self._start_qpid_receive("A120",
                                        count=5)
        count = 0;
        x = rcvr.readline()    # prints a line for each received msg
        while x:
#            print "Read from A120 " + x
            count += 1;
            x = rcvr.readline()

        self.assertEqual(count, 5)

        # drain the queue
        rcvrB = self._start_qpid_receive("B120",
                                        count=3)
        count = 0;
        x = rcvrB.readline()    # prints a line for each received msg
        while x:
#            print "Read from B120 " + x
            count += 1;
            x = rcvrB.readline()

        self.assertEqual(count, 3)

        ###session.close()

    def test_140_verify_redirect_to_source(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A140", False)
        self.create_queue(session, "B140", False)

        # Send messages to target queue - these go onto B
        sndr1 = self._start_qpid_send("B140", count=5, content="B140-before-rebind");
        sndr1.close()

        # redirect
        self.redirect("A140", "B140", True,  "Should allow redirect")

        # Send messages to target queue - these go onto A
        sndr2 = self._start_qpid_send("B140", count=3, content="B140-after-rebind");
        sndr2.close()

        # drain the queue
        rcvr = self._start_qpid_receive("B140", count=5)
        count = 0;
        x = rcvr.readline()    # prints a line for each received msg
        while x:
            #            print "Read from B140 " + x
            count += 1;
            x = rcvr.readline()

        self.assertEqual(count, 5)

        # drain the queue
        rcvrB = self._start_qpid_receive("A140", count=3)
        count = 0;
        x = rcvrB.readline()    # prints a line for each received msg
        while x:
            #            print "Read from A140 " + x
            count += 1;
            x = rcvrB.readline()

        self.assertEqual(count, 3)

        ###session.close()

    def test_150_queue_deletion_destroys_redirect(self):
        session = self.get_session('bob','bob')
        self.create_queue(session, "A150", False)
        self.create_queue(session, "B150", False)
        self.create_queue(session, "C150", False)

        # redirect
        self.redirect("A150", "B150", True,  "Should allow redirect")

        self.redirect("A150", "C150", False, "A is already redirected")

        alice = BrokerAdmin(self.config.broker, "bob", "bob")
        alice.delete_queue("B150") #should pass

        self.redirect("A150", "C150", True,  "Should allow redirect")
        session.close()

##############################################################################################
class BrokerAdmin:
    def __init__(self, broker, username=None, password=None):
        self.connection = qpid.messaging.Connection(broker)
        if username:
            self.connection.username = username
            self.connection.password = password
            self.connection.sasl_mechanisms = "PLAIN"
        self.connection.open()
        self.session = self.connection.session()
        self.sender = self.session.sender("qmf.default.direct/broker")
        self.reply_to = "responses-#; {create:always}"
        self.receiver = self.session.receiver(self.reply_to)

    def invoke(self, method, arguments):
        content = {
            "_object_id": {"_object_name": "org.apache.qpid.broker:broker:amqp-broker"},
            "_method_name": method,
            "_arguments": arguments
            }
        request = qpid.messaging.Message(reply_to=self.reply_to, content=content)
        request.properties["x-amqp-0-10.app-id"] = "qmf2"
        request.properties["qmf.opcode"] = "_method_request"
        self.sender.send(request)
        response = self.receiver.fetch()
        self.session.acknowledge()
        if response.properties['x-amqp-0-10.app-id'] == 'qmf2':
            if response.properties['qmf.opcode'] == '_method_response':
                return response.content['_arguments']
            elif response.properties['qmf.opcode'] == '_exception':
                raise Exception(response.content['_values'])
            else: raise Exception("Invalid response received, unexpected opcode: %s" % response.properties['qmf.opcode'])
        else: raise Exception("Invalid response received, not a qmfv2 method: %s" % response.properties['x-amqp-0-10.app-id'])

    def create_exchange(self, name, exchange_type=None, options={}):
        properties = options
        if exchange_type: properties["exchange_type"] = exchange_type
        self.invoke("create", {"type": "exchange", "name":name, "properties":properties})

    def create_queue(self, name, properties={}):
        self.invoke("create", {"type": "queue", "name":name, "properties":properties})

    def delete_exchange(self, name):
        self.invoke("delete", {"type": "exchange", "name":name})

    def delete_queue(self, name):
        self.invoke("delete", {"type": "queue", "name":name})
