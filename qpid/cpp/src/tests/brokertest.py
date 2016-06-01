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

# Support library for tests that start multiple brokers, e.g. HA or federation

import os, signal, string, tempfile, subprocess, socket, threading, time, imp, re
import qpid, traceback, signal
from qpid import connection, util
from qpid.compat import format_exc
from unittest import TestCase
from copy import copy
from threading import Thread, Lock, Condition
from logging import getLogger
from qpidtoollibs import BrokerAgent

# NOTE: Always import native client qpid.messaging, import swigged client
# qpid_messaging if possible. qpid_messaing is set to None if not available.
#
# qm is set to qpid_messaging if it is available, qpid.messaging if not.
# Use qm.X to specify names from the default messaging module.
#
# Set environment variable QPID_PY_NO_SWIG=1 to prevent qpid_messaging from loading.
#
# BrokerTest can be configured to determine which protocol is used by default:
#
# -DPROTOCOL="amqpX": Use protocol "amqpX". Defaults to amqp1.0 if available.
#
# The configured defaults can be over-ridden on BrokerTest.connect and some
# other methods by specifying native=True|False and protocol="amqpX"
#

import qpid.messaging
qm = qpid.messaging
qpid_messaging = None

def env_has_log_config():
    """True if there are qpid log configuratoin settings in the environment."""
    return "QPID_LOG_ENABLE" in os.environ or "QPID_TRACE" in os.environ

if not os.environ.get("QPID_PY_NO_SWIG"):
    try:
        import qpid_messaging
        from qpid.datatypes import uuid4
        qm = qpid_messaging
        # Silence warnings from swigged messaging library unless enabled in environment.
        if not env_has_log_config():
            qm.Logger.configure(["--log-enable=error"])
    except ImportError:
        print "Cannot load python SWIG bindings, falling back to native qpid.messaging."

log = getLogger("brokertest")

# Values for expected outcome of process at end of test
EXPECT_EXIT_OK=1           # Expect to exit with 0 status before end of test.
EXPECT_EXIT_FAIL=2         # Expect to exit with non-0 status before end of test.
EXPECT_RUNNING=3           # Expect to still be running at end of test
EXPECT_UNKNOWN=4            # No expectation, don't check exit status.

def find_exe(program):
    """Find an executable in the system PATH"""
    def is_exe(fpath):
        return os.path.isfile(fpath) and os.access(fpath, os.X_OK)
    mydir, name = os.path.split(program)
    if mydir:
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

def error_line(filename, n=1):
    """Get the last n line(s) of filename for error messages"""
    result = []
    try:
        f = open(filename)
        try:
            for l in f:
                if len(result) == n:  result.pop(0)
                result.append("    "+l)
        finally:
            f.close()
    except: return ""
    return ":\n" + "".join(result)

def retry(function, timeout=10, delay=.001, max_delay=1):
    """Call function until it returns a true value or timeout expires.
    Double the delay for each retry up to max_delay.
    Returns what function returns if true, None if timeout expires."""
    deadline = time.time() + timeout
    ret = None
    while True:
        ret = function()
        if ret: return ret
        remaining = deadline - time.time()
        if remaining <= 0: return False
        delay = min(delay, remaining)
        time.sleep(delay)
        delay = min(delay*2, max_delay)

class AtomicCounter:
    def __init__(self):
        self.count = 0
        self.lock = Lock()

    def next(self):
        self.lock.acquire();
        ret = self.count
        self.count += 1
        self.lock.release();
        return ret

_popen_id = AtomicCounter() # Popen identifier for use in output file names.

# Constants for file descriptor arguments to Popen
FILE = "FILE"                       # Write to file named after process
from subprocess import PIPE, STDOUT

