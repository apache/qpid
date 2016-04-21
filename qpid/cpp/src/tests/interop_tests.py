#!/usr/bin/env python
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
A set of tests that can be run against a foreign AMQP 1.0 broker.

RUNNING WITH A FOREIGN BROKER:

1. Start the broker
2. Create persistent queues named: interop-a interop-b interop-q tx-1 tx-2
3. Export the environment variable QPID_INTEROP_URL with the URL to connect to your broker
   in the form [user[:password]@]host[:port]
4. From the build directory run this test:
   ctest -VV -R interop_tests

If QPID_INTEROP_URL is not set, a qpidd broker will be started for the test.
"""

import os, sys, shutil, subprocess
import qpid_messaging as qm
from brokertest import *

URL='QPID_INTEROP_URL'

class InteropTest(BrokerTest):

    def setUp(self):
        super(InteropTest, self).setUp()
        self.url = os.environ[URL]
        self.connect_opts = ['--broker', self.url, '--connection-options', '{protocol:amqp1.0}']

    def connect(self, **kwargs):
        """Python connection to interop URL"""
        c = qm.Connection.establish(self.url, protocol='amqp1.0', **kwargs)
        self.teardown_add(c)
        return c

    def drain(self, queue, connection=None):
        """
        Drain a queue to make sure it is empty. Throw away the messages.
        """
        c = connection or self.connect()
        r = c.session().receiver(queue)
        try:
            while True:
                r.fetch(timeout=0)
                r.session.acknowledge()
        except qm.Empty:
            pass
        r.close()

    def clear_queue(self, queue, connection=None, properties=None, durable=False):
        """
        Make empty queue, prefix with self.id(). Create if needed, drain if needed
        @return queue name.
        """
        queue = "interop-%s" % queue
        c = connection or self.connect()
        props = {'create':'always'}
        if durable: props['node'] = {'durable':True}
        if properties: props.update(properties)
        self.drain("%s;%s" % (queue, props), c)
        return queue


class SimpleTest(InteropTest):
    """Simple test to check the broker is responding."""

    def test_send_receive_python(self):
        c = self.connect()
        q = self.clear_queue('q', c)
        s = c.session()
        s.sender(q).send('foo')
        self.assertEqual('foo', s.receiver(q).fetch().content)

    def test_send_receive_cpp(self):
        q = self.clear_queue('q')
        args = ['-b', self.url, '-a', q]
        self.check_output(['qpid-send', '--content-string=cpp_foo'] + args)
        self.assertEqual('cpp_foo', self.check_output(['qpid-receive'] + args).strip())


class PythonTxTest(InteropTest):

    def tx_simple_setup(self):
        """Start a transaction, remove messages from queue a, add messages to queue b"""
        c = self.connect()
        qa, qb = self.clear_queue('a', c, durable=True), self.clear_queue('b', c, durable=True)

        # Send messages to a, no transaction.
        sa = c.session().sender(qa+";{create:always,node:{durable:true}}")
        tx_msgs =  ['x', 'y', 'z']
        for m in tx_msgs: sa.send(qm.Message(content=m, durable=True))

        # Receive messages from a, in transaction.
        tx = c.session(transactional=True)
        txr = tx.receiver(qa)
        self.assertEqual(tx_msgs, [txr.fetch(1).content for i in xrange(3)])
        tx.acknowledge()

        # Send messages to b, transactional, mixed with non-transactional.
        sb = c.session().sender(qb+";{create:always,node:{durable:true}}")
        txs = tx.sender(qb)
        msgs = [str(i) for i in xrange(3)]
        for tx_m, m in zip(tx_msgs, msgs):
            txs.send(tx_m);
            sb.send(m)
        tx.sync()
        return tx, qa, qb

    def test_tx_simple_commit(self):
        tx, qa, qb = self.tx_simple_setup()
        s = self.connect().session()
        assert_browse(s, qa, [])
        assert_browse(s, qb, ['0', '1', '2'])
        tx.commit()
        assert_browse(s, qa, [])
        assert_browse(s, qb, ['0', '1', '2', 'x', 'y', 'z'])

    def test_tx_simple_rollback(self):
        tx, qa, qb = self.tx_simple_setup()
        s = self.connect().session()
        assert_browse(s, qa, [])
        assert_browse(s, qb, ['0', '1', '2'])
        tx.rollback()
        assert_browse(s, qa, ['x', 'y', 'z'])
        assert_browse(s, qb, ['0', '1', '2'])

    def test_tx_sequence(self):
        tx = self.connect().session(transactional=True)
        notx = self.connect().session()
        q = self.clear_queue('q', tx.connection, durable=True)
        s = tx.sender(q)
        r = tx.receiver(q)
        s.send('a')
        tx.commit()
        assert_browse(notx, q, ['a'])
        s.send('b')
        tx.commit()
        assert_browse(notx, q, ['a', 'b'])
        self.assertEqual('a', r.fetch().content)
        tx.acknowledge();
        tx.commit()
        assert_browse(notx, q, ['b'])
        s.send('z')
        tx.rollback()
        assert_browse(notx, q, ['b'])
        self.assertEqual('b', r.fetch().content)
        tx.acknowledge();
        tx.rollback()
        assert_browse(notx, q, ['b'])


class CppTxTest(InteropTest):

    def test_txtest2(self):
        self.popen(["qpid-txtest2"]  + self.connect_opts).assert_exit_ok()

    def test_send_receive(self):
        q = self.clear_queue('q', durable=True)
        sender = self.popen(["qpid-send",
                             "--address", q,
                             "--messages=100",
                             "--tx=10",
                             "--durable=yes"] + self.connect_opts)
        receiver = self.popen(["qpid-receive",
                               "--address", q,
                               "--messages=90",
                               "--timeout=10",
                               "--tx=10"] + self.connect_opts)
        sender.assert_exit_ok()
        receiver.assert_exit_ok()
        expect = [long(i) for i in range(91, 101)]
        sn = lambda m: m.properties["sn"]
        assert_browse(self.connect().session(), q, expect, transform=sn)


if __name__ == "__main__":
    from env import *
    outdir = "interop_tests.tmp"
    shutil.rmtree(outdir, True)
    cmd = ["qpid-python-test", "-m", "interop_tests", "-DOUTDIR=%s"%outdir] + sys.argv[1:]
    if "QPID_PORT" in os.environ: del os.environ["QPID_PORT"]
    if  os.environ.get(URL):
        os.execvp(cmd[0], cmd)
    else:
        dir = os.getcwd()
        class StartBroker(BrokerTest):
            def start_qpidd(self): pass
        test = StartBroker('start_qpidd')
        class Config:
            def __init__(self):
                self.defines = { 'OUTDIR': outdir }
        test.configure(Config())
        test.setUp()
        os.environ[URL] = test.broker().host_port()
        os.chdir(dir)
        p = subprocess.Popen(cmd)
        status = p.wait()
        test.tearDown()
        sys.exit(status)
