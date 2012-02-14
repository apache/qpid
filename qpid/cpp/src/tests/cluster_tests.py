#!/usr/bin/env python

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

import os, signal, sys, time, imp, re, subprocess, glob, random, logging
import cluster_test_logs
from qpid import datatypes, messaging
from brokertest import *
from qpid.harness import Skipped
from qpid.messaging import Message, Empty, Disposition, REJECTED, util
from threading import Thread, Lock, Condition
from logging import getLogger
from itertools import chain
from tempfile import NamedTemporaryFile

log = getLogger("qpid.cluster_tests")

# Note: brokers that shut themselves down due to critical error during
# normal operation will still have an exit code of 0. Brokers that
# shut down because of an error found during initialize will exit with
# a non-0 code. Hence the apparently inconsistent use of EXPECT_EXIT_OK
# and EXPECT_EXIT_FAIL in some of the tests below.

# TODO aconway 2010-03-11: resolve this - ideally any exit due to an error
# should give non-0 exit status.

# Import scripts as modules
qpid_cluster=import_script(checkenv("QPID_CLUSTER_EXEC"))

def readfile(filename):
    """Returns te content of file named filename as a string"""
    f = file(filename)
    try: return f.read()
    finally: f.close()

class ShortTests(BrokerTest):
    """Short cluster functionality tests."""

    def test_message_replication(self):
        """Test basic cluster message replication."""
        # Start a cluster, send some messages to member 0.
        cluster = self.cluster(2)
        s0 = cluster[0].connect().session()
        s0.sender("q; {create:always}").send(Message("x"))
        s0.sender("q; {create:always}").send(Message("y"))
        s0.connection.close()

        # Verify messages available on member 1.
        s1 = cluster[1].connect().session()
        m = s1.receiver("q", capacity=1).fetch(timeout=1)
        s1.acknowledge()
        self.assertEqual("x", m.content)
        s1.connection.close()

        # Start member 2 and verify messages available.
        s2 = cluster.start().connect().session()
        m = s2.receiver("q", capacity=1).fetch(timeout=1)
        s2.acknowledge()
        self.assertEqual("y", m.content)
        s2.connection.close()

    def test_store_direct_update_match(self):
        """Verify that brokers stores an identical message whether they receive it
        direct from clients or during an update, no header or other differences"""
        cluster = self.cluster(0, args=["--load-module", self.test_store_lib])
        cluster.start(args=["--test-store-dump", "direct.dump"])
        # Try messages with various headers
        cluster[0].send_message("q", Message(durable=True, content="foobar",
                                             subject="subject",
                                             reply_to="reply_to",
                                             properties={"n":10}))
        # Try messages of different sizes
        for size in range(0,10000,100):
            cluster[0].send_message("q", Message(content="x"*size, durable=True))
        # Try sending via named exchange
        c = cluster[0].connect_old()
        s = c.session(str(qpid.datatypes.uuid4()))
        s.exchange_bind(exchange="amq.direct", binding_key="foo", queue="q")
        props = s.delivery_properties(routing_key="foo", delivery_mode=2)
        s.message_transfer(
            destination="amq.direct",
            message=qpid.datatypes.Message(props, "content"))

        # Try message with TTL and differnet headers/properties
        cluster[0].send_message("q", Message(durable=True, ttl=100000))
        cluster[0].send_message("q", Message(durable=True, properties={}, ttl=100000))
        cluster[0].send_message("q", Message(durable=True, properties={"x":10}, ttl=100000))

        # Now update a new member and compare their dumps.
        cluster.start(args=["--test-store-dump", "updatee.dump"])
        assert readfile("direct.dump") == readfile("updatee.dump")

        os.remove("direct.dump")
        os.remove("updatee.dump")

    def test_sasl(self):
        """Test SASL authentication and encryption in a cluster"""
        sasl_config=os.path.join(self.rootdir, "sasl_config")
        acl=os.path.join(os.getcwd(), "policy.acl")
        aclf=file(acl,"w")
        # Must allow cluster-user (zag) access to credentials exchange.
        aclf.write("""
acl allow zag@QPID publish exchange name=qpid.cluster-credentials
acl allow zig@QPID all all
acl deny all all
""")
        aclf.close()
        cluster = self.cluster(1, args=["--auth", "yes",
                                        "--sasl-config", sasl_config,
                                        "--load-module", os.getenv("ACL_LIB"),
                                        "--acl-file", acl,
                                        "--cluster-username=zag",
                                        "--cluster-password=zag",
                                        "--cluster-mechanism=PLAIN"
                                        ])

        # Valid user/password, ensure queue is created.
        c = cluster[0].connect(username="zig", password="zig")
        c.session().sender("ziggy;{create:always,node:{x-declare:{exclusive:true}}}")
        c.close()
        cluster.start()                 # Start second node.

        # Check queue is created on second node.
        c = cluster[1].connect(username="zig", password="zig")
        c.session().receiver("ziggy;{assert:always}")
        c.close()
        for b in cluster: b.ready()     # Make sure all brokers still running.

        # Valid user, bad password
        try:
            cluster[0].connect(username="zig", password="foo").close()
            self.fail("Expected exception")
        except messaging.exceptions.ConnectionError: pass
        for b in cluster: b.ready()     # Make sure all brokers still running.

        # Bad user ID
        try:
            cluster[0].connect(username="foo", password="bar").close()
            self.fail("Expected exception")
        except messaging.exceptions.ConnectionError: pass
        for b in cluster: b.ready()     # Make sure all brokers still running.

        # Action disallowed by ACL
        c = cluster[0].connect(username="zag", password="zag")
        try:
            s = c.session()
            s.sender("zaggy;{create:always}")
            s.close()
            self.fail("Expected exception")
        except messaging.exceptions.UnauthorizedAccess: pass
        # make sure the queue was not created at the other node.
        c = cluster[1].connect(username="zig", password="zig")
        try:
            s = c.session()
            s.sender("zaggy;{assert:always}")
            s.close()
            self.fail("Expected exception")
        except messaging.exceptions.NotFound: pass

    def test_sasl_join_good(self):
        """Verify SASL authentication between brokers when joining a cluster."""
        sasl_config=os.path.join(self.rootdir, "sasl_config")
        # Test with a valid username/password
        cluster = self.cluster(1, args=["--auth", "yes",
                                        "--sasl-config", sasl_config,
                                        "--cluster-username=zig",
                                        "--cluster-password=zig",
                                        "--cluster-mechanism=PLAIN"
                                        ])
        cluster.start()
        c = cluster[1].connect(username="zag", password="zag", mechanism="PLAIN")

    def test_sasl_join_bad_password(self):
        # Test with an invalid password
        cluster = self.cluster(1, args=["--auth", "yes",
                                        "--sasl-config", os.path.join(self.rootdir, "sasl_config"),
                                        "--cluster-username=zig",
                                        "--cluster-password=bad",
                                        "--cluster-mechanism=PLAIN"
                                        ])
        cluster.start(wait=False, expect=EXPECT_EXIT_FAIL)
        assert cluster[1].log_contains("critical Unexpected error: connection-forced: Authentication failed")

    def test_sasl_join_wrong_user(self):
        # Test with a valid user that is not the cluster user.
        cluster = self.cluster(0, args=["--auth", "yes",
                                        "--sasl-config", os.path.join(self.rootdir, "sasl_config")])
        cluster.start(args=["--cluster-username=zig",
                            "--cluster-password=zig",
                            "--cluster-mechanism=PLAIN"
                            ])

        cluster.start(wait=False, expect=EXPECT_EXIT_FAIL,
                      args=["--cluster-username=zag",
                            "--cluster-password=zag",
                            "--cluster-mechanism=PLAIN"
                            ])
        assert cluster[1].log_contains("critical Unexpected error: unauthorized-access: unauthorized-access: Unauthorized user zag@QPID for qpid.cluster-credentials, should be zig")

    def test_user_id_update(self):
        """Ensure that user-id of an open session is updated to new cluster members"""
        sasl_config=os.path.join(self.rootdir, "sasl_config")
        cluster = self.cluster(1, args=["--auth", "yes", "--sasl-config", sasl_config,
                                        "--cluster-mechanism=ANONYMOUS"])
        c = cluster[0].connect(username="zig", password="zig")
        s = c.session().sender("q;{create:always}")
        s.send(Message("x", user_id="zig")) # Message sent before start new broker
        cluster.start()
        s.send(Message("y", user_id="zig")) # Messsage sent after start of new broker
        # Verify brokers are healthy and messages are on the queue.
        self.assertEqual("x", cluster[0].get_message("q").content)
        self.assertEqual("y", cluster[1].get_message("q").content)

    def test_link_events(self):
        """Regression test for https://bugzilla.redhat.com/show_bug.cgi?id=611543"""
        args = ["--mgmt-pub-interval", 1] # Publish management information every second.
        broker1 = self.cluster(1, args)[0]
        broker2 = self.cluster(1, args)[0]
        qp = self.popen(["qpid-printevents", broker1.host_port()], EXPECT_RUNNING)
        qr = self.popen(["qpid-route", "route", "add",
                         broker1.host_port(), broker2.host_port(),
                         "amq.fanout", "key"
                         ], EXPECT_EXIT_OK)
        # Look for link event in printevents output.
        retry(lambda: find_in_file("brokerLinkUp", qp.outfile("out")))
        broker1.ready()
        broker2.ready()

    def test_queue_cleaner(self):
        """ Regression test to ensure that cleanup of expired messages works correctly """
        cluster = self.cluster(2, args=["--queue-purge-interval", 3])

        s0 = cluster[0].connect().session()
        sender = s0.sender("my-lvq; {create: always, node:{x-declare:{arguments:{'qpid.last_value_queue':1}}}}")
        #send 10 messages that will all expire and be cleaned up
        for i in range(1, 10):
            msg = Message("message-%s" % i)
            msg.properties["qpid.LVQ_key"] = "a"
            msg.ttl = 0.1
            sender.send(msg)
        #wait for queue cleaner to run
        time.sleep(3)

        #test all is ok by sending and receiving a message
        msg = Message("non-expiring")
        msg.properties["qpid.LVQ_key"] = "b"
        sender.send(msg)
        s0.connection.close()
        s1 = cluster[1].connect().session()
        m = s1.receiver("my-lvq", capacity=1).fetch(timeout=1)
        s1.acknowledge()
        self.assertEqual("non-expiring", m.content)
        s1.connection.close()

        for b in cluster: b.ready()     # Make sure all brokers still running.


    def test_amqfailover_visible(self):
        """Verify that the amq.failover exchange can be seen by
        QMF-based tools - regression test for BZ615300."""
        broker1 = self.cluster(1)[0]
        broker2 = self.cluster(1)[0]
        qs = subprocess.Popen(["qpid-stat", "-e", broker1.host_port()],  stdout=subprocess.PIPE)
        out = qs.communicate()[0]
        assert out.find("amq.failover") > 0

    def evaluate_address(self, session, address):
        """Create a receiver just to evaluate an address for its side effects"""
        r = session.receiver(address)
        r.close()

    def test_expire_fanout(self):
        """Regression test for QPID-2874: Clustered broker crashes in assertion in
        cluster/ExpiryPolicy.cpp.
        Caused by a fan-out message being updated as separate messages"""
        cluster = self.cluster(1)
        session0 = cluster[0].connect().session()
        # Create 2 queues bound to fanout exchange.
        self.evaluate_address(session0, "q1;{create:always,node:{x-bindings:[{exchange:'amq.fanout',queue:q1}]}}")
        self.evaluate_address(session0, "q2;{create:always,node:{x-bindings:[{exchange:'amq.fanout',queue:q2}]}}")
        queues = ["q1", "q2"]
        # Send a fanout message with a long timeout
        s = session0.sender("amq.fanout")
        s.send(Message("foo", ttl=100), sync=False)
        # Start a new member, check the messages
        cluster.start()
        session1 = cluster[1].connect().session()
        for q in queues: self.assert_browse(session1, "q1", ["foo"])

    def test_route_update(self):
        """Regression test for https://issues.apache.org/jira/browse/QPID-2982
        Links and bridges associated with routes were not replicated on update.
        This meant extra management objects and caused an exit if a management
        client was attached.
        """
        args=["--mgmt-pub-interval=1","--log-enable=trace+:management"]
        # First broker will be killed.
        cluster0 = self.cluster(1, args=args)
        cluster1 = self.cluster(1, args=args)
        assert 0 == subprocess.call(
            ["qpid-route", "route", "add", cluster0[0].host_port(),
             cluster1[0].host_port(), "dummy-exchange", "dummy-key", "-d"])
        cluster0.start()

        # Wait for qpid-tool:list on cluster0[0] to generate expected output.
        pattern = re.compile("org.apache.qpid.broker.*link")
        qpid_tool = subprocess.Popen(["qpid-tool", cluster0[0].host_port()],
                                     stdin=subprocess.PIPE, stdout=subprocess.PIPE)
        class Scanner(Thread):
            def __init__(self): self.found = False; Thread.__init__(self)
            def run(self):
                for l in qpid_tool.stdout:
                    if pattern.search(l): self.found = True; return
        scanner = Scanner()
        scanner.start()
        start = time.time()
        try:
            # Wait up to 5 second timeout for scanner to find expected output
            while not scanner.found and time.time() < start + 5:
                qpid_tool.stdin.write("list\n") # Ask qpid-tool to list
                for b in cluster0: b.ready() # Raise if any brokers are down
        finally:
            qpid_tool.stdin.write("quit\n")
            qpid_tool.wait()
            scanner.join()
        assert scanner.found
        # Regression test for https://issues.apache.org/jira/browse/QPID-3235
        # Inconsistent stats when changing elder.

        # Force a change of elder
        cluster0.start()
        cluster0[0].expect=EXPECT_EXIT_FAIL # About to die.
        cluster0[0].kill()
        time.sleep(2) # Allow a management interval to pass.
        # Verify logs are consistent
        cluster_test_logs.verify_logs()

    def test_redelivered(self):
        """Verify that redelivered flag is set correctly on replayed messages"""
        cluster = self.cluster(2, expect=EXPECT_EXIT_FAIL)
        url = "amqp:tcp:%s,tcp:%s" % (cluster[0].host_port(), cluster[1].host_port())
        queue = "my-queue"
        cluster[0].declare_queue(queue)
        self.sender = self.popen(
            ["qpid-send",
             "--broker", url,
             "--address", queue,
             "--sequence=true",
             "--send-eos=1",
             "--messages=100000",
             "--connection-options={%s}"%(Cluster.CONNECTION_OPTIONS)
             ])
        self.receiver = self.popen(
            ["qpid-receive",
             "--broker", url,
             "--address", queue,
             "--ignore-duplicates",
             "--check-redelivered",
             "--connection-options={%s}"%(Cluster.CONNECTION_OPTIONS),
             "--forever"
             ])
        time.sleep(1)#give sender enough time to have some messages to replay
        cluster[0].kill()
        self.sender.wait()
        self.receiver.wait()
        cluster[1].kill()

    class BlockedSend(Thread):
        """Send a message, send is expected to block.
        Verify that it does block (for a given timeout), then allow
        waiting till it unblocks when it is expected to do so."""
        def __init__(self, sender, msg):
            self.sender, self.msg = sender, msg
            self.blocked = True
            self.condition = Condition()
            self.timeout = 0.1    # Time to wait for expected results.
            Thread.__init__(self)
        def run(self):
            try:
                self.sender.send(self.msg, sync=True)
                self.condition.acquire()
                try:
                    self.blocked = False
                    self.condition.notify()
                finally: self.condition.release()
            except Exception,e: print "BlockedSend exception: %s"%e
        def start(self):
            Thread.start(self)
            time.sleep(self.timeout)
            assert self.blocked         # Expected to block
        def assert_blocked(self): assert self.blocked
        def wait(self):                 # Now expecting to unblock
            self.condition.acquire()
            try:
                while self.blocked:
                    self.condition.wait(self.timeout)
                    if self.blocked: raise Exception("Timed out waiting for send to unblock")
            finally: self.condition.release()
            self.join()

    def queue_flowlimit_test(self, brokers):
        """Verify that the queue's flowlimit configuration and state are
        correctly replicated.
        The brokers argument allows this test to run on single broker,
        cluster of 2 pre-startd brokers or cluster where second broker
        starts after queue is in flow control.
        """
        # configure a queue with a specific flow limit on first broker
        ssn0 = brokers.first().connect().session()
        s0 = ssn0.sender("flq; {create:always, node:{type:queue, x-declare:{arguments:{'qpid.flow_stop_count':5, 'qpid.flow_resume_count':3}}}}")
        brokers.first().startQmf()
        q1 = [q for q in brokers.first().qmf_session.getObjects(_class="queue") if q.name == "flq"][0]
        oid = q1.getObjectId()
        self.assertEqual(q1.name, "flq")
        self.assertEqual(q1.arguments, {u'qpid.flow_stop_count': 5L, u'qpid.flow_resume_count': 3L})
        assert not q1.flowStopped
        self.assertEqual(q1.flowStoppedCount, 0)

        # fill the queue on one broker until flow control is active
        for x in range(5): s0.send(Message(str(x)))
        sender = ShortTests.BlockedSend(s0, Message(str(6)))
        sender.start()                  # Tests that sender does block
        # Verify the broker queue goes into a flowStopped state
        deadline = time.time() + 1
        while not q1.flowStopped and time.time() < deadline: q1.update()
        assert q1.flowStopped
        self.assertEqual(q1.flowStoppedCount, 1)
        sender.assert_blocked()         # Still blocked

        # Now verify the  both brokers in cluster have same configuration
        brokers.second().startQmf()
        qs = brokers.second().qmf_session.getObjects(_objectId=oid)
        self.assertEqual(len(qs), 1)
        q2 = qs[0]
        self.assertEqual(q2.name, "flq")
        self.assertEqual(q2.arguments, {u'qpid.flow_stop_count': 5L, u'qpid.flow_resume_count': 3L})
        assert q2.flowStopped
        self.assertEqual(q2.flowStoppedCount, 1)

        # now drain the queue using a session to the other broker
        ssn1 = brokers.second().connect().session()
        r1 = ssn1.receiver("flq", capacity=6)
        for x in range(4):
            r1.fetch(timeout=0)
            ssn1.acknowledge()
        sender.wait()                   # Verify no longer blocked.

        # and re-verify state of queue on both brokers
        q1.update()
        assert not q1.flowStopped
        q2.update()
        assert not q2.flowStopped

        ssn0.connection.close()
        ssn1.connection.close()
        cluster_test_logs.verify_logs()

    def test_queue_flowlimit(self):
        """Test flow limits on a standalone broker"""
        broker = self.broker()
        class Brokers:
            def first(self): return broker
            def second(self): return broker
        self.queue_flowlimit_test(Brokers())

    def test_queue_flowlimit_cluster(self):
        cluster = self.cluster(2)
        class Brokers:
            def first(self): return cluster[0]
            def second(self): return cluster[1]
        self.queue_flowlimit_test(Brokers())

    def test_queue_flowlimit_cluster_join(self):
        cluster = self.cluster(1)
        class Brokers:
            def first(self): return cluster[0]
            def second(self):
                if len(cluster) == 1: cluster.start()
                return cluster[1]
        self.queue_flowlimit_test(Brokers())

    def test_queue_flowlimit_replicate(self):
        """ Verify that a queue which is in flow control BUT has drained BELOW
        the flow control 'stop' threshold, is correctly replicated when a new
        broker is added to the cluster.
        """

        class AsyncSender(Thread):
            """Send a fixed number of msgs from a sender in a separate thread
            so it may block without blocking the test.
            """
            def __init__(self, broker, address, count=1, size=4):
                Thread.__init__(self)
                self.daemon = True
                self.broker = broker
                self.queue = address
                self.count = count
                self.size = size
                self.done = False

            def run(self):
                self.sender = subprocess.Popen(["qpid-send",
                                                "--capacity=1",
                                                "--content-size=%s" % self.size,
                                                "--messages=%s" % self.count,
                                                "--failover-updates",
                                                "--connection-options={%s}"%(Cluster.CONNECTION_OPTIONS),
                                                "--address=%s" % self.queue,
                                                "--broker=%s" % self.broker.host_port()])
                self.sender.wait()
                self.done = True

        cluster = self.cluster(2)
        # create a queue with rather draconian flow control settings
        ssn0 = cluster[0].connect().session()
        s0 = ssn0.sender("flq; {create:always, node:{type:queue, x-declare:{arguments:{'qpid.flow_stop_count':100, 'qpid.flow_resume_count':20}}}}")

        # fire off the sending thread to broker[0], and wait until the queue
        # hits flow control on broker[1]
        sender = AsyncSender(cluster[0], "flq", count=110);
        sender.start();

        cluster[1].startQmf()
        q_obj = [q for q in cluster[1].qmf_session.getObjects(_class="queue") if q.name == "flq"][0]
        deadline = time.time() + 10
        while not q_obj.flowStopped and time.time() < deadline:
            q_obj.update()
        assert q_obj.flowStopped
        assert not sender.done
        assert q_obj.msgDepth < 110

        # Now drain enough messages on broker[1] to drop below the flow stop
        # threshold, but not relieve flow control...
        receiver = subprocess.Popen(["qpid-receive",
                                     "--messages=15",
                                     "--timeout=1",
                                     "--print-content=no",
                                     "--failover-updates",
                                     "--connection-options={%s}"%(Cluster.CONNECTION_OPTIONS),
                                     "--ack-frequency=1",
                                     "--address=flq",
                                     "--broker=%s" % cluster[1].host_port()])
        receiver.wait()
        q_obj.update()
        assert q_obj.flowStopped
        assert not sender.done
        current_depth = q_obj.msgDepth

        # add a new broker to the cluster, and verify that the queue is in flow
        # control on that broker
        cluster.start()
        cluster[2].startQmf()
        q_obj = [q for q in cluster[2].qmf_session.getObjects(_class="queue") if q.name == "flq"][0]
        assert q_obj.flowStopped
        assert q_obj.msgDepth == current_depth

        # now drain the queue on broker[2], and verify that the sender becomes
        # unblocked
        receiver = subprocess.Popen(["qpid-receive",
                                     "--messages=95",
                                     "--timeout=1",
                                     "--print-content=no",
                                     "--failover-updates",
                                     "--connection-options={%s}"%(Cluster.CONNECTION_OPTIONS),
                                     "--ack-frequency=1",
                                     "--address=flq",
                                     "--broker=%s" % cluster[2].host_port()])
        receiver.wait()
        q_obj.update()
        assert not q_obj.flowStopped
        self.assertEqual(q_obj.msgDepth, 0)

        # verify that the sender has become unblocked
        sender.join(timeout=5)
        assert not sender.isAlive()
        assert sender.done

    def test_blocked_queue_delete(self):
        """Verify that producers which are blocked on a queue due to flow
        control are unblocked when that queue is deleted.
        """

        cluster = self.cluster(2)
        cluster[0].startQmf()
        cluster[1].startQmf()

        # configure a queue with a specific flow limit on first broker
        ssn0 = cluster[0].connect().session()
        s0 = ssn0.sender("flq; {create:always, node:{type:queue, x-declare:{arguments:{'qpid.flow_stop_count':5, 'qpid.flow_resume_count':3}}}}")
        q1 = [q for q in cluster[0].qmf_session.getObjects(_class="queue") if q.name == "flq"][0]
        oid = q1.getObjectId()
        self.assertEqual(q1.name, "flq")
        self.assertEqual(q1.arguments, {u'qpid.flow_stop_count': 5L, u'qpid.flow_resume_count': 3L})
        assert not q1.flowStopped
        self.assertEqual(q1.flowStoppedCount, 0)

        # fill the queue on one broker until flow control is active
        for x in range(5): s0.send(Message(str(x)))
        sender = ShortTests.BlockedSend(s0, Message(str(6)))
        sender.start()                  # Tests that sender does block
        # Verify the broker queue goes into a flowStopped state
        deadline = time.time() + 1
        while not q1.flowStopped and time.time() < deadline: q1.update()
        assert q1.flowStopped
        self.assertEqual(q1.flowStoppedCount, 1)
        sender.assert_blocked()         # Still blocked

        # Now verify the  both brokers in cluster have same configuration
        qs = cluster[1].qmf_session.getObjects(_objectId=oid)
        self.assertEqual(len(qs), 1)
        q2 = qs[0]
        self.assertEqual(q2.name, "flq")
        self.assertEqual(q2.arguments, {u'qpid.flow_stop_count': 5L, u'qpid.flow_resume_count': 3L})
        assert q2.flowStopped
        self.assertEqual(q2.flowStoppedCount, 1)

        # now delete the blocked queue from other broker
        ssn1 = cluster[1].connect().session()
        self.evaluate_address(ssn1, "flq;{delete:always}")
        sender.wait()                   # Verify no longer blocked.

        ssn0.connection.close()
        ssn1.connection.close()
        cluster_test_logs.verify_logs()


    def test_alternate_exchange_update(self):
        """Verify that alternate-exchange on exchanges and queues is propagated to new members of a cluster. """
        cluster = self.cluster(1)
        s0 = cluster[0].connect().session()
        # create alt queue bound to amq.fanout exchange, will be destination for alternate exchanges
        self.evaluate_address(s0, "alt;{create:always,node:{x-bindings:[{exchange:'amq.fanout',queue:alt}]}}")
        # create direct exchange ex with alternate-exchange amq.fanout and no queues bound
        self.evaluate_address(s0, "ex;{create:always,node:{type:topic, x-declare:{type:'direct', alternate-exchange:'amq.fanout'}}}")
        # create queue q with alternate-exchange amq.fanout
        self.evaluate_address(s0, "q;{create:always,node:{type:queue, x-declare:{alternate-exchange:'amq.fanout'}}}")

        def verify(broker):
            s = broker.connect().session()
            # Verify unmatched message goes to ex's alternate.
            s.sender("ex").send("foo")
            self.assertEqual("foo", s.receiver("alt").fetch(timeout=0).content)
            # Verify rejected message goes to q's alternate.
            s.sender("q").send("bar")
            msg = s.receiver("q").fetch(timeout=0)
            self.assertEqual("bar", msg.content)
            s.acknowledge(msg, Disposition(REJECTED)) # Reject the message
            self.assertEqual("bar", s.receiver("alt").fetch(timeout=0).content)

        verify(cluster[0])
        cluster.start()
        verify(cluster[1])

    def test_binding_order(self):
        """Regression test for binding order inconsistency in cluster"""
        cluster = self.cluster(1)
        c0 = cluster[0].connect()
        s0 = c0.session()
        # Declare multiple queues bound to same key on amq.topic
        def declare(q,max=0):
            if max: declare = 'x-declare:{arguments:{"qpid.max_count":%d, "qpid.flow_stop_count":0}}'%max
            else: declare = 'x-declare:{}'
            bind='x-bindings:[{queue:%s,key:key,exchange:"amq.topic"}]'%(q)
            s0.sender("%s;{create:always,node:{%s,%s}}" % (q,declare,bind))
        declare('d',max=4)              # Only one with a limit
        for q in ['c', 'b','a']: declare(q)
        # Add a cluster member, send enough messages to exceed the max count
        cluster.start()
        try:
            s = s0.sender('amq.topic/key')
            for m in xrange(1,6): s.send(Message(str(m)))
            self.fail("Expected capacity exceeded exception")
        except messaging.exceptions.TargetCapacityExceeded: pass
        c1 = cluster[1].connect()
        s1 = c1.session()
        s0 = c0.session()        # Old session s0 is broken by exception.
        # Verify queue contents are consistent.
        for q in ['a','b','c','d']:
            self.assertEqual(self.browse(s0, q), self.browse(s1, q))
        # Verify queue contents are "best effort"
        for q in ['a','b','c']: self.assert_browse(s1,q,[str(n) for n in xrange(1,6)])
        self.assert_browse(s1,'d',[str(n) for n in xrange(1,5)])

    def test_deleted_exchange(self):
        """QPID-3215: cached exchange reference can cause cluster inconsistencies
        if exchange is deleted/recreated
        Verify stand-alone case
        """
        cluster = self.cluster()
        # Verify we do not route message via an exchange that has been destroyed.
        cluster.start()
        s0 = cluster[0].connect().session()
        self.evaluate_address(s0, "ex;{create:always,node:{type:topic}}")
        self.evaluate_address(s0, "q;{create:always,node:{x-bindings:[{exchange:'ex',queue:q,key:foo}]}}")
        send0 = s0.sender("ex/foo")
        send0.send("foo")
        self.assert_browse(s0, "q", ["foo"])
        self.evaluate_address(s0, "ex;{delete:always}")
        try:
            send0.send("bar")     # Should fail, exchange is deleted.
            self.fail("Expected not-found exception")
        except qpid.messaging.NotFound: pass
        self.assert_browse(cluster[0].connect().session(), "q", ["foo"])

    def test_deleted_exchange_inconsistent(self):
        """QPID-3215: cached exchange reference can cause cluster inconsistencies
        if exchange is deleted/recreated

        Verify cluster inconsistency.
        """
        cluster = self.cluster()
        cluster.start()
        s0 = cluster[0].connect().session()
        self.evaluate_address(s0, "ex;{create:always,node:{type:topic}}")
        self.evaluate_address(s0, "q;{create:always,node:{x-bindings:[{exchange:'ex',queue:q,key:foo}]}}")
        send0 = s0.sender("ex/foo")
        send0.send("foo")
        self.assert_browse(s0, "q", ["foo"])

        cluster.start()
        s1 = cluster[1].connect().session()
        self.evaluate_address(s0, "ex;{delete:always}")
        try:
            send0.send("bar")
            self.fail("Expected not-found exception")
        except qpid.messaging.NotFound: pass

        self.assert_browse(s1, "q", ["foo"])


    def test_ttl_consistent(self):
        """Ensure we don't get inconsistent errors with message that have TTL very close together"""
        messages = [ Message(str(i), ttl=i/1000.0) for i in xrange(0,1000)]
        messages.append(Message("x"))
        cluster = self.cluster(2)
        sender = cluster[0].connect().session().sender("q;{create:always}")

        def fetch(b):
            receiver = b.connect().session().receiver("q;{create:always}")
            while receiver.fetch().content != "x": pass

        for m in messages: sender.send(m, sync=False)
        for m in messages: sender.send(m, sync=False)
        fetch(cluster[0])
        fetch(cluster[1])
        for m in messages: sender.send(m, sync=False)
        cluster.start()
        fetch(cluster[2])

