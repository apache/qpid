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
from qpid.testlib import TestBase010, testrunner
from qpid.management import managementChannel, managementClient
from qpid.datatypes import Message
from qpid.queue import Empty
from time import sleep

def add_module(args=sys.argv[1:]):
    for a in args:
        if a.startswith("federation"):
            return False
    return True

def scan_args(name, default=None, args=sys.argv[1:]):
    if (name in args):
        pos = args.index(name)
        return args[pos + 1]
    elif default:
        return default
    else:
        print "Please specify extra argument: %s" % name
        sys.exit(2)

def extract_args(name, args):
    if (name in args):
        pos = args.index(name)
        del args[pos:pos+2]
    else:
        return None

def remote_host():
    return scan_args("--remote-host", "localhost")

def remote_port():
    return int(scan_args("--remote-port"))

class Helper:
    def __init__(self, parent):
        self.parent = parent
        self.session = parent.conn.session("Helper")
        self.mc  = managementClient(self.session.spec)
        self.mch = self.mc.addChannel(self.session)
        self.mc.syncWaitForStable(self.mch)

    def shutdown (self):
        self.mc.removeChannel (self.mch)

    def get_objects(self, type):
        return self.mc.syncGetObjects(self.mch, type)

    def get_object(self, type, position = 1, expected = None):
        objects = self.get_objects(type)
        if not expected: expected = position
        self.assertEqual(len(objects), expected)
        return objects[(position - 1)]

        
    def call_method(self, object, method, args=None):
        res = self.mc.syncCallMethod(self.mch, object.id, object.classKey, method, args)
        self.assertEqual(res.status,     0)
        self.assertEqual(res.statusText, "OK")
        return res
    
    def assertEqual(self, a, b):
        self.parent.assertEqual(a, b)

