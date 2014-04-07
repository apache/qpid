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

import os, signal, sys, time, imp, re, subprocess, glob, random, logging, shutil, math, unittest, random
import traceback
from brokertest import *
from threading import Thread, Lock, Condition
from logging import getLogger, WARN, ERROR, DEBUG, INFO
from qpidtoollibs import BrokerAgent

log = getLogger(__name__)

class LogLevel:
    """
    Temporarily change the log settings on the root logger.
    Used to suppress expected WARN messages from the python client.
    """
    def __init__(self, level):
        self.save_level = getLogger().getEffectiveLevel()
        getLogger().setLevel(level)

    def restore(self):
        getLogger().setLevel(self.save_level)

class QmfAgent(object):
    """Access to a QMF broker agent."""
    def __init__(self, address, **kwargs):
        self._connection = qm.Connection.establish(
            address, client_properties={"qpid.ha-admin":1}, **kwargs)
        self._agent = BrokerAgent(self._connection)

    def queues(self):
        return [q.values['name'] for q in self._agent.getAllQueues()]

    def repsub_queue(self, sub):
        """If QMF subscription sub is a replicating subscription return
        the name of the replicated queue, else return None"""
        session = self.getSession(sub.sessionRef)
        if not session: return None
        m = re.search("qpid.ha-q:(.*)\.", session.name)
        return m and m.group(1)

    def repsub_queues(self):
        """Return queue names for all replicating subscriptions"""
        return filter(None, [self.repsub_queue(s) for s in self.getAllSubscriptions()])

    def tx_queues(self):
        """Return names of all tx-queues"""
        return [q for q in self.queues() if q.startswith("qpid.ha-tx")]

    def __getattr__(self, name):
        a = getattr(self._agent, name)
        return a

class Credentials(object):
    """SASL credentials: username, password, and mechanism"""
    def __init__(self, username, password, mechanism):
        (self.username, self.password, self.mechanism) = (username, password, mechanism)

    def __str__(self): return "Credentials%s"%(self.tuple(),)

    def tuple(self): return (self.username, self.password, self.mechanism)

    def add_user(self, url): return "%s/%s@%s"%(self.username, self.password, url)

class HaPort:
    """Many HA tests need to allocate a broker port dynamically and then kill
    and restart a broker on that same port multiple times. qpidd --port=0 only
    ensures the port for the initial broker process, subsequent brokers re-using
    the same port may fail with "address already in use".

    HaPort binds and listens to the port and returns a file descriptor to pass
    to qpidd --socket-fd. It holds on to the port untill the end of the test so
    the broker can restart multiple times.
    """

    def __init__(self, test, port=0):
        """Bind and listen to port. port=0 allocates a port dynamically.
        self.port is the allocated port, self.fileno is the file descriptor for
        qpid --socket-fd."""

        self.test = test
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.bind(("", port))
        self.socket.listen(5)
        self.port = self.socket.getsockname()[1]
        self.fileno = self.socket.fileno()
        self.stopped = False
        test.teardown_add(self) # Stop during test.tearDown

    def teardown(self):             # Called in tearDown
        if not self.stopped:
            self.stopped = True
            self.socket.shutdown(socket.SHUT_RDWR)
            self.socket.close()

    def __str__(self): return "HaPort<port:%s, fileno:%s>"%(self.port, self.fileno)


