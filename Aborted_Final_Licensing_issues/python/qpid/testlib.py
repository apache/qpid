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

import sys, re, unittest, os, random, logging
import qpid.client, qpid.spec
import Queue
from getopt import getopt, GetoptError
from qpid.content import Content

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
  -?/-h/--help         : this message
  -s/--spec <spec.xml> : file containing amqp XML spec 
  -b/--broker [<user>[/<password>]@]<host>[:<port>] : broker to connect to
  -v/--verbose         : verbose - lists tests as they are run.
  -d/--debug           : enable debug logging.
  -i/--ignore <test>   : ignore the named test.
  -I/--ignore-file     : file containing patterns to ignore.
  """
        sys.exit(1)

    def setBroker(self, broker):
        rex = re.compile(r"""
        # [   <user>  [   / <password> ] @]  <host>  [   :<port>   ]
        ^ (?: ([^/]*) (?: / ([^@]*)   )? @)? ([^:]+) (?: :([0-9]+))?$""", re.X)
        match = rex.match(broker)
        if not match: self._die("'%s' is not a valid broker" % (broker))
        self.user, self.password, self.host, self.port = match.groups()
        self.port = int(default(self.port, 5672))
        self.user = default(self.user, "guest")
        self.password = default(self.password, "guest")

    def __init__(self):
        # Defaults
        self.setBroker("localhost")
        self.spec = "../specs/amqp.0-8.xml"
        self.verbose = 1
        self.ignore = []

    def ignoreFile(self, filename):
        f = file(filename)
        for line in f.readlines(): self.ignore.append(line.strip())
        f.close()

    def _parseargs(self, args):
        try:
            opts, self.tests = getopt(args, "s:b:h?dvi:I:", ["help", "spec", "server", "verbose", "ignore", "ignore-file"])
        except GetoptError, e:
            self._die(str(e))
        for opt, value in opts:
            if opt in ("-?", "-h", "--help"): self._die()
            if opt in ("-s", "--spec"): self.spec = value
            if opt in ("-b", "--broker"): self.setBroker(value)
            if opt in ("-v", "--verbose"): self.verbose = 2
            if opt in ("-d", "--debug"): logging.basicConfig(level=logging.DEBUG)
            if opt in ("-i", "--ignore"): self.ignore.append(value)
            if opt in ("-I", "--ignore-file"): self.ignoreFile(value)

        if len(self.tests) == 0: self.tests=findmodules("tests")

    def testSuite(self):
        class IgnoringTestSuite(unittest.TestSuite):
            def addTest(self, test):
                if isinstance(test, unittest.TestCase) and test.id() in testrunner.ignore:
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

    def connect(self, host=None, port=None, spec=None, user=None, password=None):
        """Connect to the broker, returns a qpid.client.Client"""
        host = host or self.host
        port = port or self.port
        spec = spec or self.spec
        user = user or self.user
        password = password or self.password
        client = qpid.client.Client(host, port, qpid.spec.load(spec))
        client.start({"LOGIN": user, "PASSWORD": password})
        return client


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
        self.channel.channel_open()

    def tearDown(self):
        for ch, q in self.queues:
            ch.queue_delete(queue=q)
        for ch, ex in self.exchanges:
            ch.exchange_delete(exchange=ex)

    def connect(self, *args, **keys):
        """Create a new connction, return the Client object"""
        return testrunner.connect(*args, **keys)

    def queue_declare(self, channel=None, *args, **keys):
        channel = channel or self.channel
        reply = channel.queue_declare(*args, **keys)
        self.queues.append((channel, reply.queue))
        return reply
            
    def exchange_declare(self, channel=None, ticket=0, exchange='',
                         type='', passive=False, durable=False,
                         auto_delete=False, internal=False, nowait=False,
                         arguments={}):
        channel = channel or self.channel
        reply = channel.exchange_declare(ticket, exchange, type, passive, durable, auto_delete, internal, nowait, arguments)
        self.exchanges.append((channel,exchange))
        return reply

    def uniqueString(self):
        """Generate a unique string, unique for this TestBase instance"""
        if not "uniqueCounter" in dir(self): self.uniqueCounter = 1;
        return "Test Message " + str(self.uniqueCounter)
        
    def consume(self, queueName):
        """Consume from named queue returns the Queue object."""
        reply = self.channel.basic_consume(queue=queueName, no_ack=True)
        return self.client.queue(reply.consumer_tag)

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
        self.channel.basic_publish(exchange=exchange,
                                   content=Content(body, properties=properties),
                                   routing_key=routing_key)
        msg = queue.get(timeout=1)    
        self.assertEqual(body, msg.content.body)
        if (properties): self.assertEqual(properties, msg.content.properties)
        
    def assertPublishConsume(self, queue="", exchange="", routing_key="", properties=None):
        """
        Publish a message and consume it, assert it comes back intact.
        Return the Queue object used to consume.
        """
        self.assertPublishGet(self.consume(queue), exchange, routing_key, properties)

    def assertChannelException(self, expectedCode, message): 
        self.assertEqual("channel", message.method.klass.name)
        self.assertEqual("close", message.method.name)
        self.assertEqual(expectedCode, message.reply_code)


    def assertConnectionException(self, expectedCode, message): 
        self.assertEqual("connection", message.method.klass.name)
        self.assertEqual("close", message.method.name)
        self.assertEqual(expectedCode, message.reply_code)