# Some utility code for transaction tests
XA_RBROLLBACK = 1
XA_RBTIMEOUT = 2
XA_OK = 0
dtx_branch_counter = 0

class DtxStatusException(Exception):
    def __init__(self, expect, actual):
        self.expect = expect
        self.actual = actual

    def str(self):
        return "DtxStatusException(expect=%s, actual=%s)"%(self.expect, self.actual)

class DtxTestFixture:
    """Bundle together some common requirements for dtx tests."""
    def __init__(self, test, broker, name, exclusive=False):
        self.test = test
        self.broker = broker
        self.name = name
        # Use old API. DTX is not supported in messaging API.
        self.connection = broker.connect_old()
        self.session = self.connection.session(name, 1) # 1 second timeout
        self.queue = self.session.queue_declare(name, exclusive=exclusive)
        self.session.dtx_select()
        self.consumer = None

    def xid(self, id=None):
        if id is None: id = self.name
        return self.session.xid(format=0, global_id=id)

    def check_status(self, expect, actual):
        if expect != actual: raise DtxStatusException(expect, actual)

    def start(self, id=None, resume=False):
        self.check_status(XA_OK, self.session.dtx_start(xid=self.xid(id), resume=resume).status)

    def end(self, id=None, suspend=False):
        self.check_status(XA_OK, self.session.dtx_end(xid=self.xid(id), suspend=suspend).status)

    def prepare(self, id=None):
        self.check_status(XA_OK, self.session.dtx_prepare(xid=self.xid(id)).status)

    def commit(self, id=None, one_phase=True):
        self.check_status(
            XA_OK, self.session.dtx_commit(xid=self.xid(id), one_phase=one_phase).status)

    def rollback(self, id=None):
        self.check_status(XA_OK, self.session.dtx_rollback(xid=self.xid(id)).status)

    def set_timeout(self, timeout, id=None):
        self.session.dtx_set_timeout(xid=self.xid(id),timeout=timeout)

    def send(self, messages):
       for m in messages:
           dp=self.session.delivery_properties(routing_key=self.name)
           mp=self.session.message_properties()
           self.session.message_transfer(message=qpid.datatypes.Message(dp, mp, m))

    def accept(self):
        """Accept 1 message from queue"""
        consumer_tag="%s-consumer"%(self.name)
        self.session.message_subscribe(queue=self.name, destination=consumer_tag)
        self.session.message_flow(unit = self.session.credit_unit.message, value = 1, destination = consumer_tag)
        self.session.message_flow(unit = self.session.credit_unit.byte, value = 0xFFFFFFFFL, destination = consumer_tag)
        msg = self.session.incoming(consumer_tag).get(timeout=1)
        self.session.message_cancel(destination=consumer_tag)
        self.session.message_accept(qpid.datatypes.RangedSet(msg.id))
        return msg


    def verify(self, sessions, messages):
        for s in sessions:
            self.test.assert_browse(s, self.name, messages)

