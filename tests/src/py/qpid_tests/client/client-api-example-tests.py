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
import re
from time import sleep

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
logging.debug("Qpid Root: " + qpid_root)

qpid_broker = os.getenv("QPID_BROKER", "localhost:5672")
logging.debug("Qpid Broker: " + qpid_broker)

## If your examples are installed somewhere else, you have to tell us
## where examples in each language are kept

cpp_examples_path =  os.getenv("QPID_CPP_EXAMPLES", qpid_root + "/cpp/examples/messaging/")

python_examples_path =  os.getenv("QPID_PYTHON_EXAMPLES", qpid_root + "/python/examples/api/")
python_path = os.getenv("PYTHONPATH", qpid_root+"/python:" + qpid_root+"/extras/qmf/src/py")
os.environ["PYTHONPATH"] = python_path
logging.debug("PYTHONPATH: " + os.environ["PYTHONPATH"])

python_tools_path =  os.getenv("QPID_PYTHON_TOOLS", qpid_root + "/tools/src/py/")
logging.debug("QPID_PYTHON_TOOLS: " + python_tools_path)

java_qpid_home = os.getenv("QPID_HOME", qpid_root + "/java/build/lib/")
os.environ["QPID_HOME"] = java_qpid_home
logging.debug("Java's QPID_HOME: " + os.environ["QPID_HOME"])
java_examples_path =  os.getenv("QPID_JAVA_EXAMPLES", qpid_root + "/java/client/example/")
find = "find " + java_qpid_home + " -name '*.jar'"
args = shlex.split(find)
popen = subprocess.Popen(args, stdout=subprocess.PIPE)
out, err  = popen.communicate()
os.environ["CLASSPATH"] = java_examples_path + ":" + re.sub("\\n", ":", out)
logging.debug("Java CLASSPATH = " + os.environ["CLASSPATH"])

"""
log4j.logger.org.apache.qpid=WARN, console
log4j.additivity.org.apache.qpid=false

log4j.appender.console=org.apache.log4j.ConsoleAppender
log4j.appender.console.Threshold=all
log4j.appender.console.layout=org.apache.log4j.PatternLayout
log4j.appender.console.layout.ConversionPattern=%t %d %p [%c{4}] %m%n
"""


############################################################################################

# Paths to programs
hello_world = cpp_examples_path + "hello_world" + " "  + qpid_broker
cpp_drain = cpp_examples_path + "drain" + " -b " + qpid_broker
cpp_spout = cpp_examples_path + "spout" + " -b " + qpid_broker
# cpp_map_send = cpp_examples_path + "map_sender"
# cpp_map_receive = cpp_examples_path + "map_receiver"
# cpp_client = cpp_examples_path + "map_sender"
# cpp_server = cpp_examples_path + "map_receiver"
python_drain = python_examples_path + "drain" + " -b " + qpid_broker
python_spout = python_examples_path + "spout" + " -b " + qpid_broker
java_drain = "java " + "org.apache.qpid.example.Drain"
java_spout = "java " + "org.apache.qpid.example.Spout"

CPP = object()
PYTHON = object()
JAVA = object()

shortWait = 0.5
longWait = 3   # use sparingly!

