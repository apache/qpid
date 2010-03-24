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

import os, signal, string, tempfile, popen2, socket, threading, time, imp, re
import qpid, traceback
from qpid import connection, messaging, util
from qpid.compat import format_exc
from qpid.harness import Skipped
from unittest import TestCase
from copy import copy
from threading import Thread, Lock, Condition
from logging import getLogger

log = getLogger("qpid.brokertest")

# Values for expected outcome of process at end of test
EXPECT_EXIT_OK=1           # Expect to exit with 0 status before end of test.
EXPECT_EXIT_FAIL=2         # Expect to exit with non-0 status before end of test.
EXPECT_RUNNING=3           # Expect to still be running at end of test
EXPECT_UNKNOWN=4            # No expectation, don't check exit status.

def find_exe(program):
    """Find an executable in the system PATH"""
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)
    dir, name = os.path.split(program)
    if dir:
        if is_exe(program): return program
    else:
        for path in os.environ["PATH"].split(os.pathsep):
            exe_file = os.path.join(path, program)
            if is_exe(exe_file): return exe_file
    return None

def is_running(pid):
    try:
        os.kill(pid, 0)
        return True
    except:
        return False

class BadProcessStatus(Exception):
    pass

class ExceptionWrapper:
    """Proxy object that adds a message to exceptions raised"""
    def __init__(self, obj, msg):
        self.obj = obj
        self.msg = msg
        
    def __getattr__(self, name):
        func = getattr(self.obj, name)
        return lambda *args, **kwargs: self._wrap(func, args, kwargs)

    def _wrap(self, func, args, kwargs):
        try:
            return func(*args, **kwargs)
        except Exception, e:
            raise Exception("%s: %s" %(self.msg, str(e)))

def error_line(filename):
    """Get the last line of filename for error messages"""
    result = ""
    try:
        f = open(filename)
        try:
            for l in f: result = ": " + l
        finally: f.close()
    except: return ""
    return result

def retry(function, timeout=5, delay=.01):
    """Call function until it returns True or timeout expires.
    Double the delay for each retry. Return True if function
    returns true, False if timeout expires."""
    elapsed = 0
    while not function():
        elapsed += delay
        if elapsed > timeout: return False
        delay *= 2
        time.sleep(delay)
    return True

class Popen(popen2.Popen3):
    """
    Similar to subprocess.Popen but using popen2 classes for portability.
    Can set and verify expectation of process status at end of test.
    Dumps command line, stdout, stderr to data dir for debugging.
    """

    class DrainThread(Thread):
        """Thread to drain a file object and write the data to a file."""
        def __init__(self, infile, outname):
            Thread.__init__(self)
            self.infile, self.outname = infile, outname
            self.outfile = None

        def run(self):
            try:
                for line in self.infile:
                    if self.outfile is None:
                        self.outfile = open(self.outname, "w")
                    self.outfile.write(line)
            finally:
                self.infile.close()
                if self.outfile is not None: self.outfile.close()

    class OutStream(ExceptionWrapper):
        """Wrapper for output streams, handles excpetions & draining output"""
        def __init__(self, infile, outfile, msg):
            ExceptionWrapper.__init__(self, infile, msg)
            self.infile, self.outfile = infile, outfile
            self.thread = None

        def drain(self):
            if self.thread is None:
                self.thread = Popen.DrainThread(self.infile, self.outfile)
                self.thread.start()

    def outfile(self, ext): return "%s.%s" % (self.pname, ext)

    def __init__(self, cmd, expect=EXPECT_EXIT_OK, drain=True):
        """Run cmd (should be a list of arguments)
        expect - if set verify expectation at end of test.
        drain  - if true (default) drain stdout/stderr to files.
        """
        assert find_exe(cmd[0]), "executable not found: "+cmd[0]
        if type(cmd) is type(""): cmd = [cmd] # Make it a list.
        self.cmd  = [ str(x) for x in cmd ]
        self.returncode = None
        popen2.Popen3.__init__(self, self.cmd, True)
        self.expect = expect
        self.pname = "%s-%d" % (os.path.split(self.cmd[0])[1], self.pid)
        msg = "Process %s" % self.pname
        self.stdin = ExceptionWrapper(self.tochild, msg)
        self.stdout = Popen.OutStream(self.fromchild, self.outfile("out"), msg)
        self.stderr = Popen.OutStream(self.childerr, self.outfile("err"), msg)
        f = open(self.outfile("cmd"), "w")
        try: f.write(self.cmd_str())
        finally: f.close()
        log.debug("Started process %s: %s" % (self.pname, " ".join(self.cmd)))
        if drain: self.drain()
        self._clean = False

    def drain(self):
        """Start threads to drain stdout/err"""
        self.stdout.drain()
        self.stderr.drain()

    def _cleanup(self):
        """Close pipes to sub-process"""
        if self._clean: return
        self._clean = True
        self.stdin.close()
        self.drain()                    # Drain output pipes.
        self.stdout.thread.join()       # Drain thread closes pipe.
        self.stderr.thread.join()

    def unexpected(self,msg):
        self._cleanup()
        err = error_line(self.outfile("err")) or error_line(self.outfile("out"))
        raise BadProcessStatus("%s %s%s" % (self.pname, msg, err))
    
    def stop(self):                  # Clean up at end of test.
        try:
            if self.expect == EXPECT_UNKNOWN:
                try: self.kill()            # Just make sure its dead
                except: pass
            elif self.expect == EXPECT_RUNNING:
                try:
                    self.kill()
                except:
                    self.unexpected("expected running, exit code %d" % self.wait())
            else:
                retry(lambda: self.poll() is not None)
                if self.returncode is None: # Still haven't stopped
                    self.kill()
                    self.unexpected("still running")
                elif self.expect == EXPECT_EXIT_OK and self.returncode != 0:
                    self.unexpected("exit code %d" % self.returncode)
                elif self.expect == EXPECT_EXIT_FAIL and self.returncode == 0:
                    self.unexpected("expected error")
        finally:
            self._cleanup()
               
    def communicate(self, input=None):
        if input:
            self.stdin.write(input)
            self.stdin.close()
        outerr = (self.stdout.read(), self.stderr.read())
        self.wait()
        return outerr

    def is_running(self):
        return self.poll() is None

    def assert_running(self):
        if not self.is_running(): unexpected("Exit code %d" % self.returncode)

    def poll(self):
        if self.returncode is not None: return self.returncode
        self.returncode = popen2.Popen3.poll(self)
        if (self.returncode == -1): self.returncode = None
        if self.returncode is not None: self._cleanup()
        return self.returncode

    def wait(self):
        if self.returncode is not None: return self.returncode
        self.drain()
        try: self.returncode = popen2.Popen3.wait(self)
        except OSError,e: raise OSError("Wait failed %s: %s"%(self.pname, e))
        self._cleanup()
        return self.returncode

    def send_signal(self, sig):
        try: os.kill(self.pid,sig)
        except OSError,e: raise OSError("Kill failed %s: %s"%(self.pname, e))
        self._cleanup()

    def terminate(self): self.send_signal(signal.SIGTERM)
    def kill(self): self.send_signal(signal.SIGKILL)

    def cmd_str(self): return " ".join([str(s) for s in self.cmd])