class Popen(subprocess.Popen):
    """
    Can set and verify expectation of process status at end of test.
    Dumps command line, stdout, stderr to data dir for debugging.
    """

    def __init__(self, cmd, expect=EXPECT_EXIT_OK, stdin=None, stdout=FILE, stderr=FILE):
        """Run cmd (should be a list of program and arguments)
        expect - if set verify expectation at end of test.
        stdout, stderr - can have the same values as for subprocess.Popen as well as
          FILE (the default) which means write to a file named after the process.
        stdin - like subprocess.Popen but defauts to PIPE
        """
        self._clean = False
        self._clean_lock = Lock()
        if type(cmd) is type(""): cmd = [cmd] # Make it a list.
        self.cmd  = [ str(x) for x in cmd ]
        self.expect = expect
        self.id = _popen_id.next()
        self.pname = "%s-%d" % (os.path.split(self.cmd[0])[1], self.id)
        if stdout == FILE: stdout = open(self.outfile("out"), "w")
        if stderr == FILE: stderr = open(self.outfile("err"), "w")
        subprocess.Popen.__init__(self, self.cmd, bufsize=0, executable=None,
                                  stdin=stdin, stdout=stdout, stderr=stderr)
        f = open(self.outfile("cmd"), "w")
        try: f.write("%s\n%d"%(self.cmd_str(), self.pid))
        finally: f.close()
        log.debug("Started process %s: %s" % (self.pname, " ".join(self.cmd)))

    def __repr__(self): return "Popen<%s>"%(self.pname)

    def outfile(self, ext): return "%s.%s" % (self.pname, ext)

    def unexpected(self,msg):
        err = error_line(self.outfile("err")) or error_line(self.outfile("out"))
        raise BadProcessStatus("%s %s%s" % (self.pname, msg, err))

    def teardown(self):         # Clean up at end of test.
        if self.expect == EXPECT_UNKNOWN:
            try: self.kill()            # Just make sure its dead
            except: pass
        elif self.expect == EXPECT_RUNNING:
                if self.poll() != None:
                    self.unexpected("expected running, exit code %d" % self.returncode)
                else:
                    try:
                        self.kill()
                    except Exception,e:
                        self.unexpected("exception from kill: %s" % str(e))
        else:
            retry(lambda: self.poll() is not None)
            if self.returncode is None: # Still haven't stopped
                self.kill()
                self.unexpected("still running")
            elif self.expect == EXPECT_EXIT_OK and self.returncode != 0:
                self.unexpected("exit code %d" % self.returncode)
            elif self.expect == EXPECT_EXIT_FAIL and self.returncode == 0:
                self.unexpected("expected error")
        self.wait()


    def communicate(self, input=None):
        ret = subprocess.Popen.communicate(self, input)
        self._cleanup()
        return ret

    def is_running(self): return self.poll() is None

    def assert_running(self):
        if not self.is_running(): self.unexpected("Exit code %d" % self.returncode)

    def wait(self):
        ret = subprocess.Popen.wait(self)
        self._cleanup()
        return ret

    def assert_exit_ok(self):
        if self.wait() != 0: self.unexpected("Exit code %d" % self.returncode)

    def terminate(self):
        try: subprocess.Popen.terminate(self)
        except AttributeError:          # No terminate method
            try:
                os.kill( self.pid , signal.SIGTERM)
            except AttributeError: # no os.kill, using taskkill.. (Windows only)
                os.popen('TASKKILL /PID ' +str(self.pid) + ' /F')
        self.wait()

    def kill(self):
        # Set to EXPECT_UNKNOWN, EXPECT_EXIT_FAIL creates a race condition
        # if the process exits normally concurrent with the call to kill.
        self.expect = EXPECT_UNKNOWN
        try: subprocess.Popen.kill(self)
        except AttributeError:          # No terminate method
            try:
                os.kill( self.pid , signal.SIGKILL)
            except AttributeError: # no os.kill, using taskkill.. (Windows only)
                os.popen('TASKKILL /PID ' +str(self.pid) + ' /F')
        self.wait()

    def _cleanup(self):
        """Clean up after a dead process"""
        self._clean_lock.acquire()
        if not self._clean:
            self._clean = True
            try: self.stdin.close()
            except: pass
            try: self.stdout.close()
            except: pass
            try: self.stderr.close()
            except: pass
        self._clean_lock.release()

    def cmd_str(self): return " ".join([str(s) for s in self.cmd])


