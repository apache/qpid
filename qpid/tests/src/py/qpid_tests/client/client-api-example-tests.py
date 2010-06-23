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
** TODO Convert to "qpid-python-test" framework
** TODO Add Java client tests and interop

"""

import os
import shlex
import subprocess
import unittest
import uuid


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

class TestDrainSpout(unittest.TestCase):

    _broker = 0

    # setUp / tearDown

    def setUp(self):
        pass

    def tearDown(self):
        pass

    # Python utilities

    def qpid_config(self, args):
        args = shlex.split(python_tools_path + "qpid-config" + ' ' + args)
        subprocess.Popen(args).wait()

    # Send / receive methods in various languages

    def cpp_send(self, spoutargs):
        args = shlex.split(cpp_spout + ' ' + spoutargs)
        subprocess.Popen(args).wait()
  
    def cpp_receive(self, drainargs):
        args = shlex.split(cpp_drain + ' ' + drainargs)
        return subprocess.Popen(args, stdout=subprocess.PIPE).communicate()[0]

    def cpp_start_listen(self, drainargs):
        args = shlex.split(cpp_drain + ' ' + drainargs)
        return subprocess.Popen(args, stdout=subprocess.PIPE)

    def python_send(self, spoutargs):
        args = shlex.split(python_spout + ' ' + spoutargs)
        subprocess.Popen(args).wait()

    def python_receive(self, drainargs):
        args = shlex.split(python_drain + ' ' + drainargs)
        return subprocess.Popen(args, stdout=subprocess.PIPE).communicate()[0]

    def python_start_listen(self, drainargs):
        args = shlex.split(python_drain + ' ' + drainargs)
        return subprocess.Popen(args, stdout=subprocess.PIPE)

    def java_send(self, spoutargs):
        args = shlex.split(java_spout + ' ' + spoutargs)
        subprocess.Popen(args).wait()
  
    def java_receive(self, drainargs):
        args = shlex.split(java_drain + ' ' + drainargs)
        return subprocess.Popen(args, stdout=subprocess.PIPE).communicate()[0]

    def java_start_listen(self, drainargs):
        args = shlex.split(java_drain + ' ' + drainargs)
        return subprocess.Popen(args, stdout=subprocess.PIPE)

    # Hello world!

    def test_hello_world(self):
        args = shlex.split(hello_world)
        out = subprocess.Popen(args, stdout=subprocess.PIPE).communicate()[0]
        self.assertTrue(out.find("world!") > 0)

    # Make sure qpid-config is working

    def test_qpid_config(self):
        self.qpid_config("add exchange topic weather")

    # Simple queue tests

    def test_queue_spout_cpp_create_drain_cpp_delete(self):
        self.cpp_send(' --content "xoxox" "hello-world;{create:always}"')
        out = self.cpp_receive(' "hello-world;{delete:always}"')
        self.assertTrue(out.find("xoxox") >= 0)

    def test_queue_spout_python_create_drain_python_delete(self):
        self.python_send(' "hello-world;{create:always}"')
        out = self.python_receive(' "hello-world;{delete:always}"')
        self.assertTrue(out.find("Message") >= 0)

    def test_queue_spout_cpp_create_drain_python_delete(self):
        self.cpp_send(' --content "xoxox" "hello-world;{create:always}"')
        out = self.python_receive(' "hello-world;{delete:always}"')
        self.assertTrue(out.find("xoxox") > 0)

    def test_queue_spout_python_create_drain_cpp_delete(self):
        self.python_send(' "hello-world ; { create:always }"')
        out = self.cpp_receive(' "hello-world ; { delete:always }"')
        self.assertTrue(out.find("Message") >= 0)

    def test_queue_spout_python_create_drain_twice_cpp_delete(self):
        self.python_send(' "hello-world ; { create:always }"')
        out = self.cpp_receive(' "hello-world ; { delete:always }"')
        out = self.cpp_receive(' "hello-world ; { delete:always }"')
        self.assertFalse(out.find("Message") >= 0)

    def test_queue_listen_python_spout_python(self):
        popen = self.python_start_listen(' -t 5 "hello-world ; { create: always, delete:always }"')
        self.python_send(' hello-world')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_queue_listen_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "hello-world ; { create: always, delete:always }"')
        self.cpp_send(' hello-world')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_queue_listen_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "hello-world ; { create: always, delete:always }"')
        self.cpp_send(' hello-world')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_queue_listen_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "hello-world ; { create: always, delete:always }"')
        self.python_send(' hello-world')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    # Direct exchange

    def test_direct_spout_cpp_drain_cpp_empty(self):
        self.cpp_send(' --content "xoxox" "amq.direct/hello"')
        out = self.cpp_receive(' "amq.direct"')
        self.assertFalse(out.find("xoxox") >= 0)

    def test_send_nonexistent_queue_cpp(self):
        self.assertFalse(self.cpp_send(' --content "xoxox" "xoxox"'))

    def test_direct_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.direct/hello"')
        self.cpp_send(' --content "xoxox" "amq.direct/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_direct_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.direct/hello"')
        self.cpp_send(' --content "xoxox" "amq.direct/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_direct_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.direct/hello"')
        self.python_send(' "amq.direct/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_direct_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.direct"')
        self.cpp_send(' --content "xoxox" "amq.direct"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_direct_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.direct"')
        self.cpp_send(' --content "xoxox" "amq.direct"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_direct_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.direct"')
        self.python_send(' "amq.direct"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_direct_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.direct/camel"')
        self.cpp_send(' --content "xoxox" "amq.direct/pig"')
        out = popen.communicate()[0]
        self.assertFalse(out.find("xoxox") >= 0)

    def test_direct_drain_cpp_drain_cpp_spout_cpp(self):
        drain1 = self.cpp_start_listen(' -t 5 "amq.direct/camel"')
        drain2 = self.cpp_start_listen(' -t 5 "amq.direct/camel"')
        self.cpp_send(' --content "xoxox" "amq.direct/camel"')
        out1 = drain1.communicate()[0]
        out2 = drain2.communicate()[0]
        self.assertTrue(out1.find("xoxox") >= 0)
        self.assertTrue(out2.find("xoxox") >= 0)

    def test_direct_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.direct/camel"')
        self.cpp_send(' --content "xoxox" "amq.direct/pig"')
        out = popen.communicate()[0]
        self.assertFalse(out.find("xoxox") >= 0)

    def test_direct_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.direct/camel"')
        self.python_send(' "amq.direct/pig"')
        out = popen.communicate()[0]
        self.assertFalse(out.find("Message") >= 0)

    #  Fanout exchange

    def test_fanout_spout_cpp_drain_cpp_empty(self):
        self.cpp_send(' --content "xoxox" "amq.fanout/hello"')
        out = self.cpp_receive(' "amq.fanout"')
        self.assertFalse(out.find("xoxox") >= 0)

    def test_send_nonexistent_queue_cpp(self):
        self.assertFalse(self.cpp_send(' --content "xoxox" "xoxox"'))

    def test_fanout_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.fanout/hello"')
        self.cpp_send(' --content "xoxox" "amq.fanout/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_fanout_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.fanout/hello"')
        self.cpp_send(' --content "xoxox" "amq.fanout/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_fanout_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.fanout/hello"')
        self.python_send(' "amq.fanout/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_fanout_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.fanout"')
        self.cpp_send(' --content "xoxox" "amq.fanout"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_fanout_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.fanout"')
        self.cpp_send(' --content "xoxox" "amq.fanout"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_fanout_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.fanout"')
        self.python_send(' "amq.fanout"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_fanout_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.fanout/camel"')
        self.cpp_send(' --content "xoxox" "amq.fanout/pig"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_fanout_drain_cpp_drain_cpp_spout_cpp(self):
        drain1 = self.cpp_start_listen(' -t 5 "amq.fanout/camel"')
        drain2 = self.cpp_start_listen(' -t 5 "amq.fanout/camel"')
        self.cpp_send(' --content "xoxox" "amq.fanout/camel"')
        out1 = drain1.communicate()[0]
        out2 = drain2.communicate()[0]
        self.assertTrue(out1.find("xoxox") >= 0)
        self.assertTrue(out2.find("xoxox") >= 0)

    def test_fanout_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.fanout/camel"')
        self.cpp_send(' --content "xoxox" "amq.fanout/pig"')
        out = popen.communicate()[0]
        self.assertFalse(out.find("xoxox") >= 0)

    def test_fanout_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.fanout/camel"')
        self.python_send(' "amq.fanout/pig"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)


    #  Topic exchange


    def test_topic_spout_cpp_drain_cpp_empty(self):
        self.cpp_send(' --content "xoxox" "amq.topic/hello"')
        out = self.cpp_receive(' "amq.topic"')
        self.assertFalse(out.find("xoxox") >= 0)

    def test_send_nonexistent_queue_cpp(self):
        self.assertFalse(self.cpp_send(' --content "xoxox" "xoxox"'))

    def test_topic_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.topic/hello"')
        self.cpp_send(' --content "xoxox" "amq.topic/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_topic_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.topic/hello"')
        self.cpp_send(' --content "xoxox" "amq.topic/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_topic_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.topic/hello"')
        self.python_send(' "amq.topic/hello"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_topic_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.topic"')
        self.cpp_send(' --content "xoxox" "amq.topic"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_topic_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.topic"')
        self.cpp_send(' --content "xoxox" "amq.topic"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("xoxox") >= 0)

    def test_topic_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.topic"')
        self.python_send(' "amq.topic"')
        out = popen.communicate()[0]
        self.assertTrue(out.find("Message") >= 0)

    def test_topic_drain_cpp_spout_cpp(self):
        popen = self.cpp_start_listen(' -t 5 "amq.topic/camel"')
        self.cpp_send(' --content "xoxox" "amq.topic/pig"')
        out = popen.communicate()[0]
        self.assertFalse(out.find("xoxox") >= 0)

    def test_topic_drain_cpp_drain_cpp_spout_cpp(self):
        drain1 = self.cpp_start_listen(' -t 5 "amq.topic/camel"')
        drain2 = self.cpp_start_listen(' -t 5 "amq.topic/camel"')
        self.cpp_send(' --content "xoxox" "amq.topic/camel"')
        out1 = drain1.communicate()[0]
        out2 = drain2.communicate()[0]
        self.assertTrue(out1.find("xoxox") >= 0)
        self.assertTrue(out2.find("xoxox") >= 0)

    def test_topic_drain_python_spout_cpp(self):
        popen = self.python_start_listen(' -t 5 "amq.topic/camel"')
        self.cpp_send(' --content "xoxox" "amq.topic/pig"')
        out = popen.communicate()[0]
        self.assertFalse(out.find("xoxox") >= 0)

    def test_topic_drain_cpp_spout_python(self):
        popen = self.cpp_start_listen(' -t 5 "amq.topic/camel"')
        self.python_send(' "amq.topic/pig"')
        out = popen.communicate()[0]
        self.assertFalse(out.find("Message") >= 0)

    def test_topic_news_sports_weather_cpp(self):
        news = self.cpp_start_listen(' -t 10 "amq.topic/*.news"')
        weather = self.cpp_start_listen(' -t 10 "amq.topic/*.weather"')
        sports = self.cpp_start_listen(' -t 10 "amq.topic/*.sports"')
        usa = self.cpp_start_listen(' -t 10 "amq.topic/usa.*"')
        europe  = self.cpp_start_listen(' -t 10 "amq.topic/europe.*"')
        self.cpp_send(' --content "usa.news" "amq.topic/usa.news"')
        self.cpp_send(' --content "europe.news" "amq.topic/europe.news"')
        self.cpp_send(' --content "usa.weather" "amq.topic/usa.weather"')
        self.cpp_send(' --content "europe.weather" "amq.topic/europe.weather"')
        self.cpp_send(' --content "usa.sports" "amq.topic/usa.sports"')
        self.cpp_send(' --content "europe.sports" "amq.topic/europe.sports"')
        out = news.communicate()[0]
        self.assertTrue(out.find("usa.news") >= 0)        
        self.assertTrue(out.find("europe.news") >= 0)        
        out = weather.communicate()[0]
        self.assertTrue(out.find("usa.weather") >= 0)        
        self.assertTrue(out.find("europe.weather") >= 0)        
        out = sports.communicate()[0]
        self.assertTrue(out.find("usa.sports") >= 0)        
        self.assertTrue(out.find("europe.sports") >= 0)        
        out = usa.communicate()[0]
        self.assertTrue(out.find("usa.news") >= 0)        
        self.assertTrue(out.find("usa.sports") >= 0)        
        self.assertTrue(out.find("usa.weather") >= 0)        
        out = europe.communicate()[0]
        self.assertTrue(out.find("europe.news") >= 0)        
        self.assertTrue(out.find("europe.sports") >= 0)        
        self.assertTrue(out.find("europe.weather") >= 0)        

#    def test_topic_news_sports_weather_python(self):
#    def test_topic_news_sports_weather_python_cpp(self):
#    def test_topic_news_sports_weather_cpp_python(self):

    #  Request / Response

    def test_jabberwocky_cpp(self):
        args = shlex.split(cpp_examples_path + 'server')
        server = subprocess.Popen(args, stdout=subprocess.PIPE)
        args = shlex.split(cpp_examples_path + 'client')
        client = subprocess.Popen(args, stdout=subprocess.PIPE)
        out = client.communicate()[0]
        self.assertTrue(out.find("BRILLIG") >= 0)
        server.terminate()


    #  XML Exchange

    #  Map messages

    ## java - java
    ## java - cpp
    ## java - python

    ## cpp - java
    ## cpp - cpp
    ## cpp - python

    ## python - java
    ## python - cpp
    ## python - python


if __name__ == '__main__':
    unittest.main()

