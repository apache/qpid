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

#
# FIXME aconway 2009-10-30: Missing features:
# - support for calling qpid-tool/qpid-config directly from a test.
#   (Not by starting a separate process)
# - helper functions to run  executable clients e.g. sender/receiver.
# 

import os, signal, string, tempfile, popen2, socket
from qpid import connection, messaging
from shutil import rmtree
from unittest import TestCase

# Values for expected outcome of process at end of test
EXPECT_NONE=0              # No expectation
EXPECT_EXIT_OK=1           # Expect to exit with 0 before end of test
EXPECT_RUNNING=2           # Expect to still be running at end of test
    
def is_running(pid):
    try:
        os.kill(pid, 0)
        return True
    except:
        return False

class Popen:
    """Similar to subprocess.Popen but using popen2 classes for portability.
    Can set expectation that process exits with 0 or is still running at end of test.
    """

    def __init__(self, cmd, expect=EXPECT_EXIT_OK):
        self._cmd  = cmd
        self.expect = expect
        self._popen = popen2.Popen3(cmd, True)
        self.stdin = self._popen.tochild
        self.stdout = self._popen.fromchild
        self.stderr = self._popen.childerr
        self.pid = self._popen.pid

    def _addoutput(self, msg, name, output):
        if output: msg += [name, output]

    def _check(self, retcode):
        self.returncode = retcode
        if self.expect == EXPECT_EXIT_OK and self.returncode != 0:
            msg = [ "Unexpected error %d: %s" %(retcode, string.join(self._cmd)) ]
            self._addoutput(msg, "stdout:", self.stdout.read())
            self._addoutput(msg, "stderr:", self.stderr.read())
            raise Exception(string.join(msg, "\n"))
    
    def poll(self):
        retcode = self._popen.poll()
        if retcode != -1: self._check(retcode)
        return retcode

    def wait(self):
        self._check(self._popen.wait())
        return self.returncode

    def communicate(self, input=None):
        if input: self.stdin.write(input)
        outerr = (self.stdout.read(), self.stderr.read())
        wait()
        return outerr

    def is_running(self): return is_running(pid)
    def send_signal(self, sig): os.kill(self.pid,sig)
    def terminate(self): self.send_signal(signal.SIGTERM)
    def kill(self): self.send_signal(signal.SIGKILL)

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
        
        cmd = [self._qpidd, "--port=0", "--no-module-dir", "--auth=no"] + args
        if name: self.name = name
        else:
            self.name = "broker%d" % Broker._broker_count
            Broker._broker_count += 1
        self.log = os.path.join(test.dir, self.name+".log")
        cmd += ["--log-to-file", self.log, "--log-prefix", self.name]
        self.datadir = os.path.join(test.dir, self.name)
        cmd += ["--data-dir", self.datadir]
        if self._store_lib: cmd += ["--load-module", self._store_lib]

        Popen.__init__(self, cmd, expect)
        self.port = int(self.stdout.readline())
        test.willkill(self)             
        self.host = "localhost"         # Placeholder for remote brokers.

    def connect(self):
        """New API connection to the broker."""
        return messaging.Connection.open(self.host, self.port)

    def connect_old(self):
        """Old API connection to the broker."""
        socket = connect(self.host,self.port)
        connection = connection.Connection (sock=socket)
        connection.start()
        return connection;


class Cluster:
    """A cluster of brokers in a test."""

    _cluster_lib = checkenv("CLUSTER_LIB")
    _cluster_count = 0

    # FIXME aconway 2009-10-30: missing args
    def __init__(self, test, count=0):
        self.test = test
        self._brokers=[]
        self.name = "cluster%d" % Cluster._cluster_count
        Cluster._cluster_count += 1
        # Use unique cluster name
        self.args = []
        self.args += [ "--cluster-name", "%s-%s:%d" % (self.name, socket.gethostname(), os.getpid()) ]
        self.args += [ "--load-module", self._cluster_lib ]
        self.start_n(count)

    def start(self, name=None, expect=EXPECT_RUNNING):
        """Add a broker to the cluster. Returns the index of the new broker."""
        if not name: name="%s-%d" % (self.name, len(self._brokers))
        self._brokers.append(self.test.broker(self.args, name, expect))
        return self._brokers[-1]

    def start_n(self, count):
        for i in range(count): self.start()

    def wait(self):
        """Wait for all cluster members to be ready"""
        for b in brokers:
            b.connect().close()
        
    # Behave like a list of brokers.
    def __len__(self): return len(self._brokers)
    def __getitem__(self,index): return self._brokers[index]
    def __iter__(self): return self._brokers.__iter__()

class BrokerTest(TestCase):
    """Provides working dir that is cleaned up only if test passes.
    Tracks processes started by test and kills at end of test.
    Note that subclasses need to call selfpassed() at the end of
    a successful test."""
    
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.popens = []

    def willkill(self, popen):
        """Add process to be killed at end of test"""
        self.popens.append(popen)

    def popen(self, cmd, expect=EXPECT_EXIT_OK):
        """Start a process that will be killed at end of test"""
        p = Popen(cmd, expect)
        willkill(p)
        return p

    def broker(self, args=[], name=None, expect=EXPECT_RUNNING):
        return Broker(self, args, name, expect)

    def cluster(self, count=0): return Cluster(self)

    def passed(self):
        """On pass, kill processes and clean up work directory"""
        rmtree(self.dir)
        self.passed = True

    def tearDown(self):
        """On failure print working dir, kill processes"""
        if not self.passed: print "TEST DIRECTORY: ", self.dir
        err=[]
        for p in self.popens:
            if p.is_running:
                p.kill()
            else:
                if p.expect == EXPECT_RUNNING:
                    err.append("NOT running: %s" % (cmd))
        if len(err) != 0:
            raise Exception(string.join(err, "\n"))


