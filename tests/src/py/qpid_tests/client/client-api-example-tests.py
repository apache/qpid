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

** TODO Add XML Exchange tests

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

########################################################################################
#
#  If you are working from a source tree, setting the above paths is
#  sufficient.
# 
#  If your examples are installed somewhere else, you have to tell us
#  where examples in each language are kept
#
########################################################################################

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

java_invoke =  "java " + "-Dlog4j.configuration=log4j.conf "

############################################################################################


drains = [ 
    {'lang': 'CPP', 'command': cpp_examples_path + "drain" }, 
    {'lang': 'PYTHON', 'command': python_examples_path + "drain"}, 
    {'lang': 'JAVA', 'command': java_invoke + "org.apache.qpid.example.Drain"} 
    ]

spouts =  [ 
    {'lang': 'CPP', 'command': cpp_examples_path + "spout" }, 
    {'lang': 'PYTHON', 'command': python_examples_path + "spout"}, 
    {'lang': 'JAVA', 'command': java_invoke +  "org.apache.qpid.example.Spout"} 
    ]

mapSenders = [ 
    {'lang': 'CPP', 'command': cpp_examples_path + "map_sender" }, 
    {'lang': 'JAVA', 'command': java_invoke + "org.apache.qpid.example.MapSender"} 
    ]

mapReceivers =  [ 
    {'lang': 'CPP', 'command': cpp_examples_path + "map_receiver" }, 
    {'lang': 'JAVA', 'command': java_invoke +  "org.apache.qpid.example.MapReceiver"} 
    ]


hellos =   [ 
    {'lang': 'CPP', 'command': cpp_examples_path + "hello_world" }, 
    {'lang': 'PYTHON', 'command': python_examples_path + "hello" }, 
    {'lang': 'JAVA', 'command': java_invoke + "org.apache.qpid.example.Hello"} 
    ]

wockyClients = [ 
    {'lang': 'CPP', 'command': cpp_examples_path + "client" }, 
    ]