def checkenv(name):
    value = os.getenv(name)
    if not value: raise Exception("Environment variable %s is not set" % name)
    return value

class Broker(Popen):
    "A broker process. Takes care of start, stop and logging."
    _broker_count = 0

    def find_log(self):
        self.log = "%s.log" % self.name
        i = 1
        while (os.path.exists(self.log)):
            self.log = "%s-%d.log" % (self.name, i)
            i += 1

    def __init__(self, test, args=[], name=None, expect=EXPECT_RUNNING, port=0):
        """Start a broker daemon. name determines the data-dir and log
        file names."""

        self.test = test
        self._port=port
        cmd = [BrokerTest.qpidd_exec, "--port", port, "--no-module-dir", "--auth=no"] + args
        if name: self.name = name
        else:
            self.name = "broker%d" % Broker._broker_count
            Broker._broker_count += 1
        self.find_log()
        cmd += ["--log-to-file", self.log, "--log-prefix", self.name]
        cmd += ["--log-to-stderr=no"] 
        self.datadir = self.name
        cmd += ["--data-dir", self.datadir]
        Popen.__init__(self, cmd, expect, drain=False)
        test.cleanup_stop(self)
        self._host = "127.0.0.1"
        log.debug("Started broker %s (%s, %s)" % (self.name, self.pname, self.log))
        self._log_ready = False

    def host(self): return self._host

    def port(self):
        # Read port from broker process stdout if not already read.
        if (self._port == 0):
            try: self._port = int(self.stdout.readline())
            except ValueError, e:
                raise Exception("Can't get port for broker %s (%s)%s" %
                                (self.name, self.pname, error_line(self.log)))
        return self._port

    def unexpected(self,msg):
        raise BadProcessStatus("%s: %s (%s)" % (msg, self.name, self.pname))

    def connect(self):
        """New API connection to the broker."""
        return messaging.Connection.open(self.host(), self.port())

    def connect_old(self):
        """Old API connection to the broker."""
        socket = qpid.util.connect(self.host(),self.port())
        connection = qpid.connection.Connection (sock=socket)
        connection.start()
        return connection;

    def declare_queue(self, queue):
        c = self.connect_old()
        s = c.session(str(qpid.datatypes.uuid4()))
        s.queue_declare(queue=queue)
        c.close()
    
    def _prep_sender(self, queue, durable, xprops):
        s = queue + "; {create:always, node:{durable:" + str(durable)
        if xprops != None: s += ", x-declare:{" + xprops + "}"
        return s + "}}"

    def send_message(self, queue, message, durable=True, xprops=None, session=None):
        if session == None:
            s = self.connect().session()
        else:
            s = session
        s.sender(self._prep_sender(queue, durable, xprops)).send(message)
        if session == None:
            s.connection.close()

    def send_messages(self, queue, messages, durable=True, xprops=None, session=None):
        if session == None:
            s = self.connect().session()
        else:
            s = session
        sender = s.sender(self._prep_sender(queue, durable, xprops))
        for m in messages: sender.send(m)
        if session == None:
            s.connection.close()

    def get_message(self, queue):
        s = self.connect().session()
        m = s.receiver(queue+"; {create:always}", capacity=1).fetch(timeout=1)
        s.acknowledge()
        s.connection.close()
        return m

    def get_messages(self, queue, n):
        s = self.connect().session()
        receiver = s.receiver(queue+"; {create:always}", capacity=n)
        m = [receiver.fetch(timeout=1) for i in range(n)]
        s.acknowledge()
        s.connection.close()
        return m

    def host_port(self): return "%s:%s" % (self.host(), self.port())

    def log_ready(self):
        """Return true if the log file exists and contains a broker ready message"""
        if self._log_ready: return True
        if not os.path.exists(self.log): return False
        f = open(self.log)
        try:
            for l in f:
                if "notice Broker running" in l:
                    self._log_ready = True
                    return True
            return False
        finally: f.close()

    def ready(self):
        """Wait till broker is ready to serve clients"""
        # First make sure the broker is listening by checking the log.
        if not retry(self.log_ready):
            raise Exception("Timed out waiting for broker %s" % self.name)
        # Make a connection, this will wait for extended cluster init to finish.
        try: self.connect().close()
        except: raise RethrownException("Broker %s failed ready test"%self.name)

