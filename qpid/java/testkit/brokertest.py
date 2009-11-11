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

# Support library for tests that start multiple brokers, e.g. cluster
# or federation

import os, signal, string, tempfile, popen2, socket, threading, time
import qpid
from qpid import connection, messaging, util
from qpid.harness import Skipped
from unittest import TestCase
from copy import copy
from threading import Thread, Lock, Condition
from shutil import rmtree 

# Values for expected outcome of process at end of test
EXPECT_EXIT_OK=1           # Expect to exit with 0 status before end of test.
EXPECT_EXIT_FAIL=2         # Expect to exit with non-0 status before end of test.
EXPECT_RUNNING=3           # Expect to still be running at end of test
    
def is_running(pid):
    try:
        os.kill(pid, 0)
        return True
    except:
        return False

class Unexpected(Exception):
    pass

class Popen(popen2.Popen3):
    """
    Similar to subprocess.Popen but using popen2 classes for portability.
    Can set and verify expectation of process status at end of test.
    """

    def __init__(self, cmd, expect=EXPECT_EXIT_OK):
        self.cmd  = [ str(x) for x in cmd ]
        popen2.Popen3.__init__(self, self.cmd, True)
        self.expect = expect
        self.stdin = self.tochild
        self.stdout = self.fromchild
        self.stderr = self.childerr

    def unexpected(self,msg):
        raise Unexpected("%s: %s\n--stdout:\n%s\n--stderr:\n%s" %
                         (msg, self.cmd_str(), self.stdout.read(), self.stderr.read()))
    
    def testend(self):                  # Clean up at end of test.
        if self.expect == EXPECT_RUNNING:
            try:
                self.kill()
            except:
                self.unexpected("Expected running but exited %d" % self.wait())
        else:
            # Give the process some time to exit.
            delay = 0.1
            while (self.poll() is None and delay < 1):
                time.sleep(delay)
                delay *= 2
            if self.returncode is None: # Still haven't stopped
                self.kill()
                self.unexpected("Expected to exit but still running")
            elif self.expect == EXPECT_EXIT_OK and self.returncode != 0:
                self.unexpected("Expected exit ok but exited %d" % self.returncode)
            elif self.expect == EXPECT_EXIT_FAIL and self.returncode == 0:
                self.unexpected("Expected to fail but exited ok")
               
    def communicate(self, input=None):
        if input:
            self.stdin.write(input)
            self.stdin.close()
        outerr = (self.stdout.read(), self.stderr.read())
        self.wait()
        return outerr

    def is_running(self): return is_running(self.pid)

    def poll(self):
        self.returncode = popen2.Popen3.poll(self)
        if (self.returncode == -1): self.returncode = None
        return self.returncode

    def wait(self):
        self.returncode = popen2.Popen3.wait(self)
        return self.returncode

    def send_signal(self, sig):
        os.kill(self.pid,sig)
        self.wait()

    def terminate(self): self.send_signal(signal.SIGTERM)
    def kill(self): self.send_signal(signal.SIGKILL)
        
        

    def cmd_str(self): return " ".join([str(s) for s in self.cmd])

def checkenv(name):
    value = os.getenv(name)
    if not value: raise Exception("Environment variable %s is not set" % name)
    return value

class Broker(Popen):
    "A broker process. Takes care of start, stop and logging."
    _store_lib = os.getenv("STORE_LIB")
    _qpidd = checkenv("QPIDD_EXEC")
    _broker_count = 0

    def __init__(self, test, args=[], name=None, expect=EXPECT_RUNNING):
        """Start a broker daemon. name determines the data-dir and log
        file names."""

        self.test = test
        cmd = [self._qpidd, "--port=0", "--no-module-dir", "--auth=no"] + args
        if name: self.name = name
        else:
            self.name = "broker%d" % Broker._broker_count
            Broker._broker_count += 1
        self.log = os.path.join(test.dir, self.name+".log")
        cmd += ["--log-to-file", self.log, "--log-prefix", self.name,"--log-to-stderr=no"]
        self.datadir = os.path.join(test.dir, self.name)
        cmd += ["--data-dir", self.datadir, "-t"]
        if self._store_lib: cmd += ["--load-module", self._store_lib]

        Popen.__init__(self, cmd, expect)
        try: self.port = int(self.stdout.readline())
        except Exception:
            raise Exception("Failed to start broker: "+self.cmd_str())
        test.cleanup_popen(self)
        self.host = "localhost"         # Placeholder for remote brokers.

    def connect(self):
        """New API connection to the broker."""
        return messaging.Connection.open(self.host, self.port)

    def connect_old(self):
        """Old API connection to the broker."""
        socket = qpid.util.connect(self.host,self.port)
        connection = qpid.connection.Connection (sock=socket)
        connection.start()
        return connection;

    def declare_queue(self, queue):
        c = self.connect_old()
        s = c.session(str(qpid.datatypes.uuid4()))
        s.queue_declare(queue=queue)
        c.close()