class TestDrainSpout(unittest.TestCase):

    # setUp / tearDown

    def setUp(self):
        logging.debug('----------------------------')
        logging.debug('START: ' + self.tcaseName())

    def tearDown(self):
        pass

    #############################################################################
    #
    #  Lemmas
    #
    #############################################################################

    def tcaseName(self):
        return  re.split('[.]', self.id())[-1]

    # Python utilities

    def qpid_config(self, args):
        commandS = python_tools_path + "qpid-config" + ' ' + args
        args = shlex.split(commandS)
        logging.debug("qpid_config(): " + commandS)
        popen = subprocess.Popen(args, stdout=subprocess.PIPE)
        out, err  = popen.communicate()
        logging.debug("qpid-config() - out=" + str(out) + ", err=" + str(err))

    # Send / receive methods in various languages

    def send(self, lang=CPP, content="", destination="amq.topic", create=1, wait=0):
        if wait:
            sleep(wait)

        createS = ";{create:always}" if create else ""
        addressS = "'" + destination + createS + "'"
        if lang==CPP:
            contentS = " ".join(['--content',"'"+content+"'"]) if content else ""
            commandS = " ".join([cpp_spout, contentS, addressS])
        elif lang==PYTHON:
            commandS = " ".join([python_spout, addressS, content])
        elif lang==JAVA:
            commandS = " ".join([java_spout, "--content="+"'"+content+"'", addressS])            
        else:  
            raise "Ain't no such language ...."
        logging.debug("send(): " + commandS)
        args = shlex.split(commandS)
        popen = subprocess.Popen(args, stdout=subprocess.PIPE)
        out, err  = popen.communicate()
        logging.debug("send() - out=" + str(out) + ", err=" + str(err))


    def receive(self, lang=CPP, destination="amq.topic", delete=1):
        deleteS = ";{delete:always}" if delete else ""
        addressS = "'" + destination + deleteS + "'"
        if lang==CPP:
            commandS = " ".join([cpp_drain, addressS])
        elif lang==PYTHON:
            commandS = " ".join([python_drain, addressS])
        elif lang==JAVA:
            commandS = " ".join([java_drain, addressS])
        else:  
            raise "Ain't no such language ...."
        logging.debug("receive() " + commandS)
        args = shlex.split(commandS)
        popen = subprocess.Popen(args, stdout=subprocess.PIPE)
        out, err  = popen.communicate()
        logging.debug("receive() - out=" + str(out) + ", err=" + str(err))
        return out

    def subscribe(self, lang=CPP, destination="amq.topic", create=0):
        time = "-t 5"
        if lang==CPP:
            commandS = " ".join([cpp_drain, time, destination])
        elif lang==PYTHON:
            commandS = " ".join([python_drain, time, destination])
        elif lang==JAVA:
            logging.debug("Java working directory: ")
            commandS = " ".join([java_drain, time, destination])
        else:
            logging.debug("subscribe() - no such language!")  
            raise "Ain't no such language ...."
        logging.debug("subscribe() " + commandS)
        args = shlex.split(commandS)
        return subprocess.Popen(args, stdout=subprocess.PIPE)

    def listen(self, popen):
        out,err = popen.communicate()
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
        logging.debug(out)
        self.assertTrue(out.find("world!") > 0)

    # Make sure qpid-config is working

    def test_qpid_config(self):
        self.qpid_config("add exchange topic weather")
        self.qpid_config("del exchange weather")

    # Simple queue tests

    ## send / receive

    def test_queue_cpp2cpp(self):
        self.send(lang=CPP, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=CPP, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_queue_cpp2python(self):
        self.send(lang=CPP, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=PYTHON, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) > 0)

    def test_queue_python2cpp(self):
        self.send(lang=PYTHON, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=CPP, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_queue_python2python(self):
        self.send(lang=PYTHON, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=PYTHON, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_queue_java2java(self):
        self.send(lang=JAVA, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=JAVA, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_queue_java2python(self):
        self.send(lang=JAVA, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=PYTHON, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_queue_java2cpp(self):
        self.send(lang=JAVA, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=CPP, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_queue_cpp2java(self):
        self.send(lang=CPP, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=JAVA, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_queue_python2java(self):
        self.send(lang=PYTHON, content=self.tcaseName(), destination="hello-world", create=1)
        out = self.receive(lang=JAVA, destination="hello-world", delete=1)
        self.assertTrue(out.find(self.tcaseName()) >= 0)


    # Direct Exchange

    ## send / receive

    def test_amqdirect_cpp2cpp(self):
        popen = self.subscribe(lang=CPP, destination="amq.direct/subject")
        self.send(lang=CPP, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=shortWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_python2cpp(self):
        popen = self.subscribe(lang=CPP, destination="amq.direct/subject")
        self.send(lang=PYTHON, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=shortWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_cpp2python(self):
        popen = self.subscribe(lang=PYTHON, destination="amq.direct/subject")
        self.send(lang=CPP, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=shortWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_python2python(self):
        popen = self.subscribe(lang=PYTHON, destination="amq.direct/subject")
        self.send(lang=PYTHON, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=shortWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_java2java(self):
        popen = self.subscribe(lang=JAVA, destination="amq.direct/subject")
        self.send(lang=JAVA, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=shortWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_java2python(self):
        popen = self.subscribe(lang=PYTHON, destination="amq.direct/subject")
        self.send(lang=JAVA, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=shortWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_java2cpp(self):
        popen = self.subscribe(lang=CPP, destination="amq.direct/subject")
        self.send(lang=JAVA, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=shortWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_cpp2java(self):
        popen = self.subscribe(lang=JAVA, destination="amq.direct/subject")
        self.send(lang=CPP, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=longWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_python2java(self):
        popen = self.subscribe(lang=JAVA, destination="amq.direct/subject")
        self.send(lang=PYTHON, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=longWait)
        out = self.listen(popen)
        self.assertTrue(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_cpp2cpp_tworeceivers(self):
        popen1 = self.subscribe(lang=CPP, destination="amq.direct/subject")
        popen2 = self.subscribe(lang=PYTHON, destination="amq.direct/subject")
        self.send(lang=CPP, content=self.tcaseName(), destination="amq.direct/subject", create=0, wait=shortWait)
        out1 = self.listen(popen1)
        out2 = self.listen(popen2)
        self.assertTrue(out1.find(self.tcaseName()) >= 0)
        self.assertTrue(out2.find(self.tcaseName()) >= 0)

    def test_amqdirect_cpp2cpp_nosubscription(self):
        self.send(lang=CPP, content=self.tcaseName(), destination="amq.direct/subject", create=0)
        out = self.receive(lang=CPP, destination="amq.direct/subject", delete=0)
        self.assertFalse(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_cpp2python_nosubscription(self):
        self.send(lang=CPP, content=self.tcaseName(), destination="amq.direct/subject", create=0)
        out = self.receive(lang=PYTHON, destination="amq.direct/subject", delete=0)
        self.assertFalse(out.find(self.tcaseName()) > 0)

    def test_amqdirect_python2cpp_nosubscription(self):
        self.send(lang=PYTHON, content=self.tcaseName(), destination="amq.direct/subject", create=0)
        out = self.receive(lang=CPP, destination="amq.direct/subject", delete=0)
        self.assertFalse(out.find(self.tcaseName()) >= 0)

    def test_amqdirect_python2python_nosubscription(self):
        self.send(lang=PYTHON, content=self.tcaseName(), destination="amq.direct/subject", create=0)
        out = self.receive(lang=PYTHON, destination="amq.direct/subject", delete=0)
        self.assertFalse(out.find(self.tcaseName()) >= 0)


    #  Request / Response

    def test_jabberwocky_cpp(self):
        args = shlex.split(cpp_examples_path + 'server')
        server = subprocess.Popen(args, stdout=subprocess.PIPE)
        args = shlex.split(cpp_examples_path + 'client')
        client = subprocess.Popen(args, stdout=subprocess.PIPE)
        out = client.communicate()[0]
        logging.debug(out)
        self.assertTrue(out.find("BRILLIG") >= 0)
        server.terminate()

    def test_topic_news_sports_weather_cpp(self):
        news = self.subscribe(lang=CPP, destination="amq.topic/*.news")
        deepnews = self.subscribe(lang=PYTHON, destination="amq.topic/#.news")
        weather = self.subscribe(lang=JAVA, destination="amq.topic/*.weather")
        sports = self.subscribe(lang=CPP, destination="amq.topic/*.sports")
        usa = self.subscribe(lang=PYTHON, destination="amq.topic/usa.*")
        europe = self.subscribe(lang=JAVA, destination="amq.topic/europe.*")
        self.send(lang=CPP, content="usa.news", destination="amq.topic/usa.news", create=0, wait=longWait)
        self.send(lang=PYTHON, content="usa.news", destination="amq.topic/usa.faux.news", create=0)
        self.send(lang=JAVA, content="europe.news", destination="amq.topic/europe.news", create=0)
        self.send(lang=CPP, content="usa.weather", destination="amq.topic/usa.weather", create=0)
        self.send(lang=PYTHON, content="europe.weather", destination="amq.topic/europe.weather", create=0)
        self.send(lang=JAVA, content="usa.sports", destination="amq.topic/usa.sports", create=0) 
        self.send(lang=CPP, content="europe.sports", destination="amq.topic/europe.sports", create=0)
        out = self.listen(news)
        self.assertTrue(out.find("usa.news") >= 0)        
        self.assertTrue(out.find("europe.news") >= 0)        
        self.assertTrue(out.find("usa.news") >= 0)        
        self.assertFalse(out.find("usa.faux.news") >= 0)        
        out = self.listen(weather)
        self.assertTrue(out.find("usa.weather") >= 0)        
        self.assertTrue(out.find("europe.weather") >= 0) 
        out = self.listen(sports)
        self.assertTrue(out.find("usa.sports") >= 0)        
        self.assertTrue(out.find("europe.sports") >= 0)        
        out = self.listen(usa)
        self.assertTrue(out.find("usa.news") >= 0)        
        self.assertTrue(out.find("usa.sports") >= 0)        
        self.assertTrue(out.find("usa.weather") >= 0)        
        out = self.listen(europe)
        self.assertTrue(out.find("europe.news") >= 0)        
        self.assertTrue(out.find("europe.sports") >= 0)        
        self.assertTrue(out.find("europe.weather") >= 0)        
        out = self.listen(deepnews)
        self.assertTrue(out.find("usa.news") >= 0)        
        self.assertTrue(out.find("europe.news") >= 0)        
        self.assertTrue(out.find("usa.news") >= 0)        
        self.assertTrue(out.find("usa.faux.news") >= 0)        
        news.wait()
        weather.wait()
        sports.wait()
        usa.wait()
        europe.wait()
        deepnews.wait()


if __name__ == '__main__':
    unittest.main()

