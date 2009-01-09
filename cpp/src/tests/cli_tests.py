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

        command = cli_dir() + "/qpid-route dynamic add localhost:%d %s:%d amq.topic" %\
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