class HaBroker(Broker):
    """Start a broker with HA enabled
    @param client_cred: (user, password, mechanism) for admin clients started by the HaBroker.
    """

    heartbeat=5

    def __init__(self, test, ha_port=None, args=[], brokers_url=None, ha_cluster=True,
                 ha_replicate="all", client_credentials=None, **kwargs):
        assert BrokerTest.ha_lib, "Cannot locate HA plug-in"
        ha_port = ha_port or HaPort(test)
        args = copy(args)
        args += ["--load-module", BrokerTest.ha_lib,
                 # Non-standard settings for faster tests.
                 "--link-maintenance-interval=0.1",
                 # Heartbeat and negotiate time are needed so that a broker wont
                 # stall on an address that doesn't currently have a broker running.
                 "--link-heartbeat-interval=%s"%(HaBroker.heartbeat),
                 "--max-negotiate-time=1000",
                 "--ha-cluster=%s"%ha_cluster]
        # Add default --log-enable arguments unless args already has --log arguments.
        if not [l for l in args if l.startswith("--log")]:
            args += ["--log-enable=info+", "--log-enable=debug+:ha::"]
        if ha_replicate is not None:
            args += [ "--ha-replicate=%s"%ha_replicate ]
        if brokers_url: args += [ "--ha-brokers-url", brokers_url ]
        # Set up default ACL
        acl=os.path.join(os.getcwd(), "unrestricted.acl")
        if not os.path.exists(acl):
            aclf=file(acl,"w")
            aclf.write("""
acl allow all all
 """)
            aclf.close()
        if not "--acl-file" in args:
            args += [ "--acl-file", acl, ]
        args += ["--socket-fd=%s"%ha_port.fileno, "--listen-disable=tcp"]
        Broker.__init__(self, test, args, port=ha_port.port, **kwargs)
        self.qpid_ha_path=os.path.join(os.getenv("PYTHON_COMMANDS"), "qpid-ha")
        assert os.path.exists(self.qpid_ha_path)
        self.qpid_config_path=os.path.join(os.getenv("PYTHON_COMMANDS"), "qpid-config")
        assert os.path.exists(self.qpid_config_path)
        self.qpid_ha_script=import_script(self.qpid_ha_path)
        self._agent = None
        self.client_credentials = client_credentials
        self.ha_port = ha_port

    def __repr__(self): return "<HaBroker:%s:%d>"%(self.log, self.port())

    def qpid_ha(self, args):
        try:
            cred = self.client_credentials
            url = self.host_port()
            if cred:
                url =cred.add_user(url)
                args = args + ["--sasl-mechanism", cred.mechanism]
            self.qpid_ha_script.main_except(["", "-b", url]+args)
        except Exception, e:
            raise Exception("Error in qpid_ha -b %s %s: %s"%(url, args,e))

    def promote(self): self.ready(); self.qpid_ha(["promote"])
    def set_public_url(self, url): self.qpid_ha(["set", "--public-url", url])
    def set_brokers_url(self, url): self.qpid_ha(["set", "--brokers-url", url]);
    def replicate(self, from_broker, queue): self.qpid_ha(["replicate", from_broker, queue])
    @property
    def agent(self):
        if not self._agent:
            cred = self.client_credentials
            if cred:
                self._agent = QmfAgent(cred.add_user(self.host_port()), sasl_mechanisms=cred.mechanism)
            else:
                self._agent = QmfAgent(self.host_port())
        return self._agent

    def qmf(self):
        hb = self.agent.getHaBroker()
        hb.update()
        return hb

    def ha_status(self): return self.qmf().status

    def wait_status(self, status, timeout=5):
        def try_get_status():
            self._status = "<unknown>"
            # Ignore ConnectionError, the broker may not be up yet.
            try:
                self._status = self.ha_status()
                return self._status == status;
            except qm.ConnectionError: return False
        assert retry(try_get_status, timeout=timeout), "%s expected=%r, actual=%r"%(
            self, status, self._status)

    def wait_queue(self, queue, timeout=1, msg="wait_queue"):
        """ Wait for queue to be visible via QMF"""
        agent = self.agent
        assert retry(lambda: agent.getQueue(queue) is not None, timeout=timeout), msg+"queue %s not present"%queue

    def wait_no_queue(self, queue, timeout=1, msg="wait_no_queue"):
        """ Wait for queue to be invisible via QMF"""
        agent = self.agent
        assert retry(lambda: agent.getQueue(queue) is None, timeout=timeout), "%s: queue %s still present"%(msg,queue)

    # TODO aconway 2012-05-01: do direct python call to qpid-config code.
    def qpid_config(self, args):
        assert subprocess.call(
            [self.qpid_config_path, "--broker", self.host_port()]+args,
            stdout=1, stderr=subprocess.STDOUT
            ) == 0

    def config_replicate(self, from_broker, queue):
        self.qpid_config(["add", "queue", "--start-replica", from_broker, queue])

    def config_declare(self, queue, replication):
        self.qpid_config(["add", "queue", queue, "--replicate", replication])

    def connect_admin(self, **kwargs):
        cred = self.client_credentials
        if cred:
            return Broker.connect(
                self, client_properties={"qpid.ha-admin":1},
                username=cred.username, password=cred.password, sasl_mechanisms=cred.mechanism,
                **kwargs)
        else:
            return Broker.connect(self, client_properties={"qpid.ha-admin":1}, **kwargs)

    def wait_address(self, address):
        """Wait for address to become valid on the broker."""
        bs = self.connect_admin().session()
        try: wait_address(bs, address)
        finally: bs.connection.close()

    def wait_backup(self, address): self.wait_address(address)

    def browse(self, queue, timeout=0, transform=lambda m: m.content):
        c = self.connect_admin()
        try:
            return browse(c.session(), queue, timeout, transform)
        finally: c.close()

    def assert_browse(self, queue, expected, **kwargs):
        """Verify queue contents by browsing."""
        bs = self.connect().session()
        try:
            wait_address(bs, queue)
            assert_browse_retry(bs, queue, expected, **kwargs)
        finally: bs.connection.close()

    def assert_browse_backup(self, queue, expected, **kwargs):
        """Combines wait_backup and assert_browse_retry."""
        bs = self.connect_admin().session()
        try:
            wait_address(bs, queue)
            assert_browse_retry(bs, queue, expected, **kwargs)
        finally: bs.connection.close()

    def assert_connect_fail(self):
        try:
            self.connect()
            self.test.fail("Expected qm.ConnectionError")
        except qm.ConnectionError: pass

    def try_connect(self):
        try: return self.connect()
        except qm.ConnectionError: return None

    def ready(self, *args, **kwargs):
        if not 'client_properties' in kwargs: kwargs['client_properties'] = {}
        kwargs['client_properties']['qpid.ha-admin'] = True
        return Broker.ready(self, *args, **kwargs)

    def kill(self, final=True):
        if final: self.ha_port.teardown()
        self._agent = None
        return Broker.kill(self)