class Cluster:
    """A cluster of brokers in a test."""

    _cluster_lib = checkenv("CLUSTER_LIB")
    _cluster_count = 0

    def __init__(self, test, count=0, args=[], expect=EXPECT_RUNNING):
        self.test = test
        self._brokers=[]
        self.name = "cluster%d" % Cluster._cluster_count
        Cluster._cluster_count += 1
        # Use unique cluster name
        self.args = copy(args)
        self.args += [ "--cluster-name", "%s-%s:%d" % (self.name, socket.gethostname(), os.getpid()) ]
        self.args += [ "--load-module", self._cluster_lib ]
        self.start_n(count, expect=expect)

    def start(self, name=None, expect=EXPECT_RUNNING):
        """Add a broker to the cluster. Returns the index of the new broker."""
        if not name: name="%s-%d" % (self.name, len(self._brokers))
        self._brokers.append(self.test.broker(self.args, name, expect))
        return self._brokers[-1]

    def start_n(self, count, expect=EXPECT_RUNNING):
        for i in range(count): self.start(expect=expect)

    def wait(self):
        """Wait for all cluster members to be ready"""
        for b in self._brokers:
            b.connect().close()

    # Behave like a list of brokers.
    def __len__(self): return len(self._brokers)
    def __getitem__(self,index): return self._brokers[index]
    def __iter__(self): return self._brokers.__iter__()

class BrokerTest(TestCase):
    """
    Tracks processes started by test and kills at end of test.
    Provides a well-known working directory for each test.
    """

    # FIXME aconway 2009-11-05: too many env vars, need a simpler
    # scheme for locating exes and libs

    cluster_lib = os.getenv("CLUSTER_LIB")
    xml_lib = os.getenv("XML_LIB")
    qpidConfig_exec = os.getenv("QPID_CONFIG_EXEC")
    qpidRoute_exec = os.getenv("QPID_ROUTE_EXEC")
    receiver_exec = os.getenv("RECEIVER_EXEC")
    sender_exec = os.getenv("SENDER_EXEC")

    def setUp(self):
        self.dir = os.path.join("brokertest.tmp", self.id())
        if os.path.exists(self.dir):
            rmtree(self.dir)             
        os.makedirs(self.dir)
        self.popens = []

    def tearDown(self):
        err = []
        for p in self.popens:
            try: p.testend()
            except Unexpected, e: err.append(str(e))
        if err: raise Exception("\n".join(err))

    # FIXME aconway 2009-11-06: check for core files of exited processes.
    
    def cleanup_popen(self, popen):
        """Add process to be killed at end of test"""
        self.popens.append(popen)

    def popen(self, cmd, expect=EXPECT_EXIT_OK):
        """Start a process that will be killed at end of test"""
        p = Popen(cmd, expect)
        self.cleanup_popen(p)
        return p

    def broker(self, args=[], name=None, expect=EXPECT_RUNNING):
        """Create and return a broker ready for use"""
        b = Broker(self, args, name, expect)
        b.connect().close()
        return b

    def cluster(self, count=0, args=[], expect=EXPECT_RUNNING):
        """Create and return a cluster ready for use"""
        cluster = Cluster(self, count, args, expect=expect)
        cluster.wait()
        return cluster

class StoppableThread(Thread):
    """
    Base class for threads that do something in a loop and periodically check
    to see if they have been stopped.
    """
    def __init__(self):
        self.stopped = False
        self.error = None
        Thread.__init__(self)

    def stop(self):
        self.stopped = True
        self.join()
        if self.error: raise self.error
    
class Sender(StoppableThread):
    """
    Thread to run a sender client and send numbered messages until stopped.
    """

    def __init__(self, broker):
        StoppableThread.__init__(self)
        self.sender = broker.test.popen(
            [broker.test.sender_exec, "--port", broker.port], expect=EXPECT_RUNNING)

    def run(self):
        try:
            self.sent = 0
            while not self.stopped:
                self.sender.stdin.write(str(self.sent)+"\n")
                self.sender.stdin.flush()
                self.sent += 1
        except Exception, e: self.error = e

class Receiver(Thread):
    """
    Thread to run a receiver client and verify it receives
    sequentially numbered messages.
    """
    def __init__(self, broker):
        Thread.__init__(self)
        self.test = broker.test
        self.receiver = self.test.popen(
            [self.test.receiver_exec, "--port", broker.port], expect=EXPECT_RUNNING)
        self.stopat = None
        self.lock = Lock()
        self.error = None

    def run(self):
        try:
            self.received = 0
            while self.stopat is None or self.received < self.stopat:
                self.lock.acquire()
                try:
                    self.test.assertEqual(self.receiver.stdout.readline(), str(self.received)+"\n")
                    self.received += 1
                finally:
                    self.lock.release()
        except Exception, e:
            self.error = e

    def stop(self, count):
        """Returns when received >= count"""
        self.lock.acquire()
        self.stopat = count
        self.lock.release()
        self.join()
        if self.error: raise self.error

