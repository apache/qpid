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

#
# Support library for qpid python tests.
#

import sys, re, unittest, os, random, logging, traceback
import qpid.client, qpid.spec, qmf.console
import Queue
from fnmatch import fnmatch
from getopt import getopt, GetoptError
from qpid.content import Content
from qpid.message import Message

#0-10 support
from qpid.connection import Connection
from qpid.spec010 import load
from qpid.util import connect, ssl, URL

def findmodules(root):
    """Find potential python modules under directory root"""
    found = []
    for dirpath, subdirs, files in os.walk(root):
        modpath = dirpath.replace(os.sep, '.')
        if not re.match(r'\.svn$', dirpath): # Avoid SVN directories
            for f in files:
                match = re.match(r'(.+)\.py$', f)
                if match and f != '__init__.py':
                    found.append('.'.join([modpath, match.group(1)]))
    return found

def default(value, default):
    if (value == None): return default
    else: return value

class TestRunner:

    SPEC_FOLDER = "../specs"

    """Runs unit tests.

    Parses command line arguments, provides utility functions for tests,
    runs the selected test suite.
    """

    def _die(self, message = None):
        if message: print message
        print     """
run-tests [options] [test*]
The name of a test is package.module.ClassName.testMethod
Options:
  -?/-h/--help             : this message
  -s/--spec <spec.xml> : URL of AMQP XML specification or one of these abbreviations:
                           0-8 - use the default 0-8 specification.
                           0-9 - use the default 0-9 specification.
                           0-10-errata - use the 0-10 specification with qpid errata.
  -e/--errata <errata.xml> : file containing amqp XML errata
  -b/--broker [amqps://][<user>[/<password>]@]<host>[:<port>] : broker to connect to
  -v/--verbose             : verbose - lists tests as they are run.
  -d/--debug               : enable debug logging.
  -i/--ignore <test>       : ignore the named test.
  -I/--ignore-file         : file containing patterns to ignore.
  -S/--skip-self-test      : skips the client self tests in the 'tests folder'
  -F/--spec-folder         : folder that contains the specs to be loaded
  """
        sys.exit(1)

    def setBroker(self, broker):
        try:
            self.url = URL(broker)
        except ValueError:
            self._die("'%s' is not a valid broker" % (broker))
        self.user = default(self.url.user, "guest")
        self.password = default(self.url.password, "guest")
        self.host = self.url.host
        if self.url.scheme == URL.AMQPS:
            self.ssl = True
            default_port = 5671
        else:
            self.ssl = False
            default_port = 5672
        self.port = default(self.url.port, default_port)

    def ignoreFile(self, filename):
        f = file(filename)
        for line in f.readlines(): self.ignore.append(line.strip())
        f.close()

    def use08spec(self):
        "True if we are running with the old 0-8 spec."
        # NB: AMQP 0-8 identifies itself as 8-0 for historical reasons.
        return self.spec.major==8 and self.spec.minor==0

    def use09spec(self):
        "True if we are running with the 0-9 (non-wip) spec."
        return self.spec.major==0 and self.spec.minor==9

    def _parseargs(self, args):
        # Defaults
        self.setBroker("localhost")
        self.verbose = 1
        self.ignore = []
        self.specfile = "0-8"
        self.errata = []
        self.skip_self_test = False

        try:
            opts, self.tests = getopt(args, "s:e:b:h?dvSi:I:F:",
                                      ["help", "spec", "errata=", "broker=",
                                       "verbose", "skip-self-test", "ignore",
                                       "ignore-file", "spec-folder"])
        except GetoptError, e:
            self._die(str(e))
        for opt, value in opts:
            if opt in ("-?", "-h", "--help"): self._die()
            if opt in ("-s", "--spec"): self.specfile = value
            if opt in ("-e", "--errata"): self.errata.append(value)
            if opt in ("-b", "--broker"): self.setBroker(value)
            if opt in ("-v", "--verbose"): self.verbose = 2
            if opt in ("-d", "--debug"): logging.basicConfig(level=logging.DEBUG)
            if opt in ("-i", "--ignore"): self.ignore.append(value)
            if opt in ("-I", "--ignore-file"): self.ignoreFile(value)
            if opt in ("-S", "--skip-self-test"): self.skip_self_test = True
            if opt in ("-F", "--spec-folder"): TestRunner.SPEC_FOLDER = value

	# Abbreviations for default settings.
        if (self.specfile == "0-10"):
            self.spec = load(self.get_spec_file("amqp.0-10.xml"))
        elif (self.specfile == "0-10-errata"):
            self.spec = load(self.get_spec_file("amqp.0-10-qpid-errata.xml"))
        else:    
            if (self.specfile == "0-8"):
                self.specfile = self.get_spec_file("amqp.0-8.xml")
            elif (self.specfile == "0-9"):
                self.specfile = self.get_spec_file("amqp.0-9.xml")
                self.errata.append(self.get_spec_file("amqp-errata.0-9.xml"))
                
                if (self.specfile == None):
                    self._die("No XML specification provided")
                    print "Using specification from:", self.specfile

            self.spec = qpid.spec.load(self.specfile, *self.errata)

        if len(self.tests) == 0:
            if not self.skip_self_test:
                self.tests=findmodules("tests")
            if self.use08spec() or self.use09spec():
                self.tests+=findmodules("tests_0-8")
            elif (self.spec.major == 99 and self.spec.minor == 0):
                self.tests+=findmodules("tests_0-10_preview")                
            elif (self.spec.major == 0 and self.spec.minor == 10):
                self.tests+=findmodules("tests_0-10")

    def testSuite(self):
        class IgnoringTestSuite(unittest.TestSuite):
            def addTest(self, test):
                if isinstance(test, unittest.TestCase):
                    for pattern in testrunner.ignore:
                        if fnmatch(test.id(), pattern):
                            return
                unittest.TestSuite.addTest(self, test)

        # Use our IgnoringTestSuite in the test loader.
        unittest.TestLoader.suiteClass = IgnoringTestSuite
        return unittest.defaultTestLoader.loadTestsFromNames(self.tests)

    def run(self, args=sys.argv[1:]):
        self._parseargs(args)
        runner = unittest.TextTestRunner(descriptions=False,
                                         verbosity=self.verbose)
        result = runner.run(self.testSuite())

        if (self.ignore):
            print "======================================="
            print "NOTE: the following tests were ignored:"
            for t in self.ignore: print t
            print "======================================="

        return result.wasSuccessful()

    def connect(self, host=None, port=None, spec=None, user=None, password=None, tune_params=None):
        """Connect to the broker, returns a qpid.client.Client"""
        host = host or self.host
        port = port or self.port
        spec = spec or self.spec
        user = user or self.user
        password = password or self.password
        client = qpid.client.Client(host, port, spec)
        if self.use08spec():         
            client.start({"LOGIN": user, "PASSWORD": password}, tune_params=tune_params)
        else:
            client.start("\x00" + user + "\x00" + password, mechanism="PLAIN", tune_params=tune_params)
        return client

    def get_spec_file(self, fname):
        return TestRunner.SPEC_FOLDER + os.sep + fname