class HaCluster(object):
    _cluster_count = 0

    def __init__(self, test, n, promote=True, wait=True, args=[], s_args=[], **kwargs):
        """Start a cluster of n brokers.

        @test: The test being run
        @n: start n brokers
        @promote: promote self[0] to primary
        @wait: wait for primary active and backups ready. Ignored if promote=False
        @args: args for all brokers in the cluster.
        @s_args: args for specific brokers: s_args[i] for broker i.
        """
        self.test = test
        self.args = copy(args)
        self.s_args = copy(s_args)
        self.kwargs = kwargs
        self._ports = [HaPort(test) for i in xrange(n)]
        self._set_url()
        self._brokers = []
        self.id = HaCluster._cluster_count
        self.broker_id = 0
        HaCluster._cluster_count += 1
        for i in xrange(n): self.start()
        if promote:
            self[0].promote()
            if wait:
                self[0].wait_status("active")
                for b in self[1:]: b.wait_status("ready")

    def next_name(self):
        name="cluster%s-%s"%(self.id, self.broker_id)
        self.broker_id += 1
        return name

    def _ha_broker(self, i, name):
        args = self.args
        if i < len(self.s_args): args += self.s_args[i]
        ha_port = self._ports[i]
        b = HaBroker(ha_port.test, ha_port, brokers_url=self.url, name=name,
                     args=args, **self.kwargs)
        b.ready(timeout=5)
        return b

    def start(self):
        """Start a new broker in the cluster"""
        i = len(self)
        assert i <= len(self._ports)
        if i == len(self._ports): # Adding new broker after cluster init
            self._ports.append(HaPort(self.test))
            self._set_url()
            self._update_urls()
        b = self._ha_broker(i, self.next_name())
        self._brokers.append(b)
        return b

    def _set_url(self):
        self.url = ",".join("127.0.0.1:%s"%(p.port) for p in self._ports)

    def _update_urls(self):
        for b in self:
            b.set_brokers_url(self.url)
            b.set_public_url(self.url)

    def connect(self, i, **kwargs):
        """Connect with reconnect_urls"""
        return self[i].connect(reconnect=True, reconnect_urls=self.url.split(","), **kwargs)

    def kill(self, i, promote_next=True, final=True):
        """Kill broker i, promote broker i+1"""
        self[i].kill(final=final)
        if promote_next: self[(i+1) % len(self)].promote()

    def restart(self, i):
        """Start a broker with the same port, name and data directory. It will get
        a separate log file: foo.n.log"""
        if self._ports[i].stopped: raise Exception("Restart after final kill: %s"%(self))
        b = self._brokers[i]
        self._brokers[i] = self._ha_broker(i, b.name)
        self._brokers[i].ready()

    def bounce(self, i, promote_next=True):
        """Stop and restart a broker in a cluster."""
        if (len(self) == 1):
            self.kill(i, promote_next=False, final=False)
            self.restart(i)
            self[i].ready()
            if promote_next: self[i].promote()
        else:
            self.kill(i, promote_next, final=False)
            self.restart(i)

    # Behave like a list of brokers.
    def __len__(self): return len(self._brokers)
    def __getitem__(self,index): return self._brokers[index]
    def __iter__(self): return self._brokers.__iter__()


def wait_address(session, address):
    """Wait for an address to become valid."""
    def check():
        try: session.sender(address); return True
        except  qm.NotFound: return False
    assert retry(check), "Timed out waiting for address %s"%(address)

def valid_address(session, address):
    """Test if an address is valid"""
    try:
        session.receiver(address)
        return True
    except qm.NotFound: return False


