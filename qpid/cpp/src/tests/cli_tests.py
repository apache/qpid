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
import os
from qpid.testlib import TestBase010, testrunner
from qpid.datatypes import Message
from qpid.queue import Empty
from time import sleep

def add_module(args=sys.argv[1:]):
    for a in args:
        if a.startswith("cli"):
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

def cli_dir():
    return scan_args("--cli-dir")

class CliTests(TestBase010):

    def makeQueue(self, qname, arguments):
        ret = os.system(self.command(" add queue " + qname + " " + arguments))
        self.assertEqual(ret, 0)
        queues = self.qmf.getObjects(_class="queue")
        for queue in queues:
            if queue.name == qname:
                return queue
        assert False

    def test_queue_params(self):
        self.startQmf()
        queue1 = self.makeQueue("test_queue_params1", "--limit-policy none")
        queue2 = self.makeQueue("test_queue_params2", "--limit-policy reject")
        queue3 = self.makeQueue("test_queue_params3", "--limit-policy flow-to-disk")
        queue4 = self.makeQueue("test_queue_params4", "--limit-policy ring")
        queue5 = self.makeQueue("test_queue_params5", "--limit-policy ring-strict")

        LIMIT = "qpid.policy_type"
        assert LIMIT not in queue1.arguments
        self.assertEqual(queue2.arguments[LIMIT], "reject")
        self.assertEqual(queue3.arguments[LIMIT], "flow_to_disk")
        self.assertEqual(queue4.arguments[LIMIT], "ring")
        self.assertEqual(queue5.arguments[LIMIT], "ring_strict")

        queue6 = self.makeQueue("test_queue_params6", "--order fifo")
        queue7 = self.makeQueue("test_queue_params7", "--order lvq")
        queue8 = self.makeQueue("test_queue_params8", "--order lvq-no-browse")

        LVQ = "qpid.last_value_queue"
        LVQNB = "qpid.last_value_queue_no_browse"

        assert LVQ not in queue6.arguments
        assert LVQ     in queue7.arguments
        assert LVQ not in queue8.arguments

        assert LVQNB not in queue6.arguments
        assert LVQNB not in queue7.arguments
        assert LVQNB     in queue8.arguments

    def test_qpid_config(self):
        self.startQmf();
        qmf = self.qmf
        qname = "test_qpid_config"

        ret = os.system(self.command(" add queue " + qname))
        self.assertEqual(ret, 0)
        queues = qmf.getObjects(_class="queue")
        found = False
        for queue in queues:
            if queue.name == qname:
                self.assertEqual(queue.durable, False)
                found = True
        self.assertEqual(found, True)

        ret = os.system(self.command(" del queue " + qname))
        self.assertEqual(ret, 0)
        queues = qmf.getObjects(_class="queue")
        found = False
        for queue in queues:
            if queue.name == qname:
                found = True
        self.assertEqual(found, False)

    def test_qpid_config_durable(self):
        self.startQmf();
        qmf = self.qmf
        qname = "test_qpid_config"

        ret = os.system(self.command(" add queue --durable " + qname))
        self.assertEqual(ret, 0)
        queues = qmf.getObjects(_class="queue")
        found = False
        for queue in queues:
            if queue.name == qname:
                self.assertEqual(queue.durable, True)
                found = True
        self.assertEqual(found, True)

        ret = os.system(self.command(" del queue " + qname))
        self.assertEqual(ret, 0)
        queues = qmf.getObjects(_class="queue")
        found = False
        for queue in queues:
            if queue.name == qname:
                found = True
        self.assertEqual(found, False)

    def test_qpid_route(self):
        self.startQmf();
        qmf = self.qmf

        command = cli_dir() + "/qpid-route dynamic add guest/guest@localhost:%d %s:%d amq.topic" %\
            (testrunner.port, remote_host(), remote_port())
        ret = os.system(command)
        self.assertEqual(ret, 0)

        links = qmf.getObjects(_class="link")
        found = False
        for link in links:
            if link.port == remote_port():
                found = True
        self.assertEqual(found, True)

    def getProperty(self, msg, name):
        for h in msg.headers:
            if hasattr(h, name): return getattr(h, name)
        return None            

    def getAppHeader(self, msg, name):
        headers = self.getProperty(msg, "application_headers")
        if headers:
            return headers[name]
        return None

    def command(self, arg = ""):
        return cli_dir() + "/qpid-config -a localhost:%d" % testrunner.port + " " + arg


if __name__ == '__main__':
    args = sys.argv[1:]
    #need to remove the extra options from args as test runner doesn't recognise them
    extract_args("--remote-port", args)
    extract_args("--remote-host", args)
    extract_args("--cli-dir", args)

    if add_module():
        #add module(s) to run to testrunners args
        args.append("cli_tests") 
    
    if not testrunner.run(args): sys.exit(1)