class FederationTests(TestBase010):

    def test_bridge_create_and_close(self):
        mgmt = Helper(self)
        broker = mgmt.get_object("broker")

        mgmt.call_method(broker, "connect", {"host":remote_host(), "port":remote_port()})
        link = mgmt.get_object("link")
            
        mgmt.call_method(link, "bridge", {"durable":0, "src":"amq.direct", "dest":"amq.direct", "key":"my-key"})
        bridge = mgmt.get_object("bridge")
            
        mgmt.call_method(bridge, "close")
        mgmt.call_method(link, "close")

        sleep(6)
        self.assertEqual(len(mgmt.get_objects("bridge")), 0)
        self.assertEqual(len(mgmt.get_objects("link")), 0)

        mgmt.shutdown ()

    def test_pull_from_exchange(self):
        session = self.session
        
        mgmt = Helper(self)
        broker = mgmt.get_object("broker")

        mgmt.call_method(broker, "connect", {"host":remote_host(), "port":remote_port()})
        link = mgmt.get_object("link")
        
        mgmt.call_method(link, "bridge", {"durable":0, "src":"amq.direct", "dest":"amq.fanout", "key":"my-key"})
        bridge = mgmt.get_object("bridge")

        #setup queue to receive messages from local broker
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")
        sleep(6)

        #send messages to remote broker and confirm it is routed to local broker
        r_conn = self.connect(host=remote_host(), port=remote_port())
        r_session = r_conn.session("test_pull_from_exchange")

        for i in range(1, 11):
            dp = r_session.delivery_properties(routing_key="my-key")
            r_session.message_transfer(destination="amq.direct", message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            msg = queue.get(timeout=5)
            self.assertEqual("Message %d" % i, msg.body)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        mgmt.call_method(bridge, "close")
        mgmt.call_method(link, "close")
        sleep(6)
        self.assertEqual(len(mgmt.get_objects("bridge")), 0)
        self.assertEqual(len(mgmt.get_objects("link")), 0)

        mgmt.shutdown()

    def test_pull_from_queue(self):
        session = self.session

        #setup queue on remote broker and add some messages
        r_conn = self.connect(host=remote_host(), port=remote_port())
        r_session = r_conn.session("test_pull_from_queue")
        r_session.queue_declare(queue="my-bridge-queue", exclusive=True, auto_delete=True)
        for i in range(1, 6):
            dp = r_session.delivery_properties(routing_key="my-bridge-queue")
            r_session.message_transfer(message=Message(dp, "Message %d" % i))

        #setup queue to receive messages from local broker
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        mgmt = Helper(self)
        broker = mgmt.get_object("broker")

        mgmt.call_method(broker, "connect", {"host":remote_host(), "port":remote_port()})
        link = mgmt.get_object("link")

        mgmt.call_method(link, "bridge", {"durable":0, "src":"my-bridge-queue", "dest":"amq.fanout",
                                          "key":"", "tag":"", "excludes":"", "srcIsQueue":1})
        sleep(6)
        bridge = mgmt.get_object("bridge")

        #add some more messages (i.e. after bridge was created)
        for i in range(6, 11):
            dp = r_session.delivery_properties(routing_key="my-bridge-queue")
            r_session.message_transfer(message=Message(dp, "Message %d" % i))

        for i in range(1, 11):
            try:
                msg = queue.get(timeout=5)
                self.assertEqual("Message %d" % i, msg.body)
            except Empty:
                self.fail("Failed to find expected message containing 'Message %d'" % i)
        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None


        mgmt.call_method(bridge, "close")
        mgmt.call_method(link, "close")
        sleep(6)
        self.assertEqual(len(mgmt.get_objects("bridge")), 0)
        self.assertEqual(len(mgmt.get_objects("link")), 0)

        mgmt.shutdown ()

    def test_tracing(self):
        session = self.session
        
        mgmt = Helper(self)
        broker = mgmt.get_object("broker")

        mgmt.call_method(broker, "connect", {"host":remote_host(), "port":remote_port()})
        link = mgmt.get_object("link")
        
        mgmt.call_method(link, "bridge", {"durable":0, "src":"amq.direct", "dest":"amq.fanout", "key":"my-key",
                                          "tag":"my-bridge-id", "excludes":"exclude-me,also-exclude-me"})
        sleep(6)
        bridge = mgmt.get_object("bridge")

        #setup queue to receive messages from local broker
        session.queue_declare(queue="fed1", exclusive=True, auto_delete=True)
        session.exchange_bind(queue="fed1", exchange="amq.fanout")
        self.subscribe(queue="fed1", destination="f1")
        queue = session.incoming("f1")

        #send messages to remote broker and confirm it is routed to local broker
        r_conn = self.connect(host=remote_host(), port=remote_port())
        r_session = r_conn.session("test_tracing")

        trace = [None, "exclude-me", "a,exclude-me,b", "also-exclude-me,c", "dont-exclude-me"]
        body = ["yes", "first-bad", "second-bad", "third-bad", "yes"]
        for b, t in zip(body, trace):
            headers = {}
            if (t): headers["x-qpid.trace"]=t
            dp = r_session.delivery_properties(routing_key="my-key")
            mp = r_session.message_properties(application_headers=headers)
            r_session.message_transfer(destination="amq.direct", message=Message(dp, mp, b))

        for e in ["my-bridge-id", "dont-exclude-me,my-bridge-id"]:
            msg = queue.get(timeout=5)
            self.assertEqual("yes", msg.body)
            self.assertEqual(e, self.getAppHeader(msg, "x-qpid.trace"))

        try:
            extra = queue.get(timeout=1)
            self.fail("Got unexpected message in queue: " + extra.body)
        except Empty: None

        mgmt.call_method(bridge, "close")
        mgmt.call_method(link, "close")
        sleep(6)
        self.assertEqual(len(mgmt.get_objects("bridge")), 0)
        self.assertEqual(len(mgmt.get_objects("link")), 0)

        mgmt.shutdown ()

    def getProperty(self, msg, name):
        for h in msg.headers:
            if hasattr(h, name): return getattr(h, name)
        return None            

    def getAppHeader(self, msg, name):
        headers = self.getProperty(msg, "application_headers")
        if headers:
            return headers[name]
        return None            

if __name__ == '__main__':
    args = sys.argv[1:]
    #need to remove the extra options from args as test runner doesn't recognise them
    extract_args("--remote-port", args)
    extract_args("--remote-host", args)

    if add_module():
        #add module(s) to run to testrunners args
        args.append("federation") 
    
    if not testrunner.run(args): sys.exit(1)
