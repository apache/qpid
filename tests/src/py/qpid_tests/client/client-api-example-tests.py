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
"""
 client-api-examples-interop.py

"""

""" 

* Next steps:

** TODO Allow port of running broker to be passed in
** TODO Support all exchange types
** TODO Find problem with 'suscribe/listen' tests (see scrp)
** TODO Add XML Exchange tests
** TODO Convert to "qpid-python-test" framework
** TODO Add Java client tests and interop

"""

import os
import shlex
import subprocess
import unittest
import uuid

import logging

logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s %(levelname)s %(message)s',
                    filename='./client-api-example-tests.log',
                    filemode='w')

#######################################################################################
# 
#  !!! Configure your paths here !!!
#
#######################################################################################

## If you set qpid_root on a source tree, from the default install for
## this script, you're good to go. If running from elsewhere against a
## source tree, set QPID_ROOT. If running from an installed system,
## set QPID_CPP_EXAMPLES, QPID_PYTHON_EXAMPLES, QPID_PYTHON_TOOLS,
## etc. to the directories below.

qpid_root = os.getenv("QPID_ROOT", os.path.abspath("../../../../../../qpid"))
print "Qpid Root: " + qpid_root

## If your examples are installed somewhere else, you have to tell us
## where examples in each language are kept

cpp_examples_path =  os.getenv("QPID_CPP_EXAMPLES", qpid_root + "/cpp/examples/messaging/")
python_examples_path =  os.getenv("QPID_PYTHON_EXAMPLES", qpid_root + "/python/examples/api/")
java_qpid_home = os.getenv("QPID_HOME", qpid_root + "/java/build/lib/")
os.environ["QPID_HOME"] = java_qpid_home
print "Java's QPID_HOME: " + os.environ["QPID_HOME"]
java_examples_path =  os.getenv("QPID_JAVA_EXAMPLES", qpid_root + "/java/client/example/")

python_path = os.getenv("PYTHONPATH", qpid_root+"/python:" + qpid_root+"/extras/qmf/src/py")
os.environ["PYTHONPATH"] = python_path
print "PYTHONPATH: " + os.environ["PYTHONPATH"]

python_tools_path =  os.getenv("QPID_PYTHON_TOOLS", qpid_root + "/tools/src/py/")
print "QPID_PYTHON_TOOLS: " + python_tools_path


############################################################################################

# Paths to programs
hello_world = cpp_examples_path + "hello_world"
cpp_drain = cpp_examples_path + "drain"
cpp_spout = cpp_examples_path + "spout"
cpp_map_send = cpp_examples_path + "map_sender"
cpp_map_receive = cpp_examples_path + "map_receiver"
cpp_client = cpp_examples_path + "map_sender"
cpp_server = cpp_examples_path + "map_receiver"
python_drain = python_examples_path + "drain"
python_spout = python_examples_path + "spout"
java_drain = java_examples_path + "src/main/java/runSample.sh org.apache.qpid.example.Drain"
java_spout = java_examples_path + "src/main/java/runSample.sh org.apache.qpid.example.Spout"

CPP = object()
PYTHON = object()
JAVA = object()