class DtxTests(BrokerTest):

    def test_dtx_update(self):
        """Verify that DTX transaction state is updated to a new broker.
        Start a collection of transactions, then add a new cluster member,
        then verify they commit/rollback correctly on the new broker."""

        # Note: multiple test have been bundled into one to avoid the need to start/stop
        # multiple brokers per test.

        cluster=self.cluster(1)
        sessions = [cluster[0].connect().session()] # For verify

        # Transaction that will be open when new member joins, then committed.
        t1 = DtxTestFixture(self, cluster[0], "t1")
        t1.start()
        t1.send(["1", "2"])
        t1.verify(sessions, [])          # Not visible outside of transaction

        # Transaction that will be open when  new member joins, then rolled back.
        t2 = DtxTestFixture(self, cluster[0], "t2")
        t2.start()
        t2.send(["1", "2"])

        # Transaction that will be prepared when new member joins, then committed.
        t3 = DtxTestFixture(self, cluster[0], "t3")
        t3.start()
        t3.send(["1", "2"])
        t3.end()
        t3.prepare()
        t1.verify(sessions, [])          # Not visible outside of transaction

        # Transaction that will be prepared when  new member joins, then rolled back.
        t4 = DtxTestFixture(self, cluster[0], "t4")
        t4.start()
        t4.send(["1", "2"])
        t4.end()
        t4.prepare()

        # Transaction using an exclusive queue
        t5 = DtxTestFixture(self, cluster[0], "t5", exclusive=True)
        t5.start()
        t5.send(["1", "2"])

        # Accept messages in a transaction before/after join then commit
        t6 = DtxTestFixture(self, cluster[0], "t6")
        t6.send(["a","b","c"])
        t6.start()
        self.assertEqual(t6.accept().body, "a");

        # Accept messages in a transaction before/after join then roll back
        t7 = DtxTestFixture(self, cluster[0], "t7")
        t7.send(["a","b","c"])
        t7.start()
        self.assertEqual(t7.accept().body, "a");

        # Ended, suspended transactions across join.
        t8 = DtxTestFixture(self, cluster[0], "t8")
        t8.start(id="1")
        t8.send(["x"])
        t8.end(id="1", suspend=True)
        t8.start(id="2")
        t8.send(["y"])
        t8.end(id="2")
        t8.start()
        t8.send("z")


        # Start new cluster member
        cluster.start()
        sessions.append(cluster[1].connect().session())

        # Commit t1
        t1.send(["3","4"])
        t1.verify(sessions, [])
        t1.end()
        t1.commit(one_phase=True)
        t1.verify(sessions, ["1","2","3","4"])

        # Rollback t2
        t2.send(["3","4"])
        t2.end()
        t2.rollback()
        t2.verify(sessions, [])

        # Commit t3
        t3.commit(one_phase=False)
        t3.verify(sessions, ["1","2"])

        # Rollback t4
        t4.rollback()
        t4.verify(sessions, [])

        # Commit t5
        t5.send(["3","4"])
        t5.verify(sessions, [])
        t5.end()
        t5.commit(one_phase=True)
        t5.verify(sessions, ["1","2","3","4"])

        # Commit t6
        self.assertEqual(t6.accept().body, "b");
        t6.verify(sessions, ["c"])
        t6.end()
        t6.commit(one_phase=True)
        t6.session.close()              # Make sure they're not requeued by the session.
        t6.verify(sessions, ["c"])

        # Rollback t7
        self.assertEqual(t7.accept().body, "b");
        t7.end()
        t7.rollback()
        t7.verify(sessions, ["a", "b", "c"])

        # Resume t8
        t8.end()
        t8.commit(one_phase=True)
        t8.start("1", resume=True)
        t8.end("1")
        t8.commit("1", one_phase=True)
        t8.commit("2", one_phase=True)
        t8.verify(sessions, ["z", "x","y"])


    def test_dtx_failover_rollback(self):
       """Kill a broker during a transaction, verify we roll back correctly"""
       cluster=self.cluster(1, expect=EXPECT_EXIT_FAIL)
       cluster.start(expect=EXPECT_RUNNING)

       # Test unprepared at crash
       t1 = DtxTestFixture(self, cluster[0], "t1")
       t1.send(["a"])                   # Not in transaction
       t1.start()
       t1.send(["b"])                   # In transaction

       # Test prepared at crash
       t2 = DtxTestFixture(self, cluster[0], "t2")
       t2.send(["a"])                   # Not in transaction
       t2.start()
       t2.send(["b"])                   # In transaction
       t2.end()
       t2.prepare()

       # Crash the broker
       cluster[0].kill()

       # Transactional changes should not appear
       s = cluster[1].connect().session();
       self.assert_browse(s, "t1", ["a"])
       self.assert_browse(s, "t2", ["a"])

    def test_dtx_timeout(self):
        """Verify that dtx timeout works"""
        cluster = self.cluster(1)
        t1 = DtxTestFixture(self, cluster[0], "t1")
        t1.start()
        t1.set_timeout(1)
        time.sleep(1.1)
        try:
            t1.end()
            self.fail("Expected rollback timeout.")
        except DtxStatusException, e:
            self.assertEqual(e.actual, XA_RBTIMEOUT)