class Cluster:
    """A cluster of brokers in a test."""

    _cluster_count = 0

    def __init__(self, test, count=0, args=[], expect=EXPECT_RUNNING, wait=True):
        self.test = test
        self._brokers=[]
        self.name = "cluster%d" % Cluster._cluster_count
        Cluster._cluster_count += 1
        # Use unique cluster name
        self.args = copy(args)
        self.args += [ "--cluster-name", "%s-%s:%d" % (self.name, socket.gethostname(), os.getpid()) ]
        self.args += [ "--log-enable=info+", "--log-enable=debug+:cluster"]
        assert BrokerTest.cluster_lib
        self.args += [ "--load-module", BrokerTest.cluster_lib ]
        self.start_n(count, expect=expect, wait=wait)

    def start(self, name=None, expect=EXPECT_RUNNING, wait=True, args=[], port=0):
        """Add a broker to the cluster. Returns the index of the new broker."""
        if not name: name="%s-%d" % (self.name, len(self._brokers))
        self._brokers.append(self.test.broker(self.args+args, name, expect, wait, port=port))
        return self._brokers[-1]

    def start_n(self, count, expect=EXPECT_RUNNING, wait=True, args=[]):
        for i in range(count): self.start(expect=expect, wait=wait, args=args)

    # Behave like a list of brokers.
    def __len__(self): return len(self._brokers)
    def __getitem__(self,index): return self._brokers[index]
    def __iter__(self): return self._brokers.__iter__()

class BrokerTest(TestCase):
    """
    Tracks processes started by test and kills at end of test.
    Provides a well-known working directory for each test.
    """

    # Environment settings.
    qpidd_exec = checkenv("QPIDD_EXEC")
    cluster_lib = os.getenv("CLUSTER_LIB")
    xml_lib = os.getenv("XML_LIB")
    qpid_config_exec = os.getenv("QPID_CONFIG_EXEC")
    qpid_route_exec = os.getenv("QPID_ROUTE_EXEC")
    receiver_exec = os.getenv("RECEIVER_EXEC")
    sender_exec = os.getenv("SENDER_EXEC")
    store_lib = os.getenv("STORE_LIB")
    test_store_lib = os.getenv("TEST_STORE_LIB")
    rootdir = os.getcwd()

    def configure(self, config): self.config=config
    
    def setUp(self):
        outdir = self.config.defines.get("OUTDIR") or "brokertest.tmp"
        self.dir = os.path.join(self.rootdir, outdir, self.id())
        os.makedirs(self.dir)
        os.chdir(self.dir)
        self.stopem = []                # things to stop at end of test

    def tearDown(self):
        err = []
        for p in self.stopem:
            try: p.stop()
            except Exception, e: err.append(str(e))

        if err: raise Exception("Unexpected process status:\n    "+"\n    ".join(err))

    def cleanup_stop(self, stopable):
        """Call thing.stop at end of test"""
        self.stopem.append(stopable)

    def popen(self, cmd, expect=EXPECT_EXIT_OK, drain=True):
        """Start a process that will be killed at end of test, in the test dir."""
        os.chdir(self.dir)
        p = Popen(cmd, expect, drain)
        self.cleanup_stop(p)
        return p

    def broker(self, args=[], name=None, expect=EXPECT_RUNNING,wait=True,port=0):
        """Create and return a broker ready for use"""
        b = Broker(self, args=args, name=name, expect=expect, port=port)
        if (wait):
            try: b.ready()
            except Exception, e:
                raise Exception("Failed to start broker %s(%s): %s" % (b.name, b.log, e))
        return b

    def cluster(self, count=0, args=[], expect=EXPECT_RUNNING, wait=True):
        """Create and return a cluster ready for use"""
        cluster = Cluster(self, count, args, expect=expect, wait=wait)
        return cluster

    def wait():
        """Wait for all brokers in the cluster to be ready"""
        for b in _brokers: b.connect().close()