def checkenv(name):
    value = os.getenv(name)
    if not value: raise Exception("Environment variable %s is not set" % name)
    return value

def find_in_file(str, filename):
    if not os.path.exists(filename): return False
    f = open(filename)
    try: return str in f.read()
    finally: f.close()

class Broker(Popen):
    "A broker process. Takes care of start, stop and logging."
    _broker_count = 0
    _log_count = 0

    def __repr__(self): return "<Broker:%s:%d>"%(self.log, self.port())

    def get_log(self):
        return os.path.abspath(self.log)

    def __init__(self, test, args=[], test_store=False, name=None, expect=EXPECT_RUNNING, port=0, wait=None, show_cmd=False):
        """Start a broker daemon. name determines the data-dir and log
        file names."""

        self.test = test
        self._port=port
        args = copy(args)
        if BrokerTest.amqp_lib: args += ["--load-module", BrokerTest.amqp_lib]
        if BrokerTest.store_lib and not test_store:
            args += ['--load-module', BrokerTest.store_lib]
            if BrokerTest.sql_store_lib:
                args += ['--load-module', BrokerTest.sql_store_lib]
                args += ['--catalog', BrokerTest.sql_catalog]
            if BrokerTest.sql_clfs_store_lib:
                args += ['--load-module', BrokerTest.sql_clfs_store_lib]
                args += ['--catalog', BrokerTest.sql_catalog]
        cmd = [BrokerTest.qpidd_exec, "--port", port, "--interface", "127.0.0.1", "--no-module-dir"] + args
        if not "--auth" in args: cmd.append("--auth=no")
        if wait != None:
            cmd += ["--wait", str(wait)]
        if name: self.name = name
        else:
            self.name = "broker%d" % Broker._broker_count
            Broker._broker_count += 1

        self.log = "%03d:%s.log" % (Broker._log_count, self.name)
        self.store_log = "%03d:%s.store.log" % (Broker._log_count, self.name)
        Broker._log_count += 1

        cmd += ["--log-to-file", self.log]
        cmd += ["--log-to-stderr=no"]

        # Add default --log-enable arguments unless args already has --log arguments.
        if not env_has_log_config() and not [l for l in args if l.startswith("--log")]:
            args += ["--log-enable=info+"]

        if test_store: cmd += ["--load-module", BrokerTest.test_store_lib,
                               "--test-store-events", self.store_log]

        self.datadir = os.path.abspath(self.name)
        cmd += ["--data-dir", self.datadir]
        if show_cmd: print cmd
        Popen.__init__(self, cmd, expect, stdout=PIPE)
        test.teardown_add(self)
        self._host = "127.0.0.1"
        self._agent = None

        log.debug("Started broker %s" % self)

    def host(self): return self._host

    def port(self):
        # Read port from broker process stdout if not already read.
        if (self._port == 0):
            try: self._port = int(self.stdout.readline())
            except ValueError, e:
                raise Exception("Can't get port for broker %s (%s)%s: %s" %
                                (self.name, self.pname, error_line(self.log,5), e))
        return self._port

    def unexpected(self,msg):
        raise BadProcessStatus("%s: %s (%s)" % (msg, self.name, self.pname))

    def connect(self, timeout=5, native=False, **kwargs):
        """New API connection to the broker.
        @param native if True force use of the native qpid.messaging client
        even if swig client is available.
        """
        if native: connection_class = qpid.messaging.Connection
        else:
          connection_class = qm.Connection
          if (self.test.protocol and qm == qpid_messaging):
            kwargs.setdefault("protocol", self.test.protocol)
        return connection_class.establish(self.host_port(), timeout=timeout, **kwargs)
    
    @property
    def agent(self, **kwargs):
        """Return a BrokerAgent for this broker"""
        if not self._agent: self._agent = BrokerAgent(self.connect(**kwargs))
        return self._agent


    def declare_queue(self, queue):
        self.agent.addQueue(queue)

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

    def ready(self, timeout=10, **kwargs):
        """Wait till broker is ready to serve clients"""
        deadline = time.time()+timeout
        while True:
            try:
                c = self.connect(timeout=timeout, **kwargs)
                try:
                    c.session()
                    return      # All good
                finally: c.close()
            except Exception,e: # Retry up to timeout
                if time.time() > deadline:
                    raise RethrownException(
                        "Broker %s not responding: (%s)%s"%(
                            self.name,e,error_line(self.log, 5)))

    def assert_log_clean(self, ignore=None):
        log = open(self.get_log())
        try:
            error = re.compile("] error|] critical")
            if ignore: ignore = re.compile(ignore)
            else: ignore = re.compile("\000") # Won't match anything
            for line in log.readlines():
                assert not error.search(line) or ignore.search(line), "Errors in log file %s: %s"%(log, line)
        finally: log.close()