class TxTests(BrokerTest):

    def test_tx_update(self):
        """Verify that transaction state is updated to a new broker"""

        def make_message(session, body=None, key=None, id=None):
            dp=session.delivery_properties(routing_key=key)
            mp=session.message_properties(correlation_id=id)
            return qpid.datatypes.Message(dp, mp, body)

        cluster=self.cluster(1)
        # Use old API. TX is not supported in messaging API.
        c = cluster[0].connect_old()
        s = c.session("tx-session", 1)
        s.queue_declare(queue="q")
        # Start transaction
        s.tx_select()
        s.message_transfer(message=make_message(s, "1", "q"))
        # Start new member mid-transaction
        cluster.start()
        # Do more work
        s.message_transfer(message=make_message(s, "2", "q"))
        # Commit the transaction and verify the results.
        s.tx_commit()
        for b in cluster: self.assert_browse(b.connect().session(), "q", ["1","2"])


class LongTests(BrokerTest):
    """Tests that can run for a long time if -DDURATION=<minutes> is set"""
    def duration(self):
        d = self.config.defines.get("DURATION")
        if d: return float(d)*60
        else: return 3                  # Default is to be quick

    def test_failover(self):
        """Test fail-over during continuous send-receive with errors"""

        # Original cluster will all be killed so expect exit with failure
        cluster = self.cluster(3, expect=EXPECT_EXIT_FAIL)
        for b in cluster: b.ready()     # Wait for brokers to be ready
        for b in cluster: ErrorGenerator(b)

        # Start sender and receiver threads
        cluster[0].declare_queue("test-queue")
        sender = NumberedSender(cluster[0], max_depth=1000)
        receiver = NumberedReceiver(cluster[0], sender=sender)
        receiver.start()
        sender.start()
        # Wait for sender & receiver to get up and running
        retry(lambda: receiver.received > 0)

        # Kill original brokers, start new ones for the duration.
        endtime = time.time() + self.duration()
        i = 0
        while time.time() < endtime:
            sender.sender.assert_running()
            receiver.receiver.assert_running()
            cluster[i].kill()
            i += 1
            b = cluster.start(expect=EXPECT_EXIT_FAIL)
            for b in cluster[i:]: b.ready()
            ErrorGenerator(b)
            time.sleep(5)
        sender.stop()
        receiver.stop()
        for i in range(i, len(cluster)): cluster[i].kill()

    def test_management(self, args=[]):
        """
        Stress test: Run management clients and other clients concurrently
        while killing and restarting brokers.
        """

        class ClientLoop(StoppableThread):
            """Run a client executable in a loop."""
            def __init__(self, broker, cmd):
                StoppableThread.__init__(self)
                self.broker=broker
                self.cmd = cmd          # Client command.
                self.lock = Lock()
                self.process = None     # Client process.
                self.start()

            def run(self):
                try:
                    while True:
                        self.lock.acquire()
                        try:
                            if self.stopped: break
                            self.process = self.broker.test.popen(
                                self.cmd, expect=EXPECT_UNKNOWN)
                        finally:
                            self.lock.release()
                        try:
                            exit = self.process.wait()
                        except OSError, e:
                            # Process may already have been killed by self.stop()
                            break
                        except Exception, e:
                            self.process.unexpected(
                                "client of %s: %s"%(self.broker.name, e))
                        self.lock.acquire()
                        try:
                            if self.stopped: break
                            if exit != 0:
                                self.process.unexpected(
                                    "client of %s exit code %s"%(self.broker.name, exit))
                        finally:
                            self.lock.release()
                except Exception, e:
                    self.error = RethrownException("Error in ClientLoop.run")

            def stop(self):
                """Stop the running client and wait for it to exit"""
                self.lock.acquire()
                try:
                    if self.stopped: return
                    self.stopped = True
                    if self.process:
                        try: self.process.kill() # Kill the client.
                        except OSError: pass # The client might not be running.
                finally: self.lock.release()
                StoppableThread.stop(self)

        # body of test_management()

        args += ["--mgmt-pub-interval", 1]
        args += ["--log-enable=trace+:management"]
        # Use store if present.
        if BrokerTest.store_lib: args +=["--load-module", BrokerTest.store_lib]
        cluster = self.cluster(3, args, expect=EXPECT_EXIT_FAIL) # brokers will be killed

        clients = [] # Per-broker list of clients that only connect to one broker.
        mclients = [] # Management clients that connect to every broker in the cluster.

        def start_clients(broker):
            """Start ordinary clients for a broker."""
            cmds=[
                ["qpid-tool", "localhost:%s"%(broker.port())],
                ["qpid-perftest", "--count=5000", "--durable=yes",
                 "--base-name", str(qpid.datatypes.uuid4()), "--port", broker.port()],
                ["qpid-txtest", "--queue-base-name", "tx-%s"%str(qpid.datatypes.uuid4()),
                 "--port", broker.port()],
                ["qpid-queue-stats", "-a", "localhost:%s" %(broker.port())]
                 ]
            clients.append([ClientLoop(broker, cmd) for cmd in cmds])

        def start_mclients(broker):
            """Start management clients that make multiple connections."""
            cmd = ["qpid-stat", "-b", "localhost:%s" %(broker.port())]
            mclients.append(ClientLoop(broker, cmd))

        endtime = time.time() + self.duration()
        # For long duration, first run is a quarter of the duration.
        runtime = min(5.0, self.duration() / 3.0)
        alive = 0                       # First live cluster member
        for i in range(len(cluster)): start_clients(cluster[i])
        start_mclients(cluster[alive])

        while time.time() < endtime:
            time.sleep(runtime)
            runtime = 5                 # Remaining runs 5 seconds, frequent broker kills
            for b in cluster[alive:]: b.ready() # Check if a broker crashed.
            # Kill the first broker, expect the clients to fail.
            b = cluster[alive]
            b.ready()
            b.kill()
            # Stop the brokers clients and all the mclients.
            for c in clients[alive] + mclients:
                try: c.stop()
                except: pass            # Ignore expected errors due to broker shutdown.
            clients[alive] = []
            mclients = []
            # Start another broker and clients
            alive += 1
            cluster.start(expect=EXPECT_EXIT_FAIL)
            cluster[-1].ready()         # Wait till its ready
            start_clients(cluster[-1])
            start_mclients(cluster[alive])
        for c in chain(mclients, *clients):
            c.stop()
        for b in cluster[alive:]:
            b.ready() # Verify still alive
            b.kill()
        # Verify that logs are consistent
        cluster_test_logs.verify_logs()

    def test_management_qmf2(self):
        self.test_management(args=["--mgmt-qmf2=yes"])

    def test_connect_consistent(self):
        args=["--mgmt-pub-interval=1","--log-enable=trace+:management"]
        cluster = self.cluster(2, args=args)
        end = time.time() + self.duration()
        while (time.time() < end):  # Get a management interval
            for i in xrange(1000): cluster[0].connect().close()
        cluster_test_logs.verify_logs()

    def test_flowlimit_failover(self):
        """Test fail-over during continuous send-receive with flow control
        active.
        """

        # Original cluster will all be killed so expect exit with failure
        cluster = self.cluster(3, expect=EXPECT_EXIT_FAIL)
        for b in cluster: b.ready()     # Wait for brokers to be ready

        # create a queue with rather draconian flow control settings
        ssn0 = cluster[0].connect().session()
        s0 = ssn0.sender("test-queue; {create:always, node:{type:queue, x-declare:{arguments:{'qpid.flow_stop_count':2000, 'qpid.flow_resume_count':100}}}}")

        receiver = NumberedReceiver(cluster[0])
        receiver.start()
        senders = [NumberedSender(cluster[0]) for i in range(1,3)]
        for s in senders:
            s.start()
        # Wait for senders & receiver to get up and running
        retry(lambda: receiver.received > 2*senders)

        # Kill original brokers, start new ones for the duration.
        endtime = time.time() + self.duration();
        i = 0
        while time.time() < endtime:
            for s in senders: s.sender.assert_running()
            receiver.receiver.assert_running()
            for b in cluster[i:]: b.ready() # Check if any broker crashed.
            cluster[i].kill()
            i += 1
            b = cluster.start(expect=EXPECT_EXIT_FAIL)
            time.sleep(5)
        for s in senders:
            s.stop()
        receiver.stop()
        for i in range(i, len(cluster)): cluster[i].kill()

    def test_ttl_failover(self):
        """Test that messages with TTL don't cause problems in a cluster with failover"""

        class Client(StoppableThread):

            def __init__(self, broker):
                StoppableThread.__init__(self)
                self.connection = broker.connect(reconnect=True)
                self.auto_fetch_reconnect_urls(self.connection)
                self.session = self.connection.session()

            def auto_fetch_reconnect_urls(self, conn):
                """Replacment for qpid.messaging.util version which is noisy"""
                ssn = conn.session("auto-fetch-reconnect-urls")
                rcv = ssn.receiver("amq.failover")
                rcv.capacity = 10

                def main():
                    while True:
                        try:
                            msg = rcv.fetch()
                            qpid.messaging.util.set_reconnect_urls(conn, msg)
                            ssn.acknowledge(msg, sync=False)
                        except messaging.exceptions.LinkClosed: return
                        except messaging.exceptions.ConnectionError: return

                thread = Thread(name="auto-fetch-reconnect-urls", target=main)
                thread.setDaemon(True)
                thread.start()

            def stop(self):
                StoppableThread.stop(self)
                self.connection.detach()

        class Sender(Client):
            def __init__(self, broker, address):
                Client.__init__(self, broker)
                self.sent = 0    # Number of messages _reliably_ sent.
                self.sender = self.session.sender(address, capacity=1000)

            def send_counted(self, ttl):
                self.sender.send(Message(str(self.sent), ttl=ttl))
                self.sent += 1

            def run(self):
                while not self.stopped:
                    choice = random.randint(0,4)
                    if choice == 0: self.send_counted(None) # No ttl
                    elif choice == 1: self.send_counted(100000) # Large ttl
                    else: # Small ttl, might expire
                        self.sender.send(Message("", ttl=random.random()/10))
                self.sender.send(Message("z"), sync=True) # Chaser.

        class Receiver(Client):

            def __init__(self, broker, address):
                Client.__init__(self, broker)
                self.received = 0 # Number of  non-empty (reliable) messages received.
                self.receiver = self.session.receiver(address, capacity=1000)
            def run(self):
                try:
                    while True:
                        m = self.receiver.fetch(1)
                        if m.content == "z": break
                        if m.content:   # Ignore unreliable messages
                            # Ignore duplicates
                            if int(m.content) == self.received: self.received += 1
                except Exception,e: self.error = e

        # def test_ttl_failover

        # Original cluster will all be killed so expect exit with failure
        # Set small purge interval.
        cluster = self.cluster(3, expect=EXPECT_EXIT_FAIL, args=["--queue-purge-interval=1"])
        for b in cluster: b.ready()     # Wait for brokers to be ready

        # Python client failover produces noisy WARN logs, disable temporarily
        logger = logging.getLogger()
        log_level = logger.getEffectiveLevel()
        logger.setLevel(logging.ERROR)
        sender = None
        receiver = None
        try:
            # Start sender and receiver threads
            receiver = Receiver(cluster[0], "q;{create:always}")
            receiver.start()
            sender = Sender(cluster[0], "q;{create:always}")
            sender.start()
            # Wait for sender & receiver to get up and running
            retry(lambda: receiver.received > 0)

            # Kill brokers in a cycle.
            endtime = time.time() + self.duration()
            runtime = min(5.0, self.duration() / 4.0)
            i = 0
            while time.time() < endtime:
                for b in cluster[i:]: b.ready() # Check if any broker crashed.
                cluster[i].kill()
                i += 1
                b = cluster.start(expect=EXPECT_EXIT_FAIL)
                b.ready()
                time.sleep(runtime)
            sender.stop()
            receiver.stop()
            for b in cluster[i:]:
                b.ready()               # Check it didn't crash
                b.kill()
            self.assertEqual(sender.sent, receiver.received)
            cluster_test_logs.verify_logs()

        finally:
            # Detach to avoid slow reconnect attempts during shut-down if test fails.
            if sender: sender.connection.detach()
            if receiver: receiver.connection.detach()
            logger.setLevel(log_level)

    def test_msg_group_failover(self):
        """Test fail-over during continuous send-receive of grouped messages.
        """

        class GroupedTrafficGenerator(Thread):
            def __init__(self, url, queue, group_key):
                Thread.__init__(self)
                self.url = url
                self.queue = queue
                self.group_key = group_key
                self.status = -1

            def run(self):
                # generate traffic for approx 10 seconds (2011msgs / 200 per-sec)
                cmd = ["msg_group_test",
                       "--broker=%s" % self.url,
                       "--address=%s" % self.queue,
                       "--connection-options={%s}" % (Cluster.CONNECTION_OPTIONS),
                       "--group-key=%s" % self.group_key,
                       "--receivers=2",
                       "--senders=3",
                       "--messages=2011",
                       "--send-rate=200",
                       "--capacity=11",
                       "--ack-frequency=23",
                       "--allow-duplicates",
                       "--group-size=37",
                       "--randomize-group-size",
                       "--interleave=13"]
                #      "--trace"]
                self.generator = Popen( cmd );
                self.status = self.generator.wait()
                return self.status

            def results(self):
                self.join(timeout=30)  # 3x assumed duration
                if self.isAlive(): return -1
                return self.status

        # Original cluster will all be killed so expect exit with failure
        cluster = self.cluster(3, expect=EXPECT_EXIT_FAIL, args=["-t"])
        for b in cluster: b.ready()     # Wait for brokers to be ready

        # create a queue with rather draconian flow control settings
        ssn0 = cluster[0].connect().session()
        q_args = "{'qpid.group_header_key':'group-id', 'qpid.shared_msg_group':1}"
        s0 = ssn0.sender("test-group-q; {create:always, node:{type:queue, x-declare:{arguments:%s}}}" % q_args)

        # Kill original brokers, start new ones for the duration.
        endtime = time.time() + self.duration();
        i = 0
        while time.time() < endtime:
            traffic = GroupedTrafficGenerator( cluster[i].host_port(),
                                               "test-group-q", "group-id" )
            traffic.start()
            time.sleep(1)

            for x in range(2):
                for b in cluster[i:]: b.ready() # Check if any broker crashed.
                cluster[i].kill()
                i += 1
                b = cluster.start(expect=EXPECT_EXIT_FAIL)
                time.sleep(1)

            # wait for traffic to finish, verify success
            self.assertEqual(0, traffic.results())

        for i in range(i, len(cluster)): cluster[i].kill()