# Global instance for tests to call connect.
testrunner = TestRunner()


class TestBase(unittest.TestCase):
    """Base class for Qpid test cases.

    self.client is automatically connected with channel 1 open before
    the test methods are run.

    Deletes queues and exchanges after.  Tests call
    self.queue_declare(channel, ...) and self.exchange_declare(chanel,
    ...) which are wrappers for the Channel functions that note
    resources to clean up later.
    """

    def setUp(self):
        self.queues = []
        self.exchanges = []
        self.client = self.connect()
        self.channel = self.client.channel(1)
        self.version = (self.client.spec.major, self.client.spec.minor)
        if self.version == (8, 0) or self.version == (0, 9):
            self.channel.channel_open()
        else:
            self.channel.session_open()

    def tearDown(self):
        try:
            for ch, q in self.queues:
                ch.queue_delete(queue=q)
            for ch, ex in self.exchanges:
                ch.exchange_delete(exchange=ex)
        except:
            print "Error on tearDown:"
            print traceback.print_exc()

        if not self.client.closed:
            self.client.channel(0).connection_close(reply_code=200)
        else:
            self.client.close()

    def connect(self, *args, **keys):
        """Create a new connction, return the Client object"""
        return testrunner.connect(*args, **keys)

    def queue_declare(self, channel=None, *args, **keys):
        channel = channel or self.channel
        reply = channel.queue_declare(*args, **keys)
        self.queues.append((channel, keys["queue"]))
        return reply

    def exchange_declare(self, channel=None, ticket=0, exchange='',
                         type='', passive=False, durable=False,
                         auto_delete=False,
                         arguments={}):
        channel = channel or self.channel
        reply = channel.exchange_declare(ticket=ticket, exchange=exchange, type=type, passive=passive,durable=durable, auto_delete=auto_delete, arguments=arguments)
        self.exchanges.append((channel,exchange))
        return reply

    def uniqueString(self):
        """Generate a unique string, unique for this TestBase instance"""
        if not "uniqueCounter" in dir(self): self.uniqueCounter = 1;
        return "Test Message " + str(self.uniqueCounter)

    def consume(self, queueName):
        """Consume from named queue returns the Queue object."""
        if testrunner.use08spec() or testrunner.use09spec():
            reply = self.channel.basic_consume(queue=queueName, no_ack=True)
            return self.client.queue(reply.consumer_tag)
        else:
            if not "uniqueTag" in dir(self): self.uniqueTag = 1
            else: self.uniqueTag += 1
            consumer_tag = "tag" + str(self.uniqueTag)
            self.channel.message_subscribe(queue=queueName, destination=consumer_tag)
            self.channel.message_flow(destination=consumer_tag, unit=0, value=0xFFFFFFFF)
            self.channel.message_flow(destination=consumer_tag, unit=1, value=0xFFFFFFFF)
            return self.client.queue(consumer_tag)

    def subscribe(self, channel=None, **keys):
        channel = channel or self.channel
        consumer_tag = keys["destination"]
        channel.message_subscribe(**keys)
        channel.message_flow(destination=consumer_tag, unit=0, value=0xFFFFFFFF)
        channel.message_flow(destination=consumer_tag, unit=1, value=0xFFFFFFFF)

    def assertEmpty(self, queue):
        """Assert that the queue is empty"""
        try:
            queue.get(timeout=1)
            self.fail("Queue is not empty.")
        except Queue.Empty: None              # Ignore

    def assertPublishGet(self, queue, exchange="", routing_key="", properties=None):
        """
        Publish to exchange and assert queue.get() returns the same message.
        """
        body = self.uniqueString()
        if testrunner.use08spec() or testrunner.use09spec():
            self.channel.basic_publish(
                exchange=exchange,
                content=Content(body, properties=properties),
                routing_key=routing_key)
        else:
            self.channel.message_transfer(
                destination=exchange,
                content=Content(body, properties={'application_headers':properties,'routing_key':routing_key}))
        msg = queue.get(timeout=1)
        if testrunner.use08spec() or testrunner.use09spec():
            self.assertEqual(body, msg.content.body)
            if (properties):
                self.assertEqual(properties, msg.content.properties)
        else:
            self.assertEqual(body, msg.content.body)
            if (properties):
                self.assertEqual(properties, msg.content['application_headers'])

    def assertPublishConsume(self, queue="", exchange="", routing_key="", properties=None):
        """
        Publish a message and consume it, assert it comes back intact.
        Return the Queue object used to consume.
        """
        self.assertPublishGet(self.consume(queue), exchange, routing_key, properties)

    def assertChannelException(self, expectedCode, message):
        if self.version == (8, 0) or self.version == (0, 9):
            if not isinstance(message, Message): self.fail("expected channel_close method, got %s" % (message))
            self.assertEqual("channel", message.method.klass.name)
            self.assertEqual("close", message.method.name)
        else:
            if not isinstance(message, Message): self.fail("expected session_closed method, got %s" % (message))
            self.assertEqual("session", message.method.klass.name)
            self.assertEqual("closed", message.method.name)
        self.assertEqual(expectedCode, message.reply_code)


    def assertConnectionException(self, expectedCode, message):
        if not isinstance(message, Message): self.fail("expected connection_close method, got %s" % (message))
        self.assertEqual("connection", message.method.klass.name)
        self.assertEqual("close", message.method.name)
        self.assertEqual(expectedCode, message.reply_code)

class TestBase010(unittest.TestCase):
    """
    Base class for Qpid test cases. using the final 0-10 spec
    """

    def setUp(self):
        self.conn = self.connect()
        self.session = self.conn.session("test-session", timeout=10)
        self.qmf = None

    def startQmf(self):
        self.qmf = qmf.console.Session()
        self.qmf_broker = self.qmf.addBroker(str(testrunner.url))

    def connect(self, host=None, port=None):
        sock = connect(host or testrunner.host, port or testrunner.port)
        if testrunner.url.scheme == URL.AMQPS:
            sock = ssl(sock)
        conn = Connection(sock, testrunner.spec, username=testrunner.user,
                          password=testrunner.password)
        conn.start(timeout=10)
        return conn

    def tearDown(self):
        if not self.session.error(): self.session.close(timeout=10)
        self.conn.close(timeout=10)
        if self.qmf:
            self.qmf.delBroker(self.qmf_broker)

    def subscribe(self, session=None, **keys):
        session = session or self.session
        consumer_tag = keys["destination"]
        session.message_subscribe(**keys)
        session.message_flow(destination=consumer_tag, unit=0, value=0xFFFFFFFF)
        session.message_flow(destination=consumer_tag, unit=1, value=0xFFFFFFFF)