def receiver_iter(receiver, timeout=0):
    """Make an iterator out of a receiver. Returns messages till Empty is raised."""
    try:
        while True:
            yield receiver.fetch(timeout=timeout)
    except qm.Empty:
        pass

def browse(session, queue, timeout=0, transform=lambda m: m.content):
    """Return a list with the contents of each message on queue."""
    r = session.receiver("%s;{mode:browse}"%(queue))
    r.capacity = 100
    try:
        return [transform(m) for m in receiver_iter(r, timeout)]
    finally:
        r.close()

def assert_browse(session, queue, expect_contents, timeout=0, transform=lambda m: m.content, msg=None):
    """Assert that the contents of messages on queue (as retrieved
    using session and timeout) exactly match the strings in
    expect_contents"""
    if msg is None: msg = "browse '%s' failed" % queue
    actual_contents = browse(session, queue, timeout, transform=transform)
    if msg: msg = "%s: %r != %r"%(msg, expect_contents, actual_contents)
    assert expect_contents == actual_contents, msg

def assert_browse_retry(session, queue, expect_contents, timeout=1, delay=.001, transform=lambda m:m.content, msg="browse failed"):
    """Wait up to timeout for contents of queue to match expect_contents"""
    test = lambda: browse(session, queue, 0, transform=transform) == expect_contents
    retry(test, timeout, delay)
    actual_contents = browse(session, queue, 0, transform=transform)
    if msg: msg = "%s: %r != %r"%(msg, expect_contents, actual_contents)
    assert expect_contents == actual_contents, msg