wockyServers =  [ 
    {'lang': 'CPP', 'command': cpp_examples_path + "server" }, 
    ]


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

    def send(self, spout=spouts[0], content="", destination="amq.topic", create=1, wait=0):
        if wait:
            sleep(wait)

        createS = ";{create:always}" if create else ""
        addressS = "'" + destination + createS + "'"
        brokerS = "-b " + qpid_broker
        if spout['lang']=='CPP':
            contentS = " ".join(['--content',"'"+content+"'"]) if content else ""
            commandS = " ".join([spout['command'], brokerS, contentS, addressS])
        elif spout['lang']=='PYTHON':
            commandS = " ".join([spout['command'], brokerS, addressS, content])
        elif spout['lang']=='JAVA':
            brokerS = "-b guest:guest@" + qpid_broker
            commandS = " ".join([spout['command'], brokerS, "--content="+"'"+content+"'", addressS])            
        else:  
            raise "Ain't no such language ...."
        logging.debug("send(): " + commandS)
        args = shlex.split(commandS)
        popen = subprocess.Popen(args, stdout=subprocess.PIPE)
        out, err  = popen.communicate()
        logging.debug("send() - out=" + str(out) + ", err=" + str(err))


    def receive(self, drain=drains[0], destination="amq.topic", delete=1):
        deleteS = ";{delete:always}" if delete else ""
        addressS = "'" + destination + deleteS + "'"
        brokerS = "-b " + qpid_broker
        optionS = "-c 1 -t 30"
        if drain['lang']=='CPP':
            commandS = " ".join([drain['command'], optionS, brokerS, optionS, addressS])
        elif drain['lang']=='PYTHON':
            commandS = " ".join([drain['command'], brokerS, optionS, addressS])
        elif drain['lang']=='JAVA':
            brokerS = "-b guest:guest@" + qpid_broker
            commandS = " ".join([drain['command'], brokerS, optionS, addressS])
        else:  
            raise "Ain't no such language ...."
        logging.debug("receive() " + commandS)
        args = shlex.split(commandS)
        popen = subprocess.Popen(args, stdout=subprocess.PIPE)
        out, err  = popen.communicate()
        logging.debug("receive() - out=" + str(out) + ", err=" + str(err))
        return out

    def subscribe(self, drain=drains[0], destination="amq.topic", create=0):
        optionS = "-t 30 -c 1"
        brokerS = "-b " + qpid_broker
        if drain['lang']=='CPP':
            commandS = " ".join([drain['command'], brokerS, optionS, destination])
        elif drain['lang']=='PYTHON':
            commandS = " ".join([drain['command'], brokerS, optionS, destination])
        elif drain['lang']=='JAVA':
            logging.debug("Java working directory: ")
            brokerS = "-b guest:guest@" + qpid_broker
            commandS = " ".join([drain['command'], brokerS, optionS, destination])
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
        for hello_world in hellos:
            args = shlex.split(hello_world['command'])
            popen = subprocess.Popen(args, stdout=subprocess.PIPE)
            out = popen.communicate()[0]
            logging.debug(out)
            self.assertTrue(out.find("world!") > 0)

    def test_jabberwocky(self):
        for i, s in enumerate(wockyServers):
            for j, c in enumerate(wockyClients):
                args = shlex.split(s['command'])
                server = subprocess.Popen(args, stdout=subprocess.PIPE)
                args = shlex.split(c['command'])
                client = subprocess.Popen(args, stdout=subprocess.PIPE)
                out = client.communicate()[0]
                logging.debug(out)
                self.assertTrue(out.find("BRILLIG") >= 0)
                server.terminate()

    def test_maps(self):
        for s in mapSenders:
            for r in mapReceivers:
                args = shlex.split(s['command'])
                sender = subprocess.Popen(args, stdout=subprocess.PIPE)
                args = shlex.split(r['command'])
                receiver = subprocess.Popen(args, stdout=subprocess.PIPE)
                out = receiver.communicate()[0]
                logging.debug(out)
                sender.terminate()

    def test_queues(self):
        for i, s in enumerate(spouts):
            for j, d in enumerate(drains):
                content = self.tcaseName()  + ": " + s['lang'] + str(i) + " => " + d['lang'] + str(j)
                self.send(s, content=content, destination="hello_world", create=1)
                out = self.receive(d, destination="hello_world", delete=1)
                self.assertTrue(out.find(content) >= 0)

    def test_direct_exchange(self):
        for i, s in enumerate(spouts):
            for j, d in enumerate(drains):
                content = self.tcaseName() + ": " + s['lang'] + str(i) + " => " + d['lang'] + str(j)
                popen1 = self.subscribe(d, destination="amq.direct/subject")
                popen2 = self.subscribe(d, destination="amq.direct/subject")                
                self.send(s, content=content, destination="amq.direct/subject", create=0, wait=2)
                out1 = self.listen(popen1)
                out2 = self.listen(popen2)
                self.assertTrue(out1.find(self.tcaseName()) >= 0)
                self.assertTrue(out2.find(self.tcaseName()) >= 0)

    def test_fanout_exchange(self):
        for i, s in enumerate(spouts):
            for j, d in enumerate(drains):
                content = self.tcaseName() + ": " + s['lang'] + str(i) + " => " + d['lang'] + str(j)
                popen1 = self.subscribe(d, destination="amq.fanout")
                popen2 = self.subscribe(d, destination="amq.fanout")                
                self.send(s, content=content, destination="amq.fanout", create=0, wait=2)
                out1 = self.listen(popen1)
                out2 = self.listen(popen2)
                self.assertTrue(out1.find(self.tcaseName()) >= 0)
                self.assertTrue(out2.find(self.tcaseName()) >= 0)


    def test_topic_exchange(self):
        for i, s in enumerate(spouts):
            for j, d in enumerate(drains):
                content = self.tcaseName() + ": " + s['lang'] + str(i) + " => " + d['lang'] + str(j)
                popen1 = self.subscribe(d, destination="amq.topic"   + "/" + s['lang'] + "." + d['lang'])
                popen2 = self.subscribe(d, destination="amq.topic"   + "/" + "*"       + "." + d['lang'])                
                popen3 = self.subscribe(d, destination="amq.topic"   + "/" + s['lang'] + "." + "*")
                popen4 = self.subscribe(d, destination="amq.topic"   + "/" + "#"       + "." + d['lang']) 
                self.send(s, content=content, destination="amq.topic"+ "/" + s['lang'] + "." + d['lang'], create=0, wait=4)
                out1 = self.listen(popen1)
                out2 = self.listen(popen2)
                out3 = self.listen(popen3)
                out4 = self.listen(popen4)
                logging.debug("out1:"+out1)
                logging.debug("out2:"+out2)
                logging.debug("out3:"+out3)
                logging.debug("out4:"+out4)
                self.assertTrue(out1.find(self.tcaseName()) >= 0)
                self.assertTrue(out2.find(self.tcaseName()) >= 0)
                self.assertTrue(out3.find(self.tcaseName()) >= 0)
                self.assertTrue(out4.find(self.tcaseName()) >= 0)


if __name__ == '__main__':
    unittest.main()

