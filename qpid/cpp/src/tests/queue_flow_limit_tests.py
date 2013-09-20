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
from qpid.messaging import Connection
from threading import Thread
from time import sleep, time
from os import environ, popen

class QueueFlowLimitTests(TestBase010):

    _timeout = 100

    def __getattr__(self, name):
        if name == "assertGreater":
            return lambda a, b: self.failUnless(a > b)
        else:
            raise AttributeError

    def _create_queue(self, name,
                     stop_count=None, resume_count=None,
                     stop_size=None, resume_size=None,
                     max_size=None, max_count=None):
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
        if (max_size is not None):
            args["qpid.max_size"] = max_size;
        if (max_count is not None):
            args["qpid.max_count"] = max_count;

        broker = self.qmf.getObjects(_class="broker")[0]
        rc = broker.create( "queue", name, args, True )
        self.assertEqual(rc.status, 0, rc)

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
                if (max_size is not None):
                    self.assertEqual(i.arguments.get("qpid.max_size"), max_size)
                if (max_count is not None):
                    self.assertEqual(i.arguments.get("qpid.max_count"), max_count)
                self.failIf(i.flowStopped)
                return i.getObjectId()
        self.fail("Unable to create queue '%s'" % name)
        return None


    def _delete_queue(self, name):
        """ Delete a named queue
        """
        broker = self.qmf.getObjects(_class="broker")[0]
        rc = broker.delete( "queue", name, {} )
        self.assertEqual(rc.status, 0, rc)


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

    def test_qpid_config_cmd(self):
        """ Test the qpid-config command's ability to configure a queue's flow
        control thresholds.
        """
        tool = environ.get("QPID_CONFIG_EXEC")
        if tool:
            command = tool + \
                " --broker=%s:%s " % (self.broker.host, self.broker.port) \
                + "add queue test01 --flow-stop-count=999" \
                + " --flow-resume-count=55 --flow-stop-size=5000000" \
                + " --flow-resume-size=100000"
            cmd = popen(command)
            rc = cmd.close()
            self.assertEqual(rc, None)

            # now verify the settings
            self.startQmf();
            qs = self.qmf.getObjects(_class="queue")
            for i in qs:
                if i.name == "test01":
                    self.assertEqual(i.arguments.get("qpid.flow_stop_count"), 999)
                    self.assertEqual(i.arguments.get("qpid.flow_resume_count"), 55)
                    self.assertEqual(i.arguments.get("qpid.flow_stop_size"), 5000000)
                    self.assertEqual(i.arguments.get("qpid.flow_resume_size"), 100000)
                    self.failIf(i.flowStopped)
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
        self.assertEqual(self.qmf.getObjects(_objectId=oid)[0].flowStoppedCount, 0)

        sndr1 = self._start_qpid_send("test-q", count=1213, content="XXX", capacity=50);
        sndr2 = self._start_qpid_send("test-q", count=797, content="Y", capacity=13);
        sndr3 = self._start_qpid_send("test-q", count=331, content="ZZZZZ", capacity=149);
        totalMsgs = 1213 + 797 + 331

        # wait until flow control is active
        deadline = time() + self._timeout
        while (not self.qmf.getObjects(_objectId=oid)[0].flowStopped) and \
                time() < deadline:
            pass
        self.failUnless(self.qmf.getObjects(_objectId=oid)[0].flowStopped)
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
        x = rcvr.readline()    # prints a line for each received msg
        while x:
            count += 1;
            x = rcvr.readline()

        sndr1.close();
        sndr2.close();
        sndr3.close();
        rcvr.close();

        self.assertEqual(count, totalMsgs)
        self.failIf(self.qmf.getObjects(_objectId=oid)[0].flowStopped)
        self.failUnless(self.qmf.getObjects(_objectId=oid)[0].flowStoppedCount)

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

        # wait until flow control is active
        deadline = time() + self._timeout
        while (not self.qmf.getObjects(_objectId=oid)[0].flowStopped) and \
                time() < deadline:
            pass
        self.failUnless(self.qmf.getObjects(_objectId=oid)[0].flowStopped)
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
        x = rcvr.readline()    # prints a line for each received msg
        while x:
            count += 1;
            x = rcvr.readline()

        sndr1.close();
        sndr2.close();
        sndr3.close();
        rcvr.close();

        self.assertEqual(count, totalMsgs)
        self.failIf(self.qmf.getObjects(_objectId=oid)[0].flowStopped)

        self._delete_queue("test-q")


    def verify_limit(self, testq):
        """ run a limit check against the testq object
        """

        testq.mgmt = self.qmf.getObjects(_objectId=testq.oid)[0]

        # fill up the queue, waiting until flow control is active
        sndr1 = self._start_qpid_send(testq.mgmt.name, count=testq.sendCount, content=testq.content)
        deadline = time() + self._timeout
        while (not testq.mgmt.flowStopped) and time() < deadline:
            testq.mgmt.update()

        self.failUnless(testq.verifyStopped())

        # now consume enough messages to drop below the flow resume point, and
        # verify flow control is released.
        rcvr = self._start_qpid_receive(testq.mgmt.name, count=testq.consumeCount)
        rcvr.readlines()    # prints a line for each received msg
        rcvr.close();

        # we should now be below the resume threshold
        self.failUnless(testq.verifyResumed())

        self._delete_queue(testq.mgmt.name)
        sndr1.close();


    def test_default_flow_count(self):
        """ Create a queue with count-based size limit, and verify the computed
        thresholds using the broker's default ratios.
        """
        class TestQ:
            def __init__(self, oid):
                # Use the broker-wide default flow thresholds of 80%/70% (see
                # run_queue_flow_limit_tests) to base the thresholds off the
                # queue's max_count configuration parameter
                # max_count == 1000 -> stop == 800, resume == 700
                self.oid = oid
                self.sendCount = 1000
                self.consumeCount = 301 # (send - resume) + 1 to reenable flow
                self.content = "X"
                self.mgmt = None
            def verifyStopped(self):
                self.mgmt.update()
                return self.mgmt.flowStopped and (self.mgmt.msgDepth > 800)
            def verifyResumed(self):
                self.mgmt.update()
                return (not self.mgmt.flowStopped) and (self.mgmt.msgDepth < 700)

        self.startQmf();
        oid = self._create_queue("test-X", max_count=1000)
        self.verify_limit(TestQ(oid))


    def test_default_flow_size(self):
        """ Create a queue with byte-based size limit, and verify the computed
        thresholds using the broker's default ratios.
        """
        class TestQ:
            def __init__(self, oid):
                # Use the broker-wide default flow thresholds of 80%/70% (see
                # run_queue_flow_limit_tests) to base the thresholds off the
                # queue's max_count configuration parameter
                # max_size == 10000 -> stop == 8000 bytes, resume == 7000 bytes
                self.oid = oid
                self.sendCount = 2000
                self.consumeCount = 601 # (send - resume) + 1 to reenable flow
                self.content = "XXXXX"  # 5 bytes per message sent.
                self.mgmt = None
            def verifyStopped(self):
                self.mgmt.update()
                return self.mgmt.flowStopped and (self.mgmt.byteDepth > 8000)
            def verifyResumed(self):
                self.mgmt.update()
                return (not self.mgmt.flowStopped) and (self.mgmt.byteDepth < 7000)

        self.startQmf();
        oid = self._create_queue("test-Y", max_size=10000)
        self.verify_limit(TestQ(oid))


    def test_blocked_queue_delete(self):
        """ Verify that blocked senders are unblocked when a queue that is flow
        controlled is deleted.
        """

        class BlockedSender(Thread):
            def __init__(self, tester, queue, count, capacity=10):
                self.tester = tester
                self.queue = queue
                self.count = count
                self.capacity = capacity
                Thread.__init__(self)
                self.done = False
                self.start()
            def run(self):
                # spawn qpid-send
                p = self.tester._start_qpid_send(self.queue,
                                                 self.count,
                                                 self.capacity)
                p.close()  # waits for qpid-send to complete
                self.done = True

        self.startQmf();
        oid = self._create_queue("kill-q", stop_size=10, resume_size=2)
        q = self.qmf.getObjects(_objectId=oid)[0]
        self.failIf(q.flowStopped)

        sender = BlockedSender(self, "kill-q", count=100)
        # wait for flow control
        deadline = time() + self._timeout
        while (not q.flowStopped) and time() < deadline:
            q.update()

        self.failUnless(q.flowStopped)
        self.failIf(sender.done)   # sender blocked

        self._delete_queue("kill-q")
        sender.join(5)
        self.failIf(sender.isAlive())
        self.failUnless(sender.done)




