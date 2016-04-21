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

import sys
import os
import imp
from qpid.testlib import TestBase010
from qpid.datatypes import Message
from qpid.queue import Empty
from time import sleep

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

def checkenv(name):
    value = os.getenv(name)
    if not value: raise Exception("Environment variable %s is not set" % name)
    return value

class CliTests(TestBase010):

    def remote_host(self):
        return self.defines.get("remote-host", "localhost")

    def remote_port(self):
        return int(self.defines["remote-port"])

    def cli_dir(self):
        return self.defines["cli-dir"]

    def makeQueue(self, qname, arguments, api=False):
        if api:
            ret = self.qpid_config_api(" add queue " + qname + " " + arguments)
        else:
            ret = os.system(self.qpid_config_command(" add queue " + qname  + " " + arguments))

        self.assertEqual(ret, 0)
        queue = self.broker_access.getQueue(qname)
        if queue:
            return queue
        assert False

    def test_queue_params(self):
        self.startBrokerAccess()
        queue1 = self.makeQueue("test_queue_params1", "--limit-policy none")
        queue2 = self.makeQueue("test_queue_params2", "--limit-policy reject")
        queue3 = self.makeQueue("test_queue_params3", "--limit-policy ring")

        LIMIT = "qpid.policy_type"
        assert LIMIT not in queue1.arguments
        self.assertEqual(queue2.arguments[LIMIT], "reject")
        self.assertEqual(queue3.arguments[LIMIT], "ring")

        queue4 = self.makeQueue("test_queue_params4", "--lvq-key lkey")

        LVQKEY = "qpid.last_value_queue_key"

        assert LVQKEY not in queue3.arguments
        assert LVQKEY     in queue4.arguments
        assert queue4.arguments[LVQKEY] == "lkey"

    def test_queue_params_api(self):
        self.startBrokerAccess()
        queue1 = self.makeQueue("test_queue_params_api1", "--limit-policy none", True)
        queue2 = self.makeQueue("test_queue_params_api2", "--limit-policy reject", True)
        queue3 = self.makeQueue("test_queue_params_api3", "--limit-policy ring", True)

        LIMIT = "qpid.policy_type"
        assert LIMIT not in queue1.arguments
        self.assertEqual(queue2.arguments[LIMIT], "reject")
        self.assertEqual(queue3.arguments[LIMIT], "ring")

        queue4 = self.makeQueue("test_queue_params_api4", "--lvq-key lkey")

        LVQKEY = "qpid.last_value_queue_key"

        assert LVQKEY not in queue3.arguments
        assert LVQKEY     in queue4.arguments
        assert queue4.arguments[LVQKEY] == "lkey"


    def test_qpid_config(self):
        self.startBrokerAccess();
        qname = "test_qpid_config"

        ret = os.system(self.qpid_config_command(" add queue " + qname))
        self.assertEqual(ret, 0)
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                self.assertEqual(queue.durable, False)
                found = True
        self.assertEqual(found, True)

        ret = os.system(self.qpid_config_command(" del queue " + qname))
        self.assertEqual(ret, 0)
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                found = True
        self.assertEqual(found, False)

    def test_qpid_config_del_nonempty_queue(self):
        self.startBrokerAccess();
        qname = "test_qpid_config_del"

        ret = os.system(self.qpid_config_command(" add queue " + qname))
        self.assertEqual(ret, 0)
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                self.assertEqual(queue.durable, False)
                found = True
        self.assertEqual(found, True)

        self.startBrokerAccess()

        sess = self.broker_conn.session()
        tx = sess.sender(qname)
        tx.send("MESSAGE")

        ret = os.system(self.qpid_config_command(" del queue " + qname))
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                found = True
        self.assertEqual(found, True)

        ret = os.system(self.qpid_config_command(" del queue " + qname + " --force"))
        self.assertEqual(ret, 0)
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                found = True
        self.assertEqual(found, False)


    def test_qpid_config_api(self):
        self.startBrokerAccess();
        qname = "test_qpid_config_api"

        ret = self.qpid_config_api(" add queue " + qname)
        self.assertEqual(ret, 0)
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                self.assertEqual(queue.durable, False)
                found = True
        self.assertEqual(found, True)

        ret = self.qpid_config_api(" del queue " + qname)
        self.assertEqual(ret, 0)
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                found = True
        self.assertEqual(found, False)


    def test_qpid_config_sasl_plain_expect_succeed(self):
        self.startBrokerAccess();
        qname = "test_qpid_config_sasl_plain_expect_succeed"
        cmd = " --sasl-mechanism PLAIN -b guest/guest@localhost:"+str(self.broker.port) + " add queue " + qname
        ret = self.qpid_config_api(cmd)
        self.assertEqual(ret, 0)

    def test_qpid_config_sasl_plain_expect_fail(self):
        """Fails because no user name and password is supplied"""
        self.startBrokerAccess();
        qname = "test_qpid_config_sasl_plain_expect_fail"
        cmd = " --sasl-mechanism PLAIN -b localhost:"+str(self.broker.port) + " add queue " + qname
        ret = self.qpid_config_api(cmd)
        assert ret != 0

        # helpers for some of the test methods
    def helper_find_exchange(self, xchgname, typ, expected=True):
        xchgs = self.broker_access.getAllExchanges()
        found = False
        for xchg in xchgs:
            if xchg.name == xchgname:
                if typ:
                    self.assertEqual(xchg.type, typ)
                found = True
        self.assertEqual(found, expected)

    def helper_create_exchange(self, xchgname, typ="direct", opts=""):
        foo = self.qpid_config_command(opts + " add exchange " + typ + " " + xchgname)  
        # print foo
        ret = os.system(foo)
        self.assertEqual(ret, 0)
        self.helper_find_exchange(xchgname, typ, True)

    def helper_destroy_exchange(self, xchgname):
        foo = self.qpid_config_command(" del exchange " + xchgname) 
        # print foo
        ret = os.system(foo)
        self.assertEqual(ret, 0)
        self.helper_find_exchange(xchgname, False, expected=False)

    def helper_find_queue(self, qname, expected=True):
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                self.assertEqual(queue.durable, False)
                found = True
        self.assertEqual(found, expected)

    def helper_create_queue(self, qname):
        foo = self.qpid_config_command(" add queue " + qname)
        # print foo
        ret = os.system(foo)
        self.assertEqual(ret, 0)
        self.helper_find_queue(qname, True)

    def helper_destroy_queue(self, qname):
        foo = self.qpid_config_command(" del queue " + qname)
        # print foo
        ret = os.system(foo)
        self.assertEqual(ret, 0)
        self.helper_find_queue(qname, False)

        # test the bind-queue-to-header-exchange functionality
    def test_qpid_config_headers(self):
        self.startBrokerAccess();
        qname = "test_qpid_config"
        xchgname = "test_xchg"

        # first create a header xchg
        self.helper_create_exchange(xchgname, typ="headers")

        # create the queue
        self.helper_create_queue(qname)

        # now bind the queue to the xchg
        foo = self.qpid_config_command(" bind " + xchgname + " " + qname + 
                                     " key all foo=bar baz=quux")
        # print foo
        ret = os.system(foo)
        self.assertEqual(ret, 0)

        # he likes it, mikey.  Ok, now tear it all down.  first the binding
        ret = os.system(self.qpid_config_command(" unbind " + xchgname + " " + qname +
                                     " key"))
        self.assertEqual(ret, 0)

        # then the queue 
        self.helper_destroy_queue(qname)

        # then the exchange
        self.helper_destroy_exchange(xchgname)


    def test_qpid_config_xml(self):
        self.startBrokerAccess();
        qname = "test_qpid_config"
        xchgname = "test_xchg"

        # first create a header xchg
        self.helper_create_exchange(xchgname, typ="xml")

        # create the queue
        self.helper_create_queue(qname)

        # now bind the queue to the xchg
        xquery_file = self.defines["xquery-file"]
        foo = self.qpid_config_command("-f " + xquery_file + " bind " + xchgname + " " + qname)
        # print foo
        ret = os.system(foo)
        self.assertEqual(ret, 0)

        # he likes it, mikey.  Ok, now tear it all down.  first the binding
        ret = os.system(self.qpid_config_command(" unbind " + xchgname + " " + qname +
                                     " key"))
        self.assertEqual(ret, 0)

        # then the queue 
        self.helper_destroy_queue(qname)

        # then the exchange
        self.helper_destroy_exchange(xchgname)

    def test_qpid_config_durable(self):
        self.startBrokerAccess();
        qname = "test_qpid_config"

        ret = os.system(self.qpid_config_command(" add queue --durable " + qname))
        self.assertEqual(ret, 0)
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                self.assertEqual(queue.durable, True)
                found = True
        self.assertEqual(found, True)

        ret = os.system(self.qpid_config_command(" del queue " + qname))
        self.assertEqual(ret, 0)
        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qname:
                found = True
        self.assertEqual(found, False)

    def test_qpid_config_altex(self):
        self.startBrokerAccess();
        exName = "testalt"
        qName = "testqalt"
        altName = "amq.direct"

        ret = os.system(self.qpid_config_command(" add exchange topic %s --alternate-exchange=%s" % (exName, altName)))
        self.assertEqual(ret, 0)

        exchanges = self.broker_access.getAllExchanges()
        found = False
        for exchange in exchanges:
            if exchange.name == altName:
                self.assertEqual(exchange.altExchange, None)

            if exchange.name == exName:
                found = True
                if not exchange.altExchange:
                    self.fail("Alternate exchange not set")
                self.assertEqual(exchange.altExchange, altName)
        self.assertEqual(found, True)

        ret = os.system(self.qpid_config_command(" add queue %s --alternate-exchange=%s" % (qName, altName)))
        self.assertEqual(ret, 0)

        ret = os.system(self.qpid_config_command(" queues"))
        self.assertEqual(ret, 0)

        queues = self.broker_access.getAllQueues()
        found = False
        for queue in queues:
            if queue.name == qName:
                found = True
                if not queue.altExchange:
                    self.fail("Alternate exchange not set")
                self.assertEqual(queue.altExchange, altName)
        self.assertEqual(found, True)

    def test_qpid_config_list_queues_arguments(self):
        """
        Test to verify that when the type of a policy limit is
        actually a string (though still a valid value), it does not
        upset qpid-config
        """
        self.startBrokerAccess();

        names = ["queue_capacity%s" % (i) for i in range(1, 6)]
        for name in names:
            self.session.queue_declare(queue=name, exclusive=True,
                                       arguments={'qpid.max_count' : str(i), 'qpid.max_size': '100'})

        output = os.popen(self.qpid_config_command(" queues")).readlines()
        queues = [line.split()[0] for line in output[2:len(output)]] #ignore first two lines (header)

        for name in names:
            assert name in queues, "%s not in %s" % (name, queues)

    def test_qpid_route(self):
        self.startBrokerAccess();

        command = self.cli_dir() + "/qpid-route dynamic add guest/guest@localhost:%d %s:%d amq.topic" %\
            (self.broker.port, self.remote_host(), self.remote_port())
        ret = os.system(command)
        self.assertEqual(ret, 0)

        links = self.broker_access.getAllLinks()
        found = False
        for link in links:
            if link.port == self.remote_port():
                found = True
        self.assertEqual(found, True)

    def test_qpid_route_api(self):
        self.startBrokerAccess();

        ret = self.qpid_route_api("dynamic add "
                                  + "guest/guest@localhost:"+str(self.broker.port) + " "
                                  + str(self.remote_host())+":"+str(self.remote_port()) + " "
                                  +"amq.direct")

        self.assertEqual(ret, 0)

        links = self.broker_access.getAllLinks()
        found = False
        for link in links:
            if link.port == self.remote_port():
                found = True
        self.assertEqual(found, True)


    def test_qpid_route_api(self):
        self.startBrokerAccess();

        ret = self.qpid_route_api("dynamic add "
                                  + " --client-sasl-mechanism PLAIN "
                                  + "guest/guest@localhost:"+str(self.broker.port) + " "
                                  + str(self.remote_host())+":"+str(self.remote_port()) + " "
                                  +"amq.direct")

        self.assertEqual(ret, 0)

        links = self.broker_access.getAllLinks()
        found = False
        for link in links:
            if link.port == self.remote_port():
                found = True
        self.assertEqual(found, True)

    def test_qpid_route_api_expect_fail(self):
        self.startBrokerAccess();

        ret = self.qpid_route_api("dynamic add "
                                  + " --client-sasl-mechanism PLAIN "
                                  + "localhost:"+str(self.broker.port) + " "
                                  + str(self.remote_host())+":"+str(self.remote_port()) + " "
                                  +"amq.direct")
        assert ret != 0


    def getProperty(self, msg, name):
        for h in msg.headers:
            if hasattr(h, name): return getattr(h, name)
        return None            

    def getAppHeader(self, msg, name):
        headers = self.getProperty(msg, "application_headers")
        if headers:
            return headers[name]
        return None

    def qpid_config_command(self, arg = ""):
        return self.cli_dir() + "/qpid-config -b localhost:%d" % self.broker.port + " " + arg

    def qpid_config_api(self, arg = ""):
        path = os.path.join(os.getenv("SOURCE_DIR"), "management", "python",
                            "bin", "qpid-config")
        script = import_script(path)
        broker = ["-b", "localhost:"+str(self.broker.port)]
        return script.main(broker + arg.split())

    def qpid_route_api(self, arg = ""):
        path = os.path.join(os.getenv("SOURCE_DIR"), "management", "python",
                            "bin", "qpid-route")
        script = import_script(path)
        return script.main(arg.split())
