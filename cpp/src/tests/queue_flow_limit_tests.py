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
from qpid.testlib import TestBase010
from qpid import datatypes, messaging
from qpid.messaging import Message, Empty
from threading import Thread, Lock
from logging import getLogger
from time import sleep
from subprocess import Popen, PIPE
from os import environ

class QueueFlowLimitTests(TestBase010):

    def _create_queue(self, name,
                     stop_count=None, resume_count=None,
                     stop_size=None, resume_size=None):
        """ Create a queue with the given flow settings via the queue.declare
        command.
        """
        args={}
        if (stop_count is not None):
            args["qpid.flow_stop_count"] = stop_count;
        if (resume_count is not None):
            args["qpid.flow_resume_count"] = resume_count;
        if (stop_size is not None):
            args["qpid.flow_stop_size"] = stop_size;
        if (resume_size is not None):
            args["qpid.flow_resume_size"] = resume_size;

        self.session.queue_declare(queue=name, arguments=args)

        qs = self.qmf.getObjects(_class="queue")
        for i in qs:
            if i.name == name:
                # verify flow settings
                if (stop_count is not None):
                    self.assertEqual(i.arguments.get("qpid.flow_stop_count"), stop_count)
                if (resume_count is not None):
                    self.assertEqual(i.arguments.get("qpid.flow_resume_count"), resume_count)
                if (stop_size is not None):
                    self.assertEqual(i.arguments.get("qpid.flow_stop_size"), stop_size)
                if (resume_size is not None):
                    self.assertEqual(i.arguments.get("qpid.flow_resume_size"), resume_size)
                self.assertFalse(i.flowStopped)
                return i.getObjectId()
        self.fail("Unable to create queue '%s'" % name)
        return None


    def _delete_queue(self, name):
        """ Delete a named queue
        """
        self.session.queue_delete(queue=name)


    def _start_qpid_send(self, queue, count, content="X", capacity=10):
        """ Use the qpid-send client to generate traffic to a queue.
        """
        command = ["qpid-send",
                   "-b", "%s:%s" % (self.broker.host, self.broker.port),
                   "-a", str(queue),
                   "--messages", str(count),
                   "--content-string", str(content),
                   "--capacity", str(capacity)
                   ]

        return Popen(command, stdout=PIPE)

    def _start_qpid_receive(self, queue, count, timeout=5):
        """ Use the qpid-receive client to consume from a queue.
        Note well: prints one line of text to stdout for each consumed msg.
        """
        command = ["qpid-receive",
                   "-b", "%s:%s" % (self.broker.host, self.broker.port),
                   "-a", str(queue),
                   "--messages", str(count),
                   "--timeout", str(timeout),
                   "--print-content", "yes"
                   ]
        return Popen(command, stdout=PIPE)



    def test_qpid_config_cmd(self):
        """ Test the qpid-config command's ability to configure a queue's flow
        control thresholds.
        """
        tool = environ.get("QPID_CONFIG_EXEC")
        if tool:
            command = [tool,
                       "--broker-addr=%s:%s" % (self.broker.host, self.broker.port),
                       "add", "queue", "test01",
                       "--flow-stop-count=999",
                       "--flow-resume-count=55",
                       "--flow-stop-size=5000000",
                       "--flow-resume-size=100000"]
            #cmd = Popen(command, stdout=PIPE)
            cmd = Popen(command)
            cmd.wait()
            self.assertEqual(cmd.returncode, 0)

            # now verify the settings
            self.startQmf();
            qs = self.qmf.getObjects(_class="queue")
            for i in qs:
                if i.name == "test01":
                    self.assertEqual(i.arguments.get("qpid.flow_stop_count"), 999)
                    self.assertEqual(i.arguments.get("qpid.flow_resume_count"), 55)
                    self.assertEqual(i.arguments.get("qpid.flow_stop_size"), 5000000)
                    self.assertEqual(i.arguments.get("qpid.flow_resume_size"), 100000)
                    self.assertFalse(i.flowStopped)
                    break;
            self.assertEqual(i.name, "test01")
            self._delete_queue("test01")


    def test_flow_count(self):
        """ Create a queue with count-based flow limit.  Spawn several
        producers which will exceed the limit.  Verify limit exceeded.  Consume
        all messages.  Verify flow control released.
        """
        self.startQmf();
        oid = self._create_queue("test-q", stop_count=373, resume_count=229)

        sndr1 = self._start_qpid_send("test-q", count=1213, content="XXX", capacity=50);
        sndr2 = self._start_qpid_send("test-q", count=797, content="Y", capacity=13);
        sndr3 = self._start_qpid_send("test-q", count=331, content="ZZZZZ", capacity=149);
        totalMsgs = 1213 + 797 + 331
        

        # wait until flow control is active
        count = 0
        while self.qmf.getObjects(_objectId=oid)[0].flowStopped == False and \
                count < 10:
            sleep(1);
            count += 1;
        self.assertTrue(self.qmf.getObjects(_objectId=oid)[0].flowStopped)
        depth = self.qmf.getObjects(_objectId=oid)[0].msgDepth
        self.assertGreater(depth, 373)

        # now wait until the enqueues stop happening - ensure that
        # not all msgs have been sent (senders are blocked)
        sleep(1)
        newDepth = self.qmf.getObjects(_objectId=oid)[0].msgDepth
        while depth != newDepth:
            depth = newDepth;
            sleep(1)
            newDepth = self.qmf.getObjects(_objectId=oid)[0].msgDepth
        self.assertGreater(totalMsgs, depth)

        # drain the queue
        rcvr = self._start_qpid_receive("test-q",
                                        count=totalMsgs)
        count = 0;
        x = rcvr.stdout.readline()    # prints a line for each received msg
        while x:
            count += 1;
            x = rcvr.stdout.readline()

        sndr1.wait();
        sndr2.wait();
        sndr3.wait();
        rcvr.wait();

        self.assertEqual(count, totalMsgs)
        self.assertFalse(self.qmf.getObjects(_objectId=oid)[0].flowStopped)

        self._delete_queue("test-q")


    def test_flow_size(self):
        """ Create a queue with size-based flow limit.  Spawn several
        producers which will exceed the limit.  Verify limit exceeded.  Consume
        all messages.  Verify flow control released.
        """
        self.startQmf();
        oid = self._create_queue("test-q", stop_size=351133, resume_size=251143)

        sndr1 = self._start_qpid_send("test-q", count=1699, content="X"*439, capacity=53);
        sndr2 = self._start_qpid_send("test-q", count=1129, content="Y"*631, capacity=13);
        sndr3 = self._start_qpid_send("test-q", count=881, content="Z"*823, capacity=149);
        totalMsgs = 1699 + 1129 + 881
        totalBytes = 439 + 631 + 823

        # wait until flow control is active
        count = 0
        while self.qmf.getObjects(_objectId=oid)[0].flowStopped == False and \
                count < 10:
            sleep(1);
            count += 1;
        self.assertTrue(self.qmf.getObjects(_objectId=oid)[0].flowStopped)
        self.assertGreater(self.qmf.getObjects(_objectId=oid)[0].byteDepth, 351133)

        # now wait until the enqueues stop happening - ensure that
        # not all msgs have been sent (senders are blocked)
        depth = self.qmf.getObjects(_objectId=oid)[0].msgDepth
        sleep(1)
        newDepth = self.qmf.getObjects(_objectId=oid)[0].msgDepth
        while depth != newDepth:
            depth = newDepth;
            sleep(1)
            newDepth = self.qmf.getObjects(_objectId=oid)[0].msgDepth
        self.assertGreater(totalMsgs, depth)

        # drain the queue
        rcvr = self._start_qpid_receive("test-q",
                                        count=totalMsgs)
        count = 0;
        x = rcvr.stdout.readline()    # prints a line for each received msg
        while x:
            count += 1;
            x = rcvr.stdout.readline()

        sndr1.wait();
        sndr2.wait();
        sndr3.wait();
        rcvr.wait();

        self.assertEqual(count, totalMsgs)
        self.assertFalse(self.qmf.getObjects(_objectId=oid)[0].flowStopped)

        self._delete_queue("test-q")