class StoreTests(BrokerTest):
    """
    Cluster tests that can only be run if there is a store available.
    """
    def args(self):
        assert BrokerTest.store_lib
        return ["--load-module", BrokerTest.store_lib]

    def test_store_loaded(self):
        """Ensure we are indeed loading a working store"""
        broker = self.broker(self.args(), name="recoverme", expect=EXPECT_EXIT_FAIL)
        m = Message("x", durable=True)
        broker.send_message("q", m)
        broker.kill()
        broker = self.broker(self.args(), name="recoverme")
        self.assertEqual("x", broker.get_message("q").content)

    def test_kill_restart(self):
        """Verify we can kill/resetart a broker with store in a cluster"""
        cluster = self.cluster(1, self.args())
        cluster.start("restartme", expect=EXPECT_EXIT_FAIL).kill()

        # Send a message, retrieve from the restarted broker
        cluster[0].send_message("q", "x")
        m = cluster.start("restartme").get_message("q")
        self.assertEqual("x", m.content)

    def stop_cluster(self,broker):
        """Clean shut-down of a cluster"""
        self.assertEqual(0, qpid_cluster.main(
            ["-kf", broker.host_port()]))

    def test_persistent_restart(self):
        """Verify persistent cluster shutdown/restart scenarios"""
        cluster = self.cluster(0, args=self.args() + ["--cluster-size=3"])
        a = cluster.start("a", expect=EXPECT_EXIT_OK, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_OK, wait=False)
        c = cluster.start("c", expect=EXPECT_EXIT_FAIL, wait=True)
        a.send_message("q", Message("1", durable=True))
        # Kill & restart one member.
        c.kill()
        self.assertEqual(a.get_message("q").content, "1")
        a.send_message("q", Message("2", durable=True))
        c = cluster.start("c", expect=EXPECT_EXIT_OK)
        self.assertEqual(c.get_message("q").content, "2")
        # Shut down the entire cluster cleanly and bring it back up
        a.send_message("q", Message("3", durable=True))
        self.stop_cluster(a)
        a = cluster.start("a", wait=False)
        b = cluster.start("b", wait=False)
        c = cluster.start("c", wait=True)
        self.assertEqual(a.get_message("q").content, "3")

    def test_persistent_partial_failure(self):
        # Kill 2 members, shut down the last cleanly then restart
        # Ensure we use the clean database
        cluster = self.cluster(0, args=self.args() + ["--cluster-size=3"])
        a = cluster.start("a", expect=EXPECT_EXIT_FAIL, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_FAIL, wait=False)
        c = cluster.start("c", expect=EXPECT_EXIT_OK, wait=True)
        a.send_message("q", Message("4", durable=True))
        a.kill()
        b.kill()
        self.assertEqual(c.get_message("q").content, "4")
        c.send_message("q", Message("clean", durable=True))
        self.stop_cluster(c)
        a = cluster.start("a", wait=False)
        b = cluster.start("b", wait=False)
        c = cluster.start("c", wait=True)
        self.assertEqual(a.get_message("q").content, "clean")

    def test_wrong_cluster_id(self):
        # Start a cluster1 broker, then try to restart in cluster2
        cluster1 = self.cluster(0, args=self.args())
        a = cluster1.start("a", expect=EXPECT_EXIT_OK)
        a.terminate()
        cluster2 = self.cluster(1, args=self.args())
        try:
            a = cluster2.start("a", expect=EXPECT_EXIT_FAIL)
            a.ready()
            self.fail("Expected exception")
        except: pass

    def test_wrong_shutdown_id(self):
        # Start 2 members and shut down.
        cluster = self.cluster(0, args=self.args()+["--cluster-size=2"])
        a = cluster.start("a", expect=EXPECT_EXIT_OK, wait=False)
        b = cluster.start("b", expect=EXPECT_EXIT_OK, wait=False)
        self.stop_cluster(a)
        self.assertEqual(a.wait(), 0)
        self.assertEqual(b.wait(), 0)

        # Restart with a different member and shut down.
        a = cluster.start("a", expect=EXPECT_EXIT_OK, wait=False)
        c = cluster.start("c", expect=EXPECT_EXIT_OK, wait=False)
        self.stop_cluster(a)
        self.assertEqual(a.wait(), 0)
        self.assertEqual(c.wait(), 0)
        # Mix members from both shutdown events, they should fail
        # TODO aconway 2010-03-11: can't predict the exit status of these
        # as it depends on the order of delivery of initial-status messages.
        # See comment at top of this file.
        a = cluster.start("a", expect=EXPECT_UNKNOWN, wait=False)
        b = cluster.start("b", expect=EXPECT_UNKNOWN, wait=False)
        self.assertRaises(Exception, lambda: a.ready())
        self.assertRaises(Exception, lambda: b.ready())

    def test_solo_store_clean(self):
        # A single node cluster should always leave a clean store.
        cluster = self.cluster(0, self.args())
        a = cluster.start("a", expect=EXPECT_EXIT_FAIL)
        a.send_message("q", Message("x", durable=True))
        a.kill()
        a = cluster.start("a")
        self.assertEqual(a.get_message("q").content, "x")

    def test_last_store_clean(self):
        # Verify that only the last node in a cluster to shut down has
        # a clean store. Start with cluster of 3, reduce to 1 then
        # increase again to ensure that a node that was once alone but
        # finally did not finish as the last node does not get a clean
        # store.
        cluster = self.cluster(0, self.args())
        a = cluster.start("a", expect=EXPECT_EXIT_FAIL)
        self.assertEqual(a.store_state(), "clean")
        b = cluster.start("b", expect=EXPECT_EXIT_FAIL)
        c = cluster.start("c", expect=EXPECT_EXIT_FAIL)
        self.assertEqual(b.store_state(), "dirty")
        self.assertEqual(c.store_state(), "dirty")
        retry(lambda: a.store_state() == "dirty")

        a.send_message("q", Message("x", durable=True))
        a.kill()
        b.kill()                # c is last man, will mark store clean
        retry(lambda: c.store_state() == "clean")
        a = cluster.start("a", expect=EXPECT_EXIT_FAIL) # c no longer last man
        retry(lambda: c.store_state() == "dirty")
        c.kill()                        # a is now last man
        retry(lambda: a.store_state() == "clean")
        a.kill()
        self.assertEqual(a.store_state(), "clean")
        self.assertEqual(b.store_state(), "dirty")
        self.assertEqual(c.store_state(), "dirty")

    def test_restart_clean(self):
        """Verify that we can re-start brokers one by one in a
        persistent cluster after a clean oshutdown"""
        cluster = self.cluster(0, self.args())
        a = cluster.start("a", expect=EXPECT_EXIT_OK)
        b = cluster.start("b", expect=EXPECT_EXIT_OK)
        c = cluster.start("c", expect=EXPECT_EXIT_OK)
        a.send_message("q", Message("x", durable=True))
        self.stop_cluster(a)
        a = cluster.start("a")
        b = cluster.start("b")
        c = cluster.start("c")
        self.assertEqual(c.get_message("q").content, "x")

    def test_join_sub_size(self):
        """Verify that after starting a cluster with cluster-size=N,
        we can join new members even if size < N-1"""
        cluster = self.cluster(0, self.args()+["--cluster-size=3"])
        a = cluster.start("a", wait=False, expect=EXPECT_EXIT_FAIL)
        b = cluster.start("b", wait=False, expect=EXPECT_EXIT_FAIL)
        c = cluster.start("c")
        a.send_message("q", Message("x", durable=True))
        a.send_message("q", Message("y", durable=True))
        a.kill()
        b.kill()
        a = cluster.start("a")
        self.assertEqual(c.get_message("q").content, "x")
        b = cluster.start("b")
        self.assertEqual(c.get_message("q").content, "y")