class BrokerTest(TestCase):
    """
    Tracks processes started by test and kills at end of test.
    Provides a well-known working directory for each test.
    """

    def __init__(self, *args, **kwargs):
        self.longMessage = True # Enable long messages for assert*(..., msg=xxx)
        TestCase.__init__(self, *args, **kwargs)

    # Environment settings.
    qpidd_exec = "qpidd"
    ha_lib = os.getenv("HA_LIB")
    xml_lib = os.getenv("XML_LIB")
    amqp_lib = os.getenv("AMQP_LIB")
    qpid_config_exec = "qpid-config"
    qpid_route_exec = "qpid-route"
    receiver_exec = "receiver"
    sender_exec = "sender"
    sql_store_lib = os.getenv("STORE_SQL_LIB")
    sql_clfs_store_lib = os.getenv("STORE_SQL_CLFS_LIB")
    sql_catalog = os.getenv("STORE_CATALOG")
    store_lib = os.getenv("STORE_LIB")
    test_store_lib = os.getenv("TEST_STORE_LIB")
    rootdir = os.getcwd()

    try:
        import proton
        PN_VERSION = (proton.VERSION_MAJOR, proton.VERSION_MINOR)
    except ImportError:
        # proton not on path, can't determine version
        PN_VERSION = (0, 0)
    except AttributeError:
        # prior to 0.8 proton did not expose version info
        PN_VERSION = (0, 7)

    PN_TX_VERSION = (0, 9)

    amqp_tx_supported = PN_VERSION >= PN_TX_VERSION
    
    def configure(self, config): self.config=config

    def setUp(self):
        defs = self.config.defines
        outdir = defs.get("OUTDIR") or "brokertest.tmp"
        self.dir = os.path.join(self.rootdir, outdir, self.id())
        os.makedirs(self.dir)
        os.chdir(self.dir)
        self.teardown_list = []                # things to tear down at end of test
        if qpid_messaging and self.amqp_lib: default_protocol="amqp1.0"
        else: default_protocol="amqp0-10"
        self.protocol = defs.get("PROTOCOL") or default_protocol
        self.tx_protocol = self.protocol
        if not self.amqp_tx_supported: self.tx_protocol = "amqp0-10"

    def tearDown(self):
        err = []
        self.teardown_list.reverse() # Tear down in reverse order
        for p in self.teardown_list:
            log.debug("Tearing down %s", p)
            try:
                # Call the first of the methods that is available on p.
                for m in ["teardown", "close"]:
                    a = getattr(p, m, None)
                    if a: a(); break
                else: raise Exception("Don't know how to tear down %s", p)
            except Exception, e:
                if m != "close": # Ignore connection close errors.
                    err.append("%s: %s"%(e.__class__.__name__, str(e)))
        self.teardown_list = []                # reset in case more processes start
        os.chdir(self.rootdir)
        if err: raise Exception("Unexpected process status:\n    "+"\n    ".join(err))

    def teardown_add(self, thing):
        """Call thing.teardown() or thing.close() at end of test"""
        self.teardown_list.append(thing)

    def popen(self, cmd, expect=EXPECT_EXIT_OK, stdin=None, stdout=FILE, stderr=FILE):
        """Start a process that will be killed at end of test, in the test dir."""
        os.chdir(self.dir)
        p = Popen(cmd, expect, stdin=stdin, stdout=stdout, stderr=stderr)
        self.teardown_add(p)
        return p

    def broker(self, args=[], name=None, expect=EXPECT_RUNNING, wait=True, port=0, show_cmd=False, **kw):
        """Create and return a broker ready for use"""
        b = Broker(self, args=args, name=name, expect=expect, port=port, show_cmd=show_cmd, **kw)
        if (wait):
            try: b.ready()
            except Exception, e:
                raise RethrownException("Failed to start broker %s(%s): %s" % (b.name, b.log, e))
        return b

    def check_output(self, args, stdin=None):
        p = self.popen(args, stdout=PIPE, stderr=STDOUT)
        out = p.communicate(stdin)
        if p.returncode != 0:
            raise Exception("%s exit code %s, output:\n%s" % (args, p.returncode, out[0]))
        return out[0]

    def browse(self, *args, **kwargs): browse(*args, **kwargs)
    def assert_browse(self, *args, **kwargs): assert_browse(*args, **kwargs)
    def assert_browse_retry(self, *args, **kwargs): assert_browse_retry(*args, **kwargs)

    def protocol_option(self, connection_options=""):
        if "protocol" in connection_options: return connection_options
        else: return ",".join(filter(None, [connection_options,"protocol:'%s'"%self.protocol]))


def join(thread, timeout=30):
    thread.join(timeout)
    if thread.isAlive(): raise Exception("Timed out joining thread %s"%thread)

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
        join(self)
        if self.error: raise self.error

# Options for a client that wants to reconnect automatically.
RECONNECT_OPTIONS="reconnect:true,reconnect-timeout:10,reconnect-urls-replace:true"