class TestDrainSpout(unittest.TestCase):

    _broker = 0

    # setUp / tearDown

    def setUp(self):
        logging.debug('--- START TEST ----')

    def tearDown(self):
        pass

    #############################################################################
    #
    #  Lemmas
    #
    #############################################################################

    # Python utilities

    def qpid_config(self, args):
        commandS = python_tools_path + "qpid-config" + ' ' + args
        args = shlex.split(commandS)
        logging.debug("qpid_config(): " + commandS)
        subprocess.Popen(args).wait()

    # Send / receive methods in various languages

    def send(self, lang=CPP, content="", destination="amq.topic", create=1):
        createS = ";{create:always}" if create else ""
        addressS = '"' + destination + createS + '"'
        if lang==CPP:
            contentS = " ".join(['--content','"'+content+'"']) if content else ""
            commandS = " ".join([cpp_spout, contentS, addressS])
        elif lang==PYTHON:
            commandS = " ".join([python_spout, addressS, content])
        elif lang==JAVA:
            pass
        else:  
            raise "Ain't no such language ...."
        logging.debug("send(): " + commandS)
        args = shlex.split(commandS)
        subprocess.Popen(args).wait()

    def receive(self, lang=CPP, destination="amq.topic", delete=1):
        deleteS = ";{delete:always}" if delete else ""
        addressS = '"' + destination + deleteS + '"'
        if lang==CPP:
            commandS = " ".join([cpp_drain, addressS])
        elif lang==PYTHON:
            commandS = " ".join([python_drain, addressS])
        elif lang==JAVA:
            pass
        else:  
            raise "Ain't no such language ...."
        logging.debug("receive() " + commandS)
        args = shlex.split(commandS)
        popen = subprocess.Popen(args, stdout=subprocess.PIPE)
        out, err  = popen.communicate()
        popen.wait()
        logging.debug("receive() - out=" + str(out) + ", err=" + str(err))
        return out

    def subscribe(self, lang=CPP, destination="amq.topic", create=0):
        time = "-t 5"
        if lang==CPP:
            commandS = " ".join([cpp_drain, time, destination])
        elif lang==PYTHON:
            commandS = " ".join([python_drain, time, destination])
        elif lang==JAVA:
            pass
        else:
            logging.debug("subscribe() - no such language!")  
            raise "Ain't no such language ...."
        logging.debug("subscribe() " + commandS)
        args = shlex.split(commandS)
        return subprocess.Popen(args, stdout=subprocess.PIPE)

    def listen(self, popen):
        out,err = popen.communicate()
        popen.wait()
        logging.debug("listen(): out=" + str(out) + ", err=" + str(err))
        return out
    
    #############################################################################
    #
    #  Tests
    #
    #############################################################################

    # Hello world!
 
    def test_hello_world(self):
        args = shlex.split(hello_world)
        popen = subprocess.Popen(args, stdout=subprocess.PIPE)
        out = popen.communicate()[0]
        popen.wait()
        self.assertTrue(out.find("world!") > 0)

    # Make sure qpid-config is working

    def test_qpid_config(self):
        self.qpid_config("add exchange topic weather")

    # Simple queue tests

    ## send / receive

    def test_queue_cpp2cpp(self):
        self.send(lang=CPP, content="xoxox", destination="hello-world", create=1)
        out = self.receive(lang=CPP, destination="hello-world", delete=1)
        self.assertTrue(out.find("xoxox") >= 0)

    def test_queue_cpp2python(self):
        self.send(lang=CPP, content="xoxox", destination="hello-world", create=1)
        out = self.receive(lang=PYTHON, destination="hello-world", delete=1)
        self.assertTrue(out.find("xoxox") > 0)

    def test_queue_python2cpp(self):
        self.send(lang=PYTHON, content="xoxox", destination="hello-world", create=1)
        out = self.receive(lang=CPP, destination="hello-world", delete=1)
        self.assertTrue(out.find("xoxox") >= 0)

    def test_queue_python2python(self):
        self.send(lang=PYTHON, content="xoxox", destination="hello-world", create=1)
        out = self.receive(lang=PYTHON, destination="hello-world", delete=1)
        self.assertTrue(out.find("xoxox") >= 0)


    # Direct Exchange

    ## send / receive

    def test_amqdirect_cpp2cpp(self):
        popen = self.subscribe(lang=CPP, destination="amq.direct")
        self.send(lang=CPP, content="xoxox", destination="amq.direct", create=0)
        out = self.listen(popen)
        self.assertTrue(out.find("xoxox") >= 0)

    def test_amqdirect_python2cpp(self):
        popen = self.subscribe(lang=CPP, destination="amq.direct")
        self.send(lang=PYTHON, content="xoxox", destination="amq.direct", create=0)
        out = self.listen(popen)
        self.assertTrue(out.find("xoxox") >= 0)

    def test_amqdirect_cpp2python(self):
        popen = self.subscribe(lang=PYTHON, destination="amq.direct")
        self.send(lang=CPP, content="xoxox", destination="amq.direct", create=0)
        out = self.listen(popen)
        self.assertTrue(out.find("xoxox") >= 0)

    def test_amqdirect_python2python(self):
        popen = self.subscribe(lang=PYTHON, destination="amq.direct")
        self.send(lang=PYTHON, content="xoxox", destination="amq.direct", create=0)
        out = self.listen(popen)
        self.assertTrue(out.find("xoxox") >= 0)

    def test_amqdirect_cpp2cpp_tworeceivers(self):
        popen1 = self.subscribe(lang=CPP, destination="amq.direct")
        popen2 = self.subscribe(lang=PYTHON, destination="amq.direct")
        self.send(lang=CPP, content="xoxox", destination="amq.direct", create=0)
        out1 = self.listen(popen1)
        out2 = self.listen(popen2)
        self.assertTrue(out1.find("xoxox") >= 0)
        self.assertTrue(out2.find("xoxox") >= 0)

    def test_amqdirect_cpp2cpp_nosubscription(self):
        self.send(lang=CPP, content="xoxox", destination="amq.direct", create=0)
        out = self.receive(lang=CPP, destination="amq.direct", delete=0)
        self.assertFalse(out.find("xoxox") >= 0)

    def test_amqdirect_cpp2python_nosubscription(self):
        self.send(lang=CPP, content="xoxox", destination="amq.direct", create=0)
        out = self.receive(lang=PYTHON, destination="amq.direct", delete=0)
        self.assertFalse(out.find("xoxox") > 0)

    def test_amqdirect_python2cpp_nosubscription(self):
        self.send(lang=PYTHON, content="xoxox", destination="amq.direct", create=0)
        out = self.receive(lang=CPP, destination="amq.direct", delete=0)
        self.assertFalse(out.find("xoxox") >= 0)

    def test_amqdirect_python2python_nosubscription(self):
        self.send(lang=PYTHON, content="xoxox", destination="amq.direct", create=0)
        out = self.receive(lang=PYTHON, destination="amq.direct", delete=0)
        self.assertFalse(out.find("xoxox") >= 0)


    #  Request / Response

    def test_jabberwocky_cpp(self):
        args = shlex.split(cpp_examples_path + 'server')
        server = subprocess.Popen(args, stdout=subprocess.PIPE)
        args = shlex.split(cpp_examples_path + 'client')
        client = subprocess.Popen(args, stdout=subprocess.PIPE)
        out = client.communicate()[0]
        client.wait()
        self.assertTrue(out.find("BRILLIG") >= 0)
        server.terminate()


if __name__ == '__main__':
    unittest.main()