class RethrownException(Exception):
    """Captures the stack trace of the current exception to be thrown later"""
    def __init__(self, msg=""):
        Exception.__init__(self, msg+"\n"+format_exc())

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
    
class NumberedSender(Thread):
    """
    Thread to run a sender client and send numbered messages until stopped.
    """

    def __init__(self, broker, max_depth=None):
        """
        max_depth: enable flow control, ensure sent - received <= max_depth.
        Requires self.received(n) to be called each time messages are received.
        """
        Thread.__init__(self)
        self.sender = broker.test.popen(
            [broker.test.sender_exec, "--port", broker.port()], expect=EXPECT_RUNNING)
        self.condition = Condition()
        self.max = max_depth
        self.received = 0
        self.stopped = False
        self.error = None

    def run(self):
        try:
            self.sent = 0
            while not self.stopped:
                if self.max:
                    self.condition.acquire()
                    while not self.stopped and self.sent - self.received > self.max:
                        self.condition.wait()
                    self.condition.release()
                self.sender.stdin.write(str(self.sent)+"\n")
                self.sender.stdin.flush()
                self.sent += 1
        except Exception: self.error = RethrownException(self.sender.pname)

    def notify_received(self, count):
        """Called by receiver to enable flow control. count = messages received so far."""
        self.condition.acquire()
        self.received = count
        self.condition.notify()
        self.condition.release()

    def stop(self):
        self.condition.acquire()
        self.stopped = True
        self.condition.notify()
        self.condition.release()
        self.join()
        if self.error: raise self.error
        
class NumberedReceiver(Thread):
    """
    Thread to run a receiver client and verify it receives
    sequentially numbered messages.
    """
    def __init__(self, broker, sender = None):
        """
        sender: enable flow control. Call sender.received(n) for each message received.
        """
        Thread.__init__(self)
        self.test = broker.test
        self.receiver = self.test.popen(
            [self.test.receiver_exec, "--port", broker.port()],
            expect=EXPECT_RUNNING, drain=False)
        self.stopat = None
        self.lock = Lock()
        self.error = None
        self.sender = sender

    def continue_test(self):
        self.lock.acquire()
        ret = self.stopat is None or self.received < self.stopat
        self.lock.release()
        return ret
    
    def run(self):
        try:
            self.received = 0
            while self.continue_test():
                m = int(self.receiver.stdout.readline())
                assert(m <= self.received) # Allow for duplicates
                if (m == self.received):
                    self.received += 1
                    if self.sender:
                        self.sender.notify_received(self.received)
        except Exception:
            self.error = RethrownException(self.receiver.pname)

    def stop(self, count):
        """Returns when received >= count"""
        self.lock.acquire()
        self.stopat = count
        self.lock.release()
        self.join()
        if self.error: raise self.error

class ErrorGenerator(StoppableThread):
    """
    Thread that continuously generates errors by trying to consume from
    a non-existent queue. For cluster regression tests, error handling
    caused issues in the past.
    """

    def __init__(self, broker):
        StoppableThread.__init__(self)
        self.broker=broker
        broker.test.cleanup_stop(self)
        self.start()
        
    def run(self):
        c = self.broker.connect_old()
        try:
            while not self.stopped:
                try:
                    c.session(str(qpid.datatypes.uuid4())).message_subscribe(
                        queue="non-existent-queue")
                    assert(False)
                except qpid.session.SessionException: pass
        except: pass                    # Normal if broker is killed.

def import_script(path):
    """
    Import executable script at path as a module.
    Requires some trickery as scripts are not in standard module format
    """
    f = open(path)
    try:
        name=os.path.split(path)[1].replace("-","_")
        return imp.load_module(name, f, path, ("", "r", imp.PY_SOURCE))
    finally: f.close()