class NumberedSender(Thread):
    """
    Thread to run a sender client and send numbered messages until stopped.
    """

    def __init__(self, broker, max_depth=None, queue="test-queue",
                 connection_options=RECONNECT_OPTIONS,
                 failover_updates=False, url=None, args=[]):
        """
        max_depth: enable flow control, ensure sent - received <= max_depth.
        Requires self.notify_received(n) to be called each time messages are received.
        """
        Thread.__init__(self)
        cmd = ["qpid-send",
               "--broker", url or broker.host_port(),
               "--address", "%s;{create:always}"%queue,
               "--connection-options", "{%s}"%(broker.test.protocol_option(connection_options)),
               "--content-stdin"
               ] + args
        if failover_updates: cmd += ["--failover-updates"]
        self.sender = broker.test.popen(
            cmd, expect=EXPECT_RUNNING, stdin=PIPE)
        self.condition = Condition()
        self.max = max_depth
        self.received = 0
        self.stopped = False
        self.error = None
        self.queue = queue

    def write_message(self, n):
        self.sender.stdin.write(str(n)+"\n")
        self.sender.stdin.flush()

    def run(self):
        try:
            self.sent = 0
            while not self.stopped:
                self.sender.assert_running()
                if self.max:
                    self.condition.acquire()
                    while not self.stopped and self.sent - self.received > self.max:
                        self.condition.wait()
                    self.condition.release()
                self.write_message(self.sent)
                self.sent += 1
        except Exception, e:
            self.error = RethrownException(
                "%s: (%s)%s"%(self.sender.pname,e,
                              error_line(self.sender.outfile("err"))))


    def notify_received(self, count):
        """Called by receiver to enable flow control. count = messages received so far."""
        self.condition.acquire()
        self.received = count
        self.condition.notify()
        self.condition.release()

    def stop(self):
        self.condition.acquire()
        try:
            self.stopped = True
            self.condition.notify()
        finally: self.condition.release()
        join(self)
        self.write_message(-1)          # end-of-messages marker.
        if self.error: raise self.error

class NumberedReceiver(Thread):
    """
    Thread to run a receiver client and verify it receives
    sequentially numbered messages.
    """
    def __init__(self, broker, sender=None, queue="test-queue",
                 connection_options=RECONNECT_OPTIONS,
                 failover_updates=False, url=None, args=[]):
        """
        sender: enable flow control. Call sender.received(n) for each message received.
        """
        Thread.__init__(self)
        self.test = broker.test
        cmd = ["qpid-receive",
               "--broker", url or broker.host_port(),
               "--address", "%s;{create:always}"%queue,
               "--connection-options", "{%s}"%(broker.test.protocol_option(connection_options)),
               "--forever"
               ]
        if failover_updates: cmd += [ "--failover-updates" ]
        cmd += args
        self.receiver = self.test.popen(
            cmd, expect=EXPECT_RUNNING, stdout=PIPE)
        self.lock = Lock()
        self.error = None
        self.sender = sender
        self.received = 0
        self.queue = queue

    def read_message(self):
        n = int(self.receiver.stdout.readline())
        return n

    def run(self):
        try:
            m = self.read_message()
            while m != -1:
                self.receiver.assert_running()
                assert m <= self.received, "%s missing message %s>%s"%(self.queue, m, self.received)
                if (m == self.received): # Ignore duplicates
                    self.received += 1
                    if self.sender:
                        self.sender.notify_received(self.received)
                m = self.read_message()
        except Exception, e:
            self.error = RethrownException(
                "%s: (%s)%s"%(self.receiver.pname,e,
                              error_line(self.receiver.outfile("err"))))

    def check(self):
        """Raise an exception if there has been an error"""
        if self.error: raise self.error

    def stop(self):
        """Returns when termination message is received"""
        join(self)
        self.check()

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
