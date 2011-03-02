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

import os, signal, sys, time, imp, re, subprocess, glob, cluster_test_logs
from qpid import datatypes, messaging
from brokertest import *
from qpid.harness import Skipped
from qpid.messaging import Message, Empty, Disposition, REJECTED
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
        aclf.write("""
acl deny zag@QPID create queue
acl allow all all
""")
        aclf.close()
        cluster = self.cluster(2, args=["--auth", "yes",
                                        "--sasl-config", sasl_config,
                                        "--load-module", os.getenv("ACL_LIB"),
                                        "--acl-file", acl])

        # Valid user/password, ensure queue is created.
        c = cluster[0].connect(username="zig", password="zig")
        c.session().sender("ziggy;{create:always}")
        c.close()
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
        c = cluster[0].connect(username="zag", password="zag")
        try:
            s = c.session()
            s.sender("zaggy;{assert:always}")
            s.close()
            self.fail("Expected exception")
        except messaging.exceptions.NotFound: pass

    def test_user_id_update(self):
        """Ensure that user-id of an open session is updated to new cluster members"""
        sasl_config=os.path.join(self.rootdir, "sasl_config")
        cluster = self.cluster(1, args=["--auth", "yes", "--sasl-config", sasl_config,])
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

    def test_dr_no_message(self):
        """Regression test for https://bugzilla.redhat.com/show_bug.cgi?id=655141
        Joining broker crashes with 'error deliveryRecord no update message'
        """

        cluster = self.cluster(1)
        session0 = cluster[0].connect().session()
        s = session0.sender("q1;{create:always}")
        s.send(Message("a", ttl=0.05), sync=False)
        s.send(Message("b", ttl=0.05), sync=False)
        r1 = session0.receiver("q1")
        self.assertEqual("a", r1.fetch(timeout=0).content)
        r2 = session0.receiver("q1;{mode:browse}")
        self.assertEqual("b", r2.fetch(timeout=0).content)
        # Leave messages un-acknowledged, let the expire, then start new broker.
        time.sleep(.1)
        cluster.start()
        self.assertRaises(Empty, cluster[1].connect().session().receiver("q1").fetch,0)

    def test_route_update(self):
        """Regression test for https://issues.apache.org/jira/browse/QPID-2982
        Links and bridges associated with routes were not replicated on update.
        This meant extra management objects and caused an exit if a management
        client was attached.
        """
        args=["--mgmt-pub-interval=1","--log-enable=trace+:management"]
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
             "--connection-options={reconnect:true}"
             ])
        self.receiver = self.popen(
            ["qpid-receive",
             "--broker", url,
             "--address", queue,
             "--ignore-duplicates",
             "--check-redelivered",
             "--connection-options={reconnect:true}",
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
                self.sender.send(self.msg)
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
        q = [q for q in brokers.first().qmf_session.getObjects(_class="queue") if q.name == "flq"][0]
        oid = q.getObjectId()
        self.assertEqual(q.name, "flq")
        self.assertEqual(q.arguments, {u'qpid.flow_stop_count': 5L, u'qpid.flow_resume_count': 3L})
        assert not q.flowStopped

        # fill the queue on one broker until flow control is active
        for x in range(5): s0.send(Message(str(x)))
        sender = ShortTests.BlockedSend(s0, Message(str(6)))
        sender.start()                  # Tests that sender does block
        # Verify the broker queue goes into a flowStopped state
        deadline = time.time() + 1
        while not q.flowStopped and time.time() < deadline: q.update()
        assert q.flowStopped
        sender.assert_blocked()         # Still blocked

        # Now verify the  both brokers in cluster have same configuration
        brokers.second().startQmf()
        qs = brokers.second().qmf_session.getObjects(_objectId=oid)
        self.assertEqual(len(qs), 1)
        q = qs[0]
        self.assertEqual(q.name, "flq")
        self.assertEqual(q.arguments, {u'qpid.flow_stop_count': 5L, u'qpid.flow_resume_count': 3L})
        assert q.flowStopped

        # now drain the queue using a session to the other broker
        ssn1 = brokers.second().connect().session()
        r1 = ssn1.receiver("flq", capacity=6)
        for x in range(4):
            r1.fetch(timeout=0)
            ssn1.acknowledge()
        sender.wait()                   # Verify no longer blocked.

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
        return          # TODO aconway 2011-02-18: disabled till fixed, QPID-2935
        cluster = self.cluster(2)
        class Brokers:
            def first(self): return cluster[0]
            def second(self): return cluster[1]
        self.queue_flowlimit_test(Brokers())

    def test_queue_flowlimit_cluster_join(self):
        return          # TODO aconway 2011-02-18: disabled till fixed, QPID-2935
        cluster = self.cluster(1)
        class Brokers:
            def first(self): return cluster[0]
            def second(self):
                if len(cluster) == 1: cluster.start()
                return cluster[1]
        self.queue_flowlimit_test(Brokers())

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
        for b in cluster: ErrorGenerator(b)

        # Start sender and receiver threads
        cluster[0].declare_queue("test-queue")
        sender = NumberedSender(cluster[1], 1000) # Max queue depth
        receiver = NumberedReceiver(cluster[2], sender)
        receiver.start()
        sender.start()

        # Kill original brokers, start new ones for the duration.
        endtime = time.time() + self.duration()
        i = 0
        while time.time() < endtime:
            cluster[i].kill()
            i += 1
            b = cluster.start(expect=EXPECT_EXIT_FAIL)
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
                        finally: self.lock.release()
                        try: exit = self.process.wait()
                        except OSError, e:
                            # Seems to be a race in wait(), it throws
                            # "no such process" during test shutdown.
                            # Doesn't indicate a test error, ignore.
                            return
                        except Exception, e:
                            self.process.unexpected(
                                "client of %s: %s"%(self.broker.name, e))
                        self.lock.acquire()
                        try:
                            # Quit and ignore errors if stopped or expecting failure.
                            if self.stopped: break
                            if exit != 0:
                                self.process.unexpected(
                                    "client of %s exit code %s"%(self.broker.name, exit))
                        finally: self.lock.release()
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
        cluster = self.cluster(3, args)

        clients = [] # Per-broker list of clients that only connect to one broker.
        mclients = [] # Management clients that connect to every broker in the cluster.

        def start_clients(broker):
            """Start ordinary clients for a broker."""
            cmds=[
                ["qpid-tool", "localhost:%s"%(broker.port())],
                ["qpid-perftest", "--count", 50000,
                 "--base-name", str(qpid.datatypes.uuid4()), "--port", broker.port()],
                ["qpid-queue-stats", "-a", "localhost:%s" %(broker.port())],
                ["testagent", "localhost", str(broker.port())] ]
            clients.append([ClientLoop(broker, cmd) for cmd in cmds])

        def start_mclients(broker):
            """Start management clients that make multiple connections."""
            cmd = ["qpid-stat", "-b", "localhost:%s" %(broker.port())]
            mclients.append(ClientLoop(broker, cmd))

        endtime = time.time() + self.duration()
        runtime = self.duration() / 4   # First run is longer, use quarter of duration.
        alive = 0                       # First live cluster member
        for i in range(len(cluster)): start_clients(cluster[i])
        start_mclients(cluster[alive])

        while time.time() < endtime:
            time.sleep(runtime)
            runtime = 5                 # Remaining runs 5 seconds, frequent broker kills
            for b in cluster[alive:]: b.ready() # Check if a broker crashed.
            # Kill the first broker, expect the clients to fail.
            b = cluster[alive]
            b.expect = EXPECT_EXIT_FAIL
            b.kill()
            # Stop the brokers clients and all the mclients.
            for c in clients[alive] + mclients:
                try: c.stop()
                except: pass            # Ignore expected errors due to broker shutdown.
            clients[alive] = []
            mclients = []
            # Start another broker and clients
            alive += 1
            cluster.start()
            start_clients(cluster[-1])
            start_mclients(cluster[alive])
        for c in chain(mclients, *clients):
            c.stop()

        # Verify that logs are consistent
        cluster_test_logs.verify_logs()

    def test_management_qmf2(self):
        self.test_management(args=["--mgmt-qmf2=yes"])

    def test_connect_consistent(self):   # FIXME aconway 2011-01-18:
        args=["--mgmt-pub-interval=1","--log-enable=trace+:management"]
        cluster = self.cluster(2, args=args)
        end = time.time() + self.duration()
        while (time.time() < end):  # Get a management interval
            for i in xrange(1000): cluster[0].connect().close()
            cluster_test_logs.verify_logs()


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
