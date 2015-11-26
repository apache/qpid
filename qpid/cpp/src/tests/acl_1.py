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

from qpid.tests.messaging.implementation import *
from qpid.tests.messaging import VersionTest
from mgmt_1 import Mgmt

class Policy(object):
    def __init__(self):
        self.lines = []


    def str(self):
        return '\n'.join(lines)

class AclCtrl(object):
    def __init__(self, broker, path):
        self.policy = path
        self.original = self.read()
        conn = Connection.establish(broker, protocol='amqp0-10', username='admin', password='admin', sasl_mechanism='PLAIN')
        self.agent = Mgmt(conn)
        self.agent.create('queue', 'acl_test_queue')
        self.lines = []

    def deny(self, user=None, *args):
        self._add_rule('deny', user, *args)
        return self

    def allow(self, user=None, *args):
        self._add_rule('allow', user, *args)
        return self

    def apply(self, allow_admin=True):
        if allow_admin:
            # admin users needs permission to send a qmf message to
            # reload policy
            self.lines.insert(0, 'acl allow admin@QPID all all')
        self.specify("\n".join(self.lines))
        return self

    def dump(self):
        print "\n".join(self.lines)
        return self

    def clear(self):
        self.lines = []
        return self

    def _add_rule(self, deny_or_allow, user=None, *args):
        elements = ['acl', deny_or_allow]
        if user:
            elements.append("%s@QPID" % user)
        else:
            elements.append('all')
        if len(args) > 0:
            for a in args:
                elements.append(a)
        else:
            elements.append('all')
        self.lines.append(' '.join(elements))

    def read(self):
        f = open(self.policy,'r')
        content = f.read()
        f.close()
        return content

    def specify(self, acl):
        f = open(self.policy,'w')
        f.write(acl)
        f.close()
        self.agent.reload_acl_file()

    def restore(self):
        self.agent.delete('queue', 'acl_test_queue')
        self.specify(self.original)
        self.agent.close()


class Acl_AMQP1_Tests (VersionTest):
    """
    Tests for acl when accessing qpidd via AMQP 1.0
    """
    def auth_session(self, user):
        conn = Connection.establish(self.config.broker, protocol='amqp1.0', username=user, password=user, sasl_mechanism='PLAIN', container_id=user)
        return conn.session()

    def setUp(self):
        VersionTest.setup(self)
        self.acl = AclCtrl(self.config.broker, self.config.defines.get("policy_file"))
        self.alice = self.auth_session('alice')
        self.bob = self.auth_session('bob')

    def tearDown(self):
        self.bob.connection.close()
        self.alice.connection.close()
        self.acl.restore()
        VersionTest.teardown(self)

    def test_deny_sender_to_exchange(self):
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.sender("amq.topic")
            assert False, "anonymous should not be allowed to create sender to amq.topic"
        except UnauthorizedAccess: pass
        try:
            self.bob.sender("amq.topic")
            assert False, "bob should not be allowed to create sender to amq.topic"
        except UnauthorizedAccess: pass
        self.alice.sender("amq.topic")

    def test_deny_sender_to_queue(self):
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.sender("acl_test_queue")
            assert False, "anonymous shound not be allowed to create sender to acl_test_queue"
        except UnauthorizedAccess: pass
        try:
            self.bob.sender("acl_test_queue")
            assert False, "bob should not be allowed to create sender to acl_test_queue"
        except UnauthorizedAccess: pass
        self.alice.sender("acl_test_queue")

    def test_deny_sender_to_unknown(self):
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.sender("unknown")
            assert False, "anonymous should not be allowed to create sender to non-existent node"
        except UnauthorizedAccess: pass
        try:
            self.bob.sender("unknown")
            assert False, "bob should not be allowed to create sender to unknown"
        except UnauthorizedAccess: pass
        try:
            self.alice.sender("unknown")
        except NotFound: pass

    def test_deny_receiver_to_exchange(self):
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.receiver("amq.topic")
            assert False, "anonymous should not be allowed to create receiver from amq.topic"
        except UnauthorizedAccess: pass
        try:
            self.bob.receiver("amq.topic")
            assert False, "bob should not be allowed to create receiver to amq.topic"
        except UnauthorizedAccess: pass
        self.alice.receiver("amq.topic")

    def test_deny_receiver_to_queue(self):
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.receiver("acl_test_queue")
            assert False, "anonymous should not be allowed to create receiver from acl_test_queue"
        except UnauthorizedAccess: pass
        try:
            self.bob.receiver("acl_test_queue")
            assert False, "bob should not be allowed to create receiver to acl_test_queue"
        except UnauthorizedAccess: pass
        self.alice.receiver("acl_test_queue")

    def test_deny_receiver_to_unknown(self):
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.receiver("I_dont_exist")
            assert False, "anonymous should not be allowed to create receiver from non-existent node"
        except UnauthorizedAccess: pass
        try:
            self.bob.receiver("unknown")
            assert False, "bob should not be allowed to create receiver to unknown"
        except UnauthorizedAccess: pass
        try:
            self.alice.receiver("unknown")
        except NotFound: pass

    def test_create_for_receiver_from_exchange(self):
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'access', 'queue', 'name=amq.topic')
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.receiver("amq.topic")
            assert False, "anonymous should not be allowed to create receiver from amq.topic"
        except UnauthorizedAccess: pass
        try:
            self.bob.receiver("amq.topic")
            assert False, "bob should not be allowed to create receiver from amq.topic without create permission"
        except UnauthorizedAccess: pass
        self.alice.receiver("amq.topic")

    def test_bind_for_receiver_from_exchange(self):
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'access', 'queue', 'name=amq.topic')
        self.acl.allow('bob', 'create', 'queue', 'name=bob*')
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.receiver("amq.topic")
            assert False, "anonymous should not be allowed to create receiver from amq.topic"
        except UnauthorizedAccess: pass
        try:
            self.bob.receiver("amq.topic")
            assert False, "bob should not be allowed to create receiver from amq.topic without bind permission"
        except UnauthorizedAccess: pass
        self.alice.receiver("amq.topic")

    def test_consume_for_receiver_from_exchange(self):
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'access', 'queue', 'name=amq.topic')
        self.acl.allow('bob', 'create', 'queue', 'name=bob*')
        self.acl.allow('bob', 'bind', 'exchange', 'name=amq.topic')
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.receiver("amq.topic")
            assert False, "anonymous should not be allowed to create receiver from amq.topic"
        except UnauthorizedAccess: pass
        try:
            self.bob.receiver("amq.topic")
            assert False, "bob should not be allowed to create receiver from amq.topic without consume permission"
        except UnauthorizedAccess: pass
        self.alice.receiver("amq.topic")

    def test_required_permissions_for_receiver_from_exchange(self):
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'access', 'queue', 'name=amq.topic')
        self.acl.allow('bob', 'create', 'queue', 'name=bob*')
        self.acl.allow('bob', 'bind', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'consume', 'queue', 'name=bob*')
        self.acl.allow('alice').deny().apply()
        try:
            self.ssn.receiver("amq.topic")
            assert False, "anonymous should not be allowed to create receiver from amq.topic"
        except UnauthorizedAccess: pass
        self.bob.receiver("amq.topic")
        try:
            self.bob.receiver("amq.direct")
            assert False, "bob should not be allowed to create receiver from amq.direct"
        except UnauthorizedAccess: pass

    def test_publish_to_exchange(self):
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'access', 'queue', 'name=amq.topic')
        self.acl.allow('bob', 'publish', 'exchange', 'name=amq.topic', 'routingkey=abc')
        self.acl.allow('alice').deny().apply()

        sender = self.bob.sender("amq.topic")
        sender.send(Message("a message", subject="abc"), sync=True)
        try:
            sender.send(Message("another", subject="def"), sync=True)
            assert False, "bob should not be allowed to send message to amq.topic with subject 'def'"
        except UnauthorizedAccess: pass
        sender = self.alice.sender("amq.topic")
        sender.send(Message("alice's message", subject="abc"), sync=True)
        sender.send(Message("another from alice", subject="def"), sync=True)

    def test_publish_to_anonymous_relay(self):
        self.acl.allow('bob', 'access', 'exchange', 'name=ANONYMOUS-RELAY')
        self.acl.allow('bob', 'access', 'queue', 'name=acl_test_queue')
        self.acl.allow('bob', 'access', 'exchange', 'name=acl_test_queue')
        self.acl.allow('bob', 'publish', 'exchange', 'routingkey=acl_test_queue')
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'access', 'queue', 'name=amq.topic')
        self.acl.allow('bob', 'publish', 'exchange', 'name=amq.topic', 'routingkey=abc')
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.direct')
        self.acl.allow('bob', 'access', 'queue', 'name=amq.direct')
        self.acl.allow('alice').deny().apply()

        sender = self.bob.sender("<null>")
        sender.send(Message("a message", properties={'x-amqp-to':'acl_test_queue'}), sync=True)
        sender.send(Message("another", subject='abc', properties={'x-amqp-to':'amq.topic'}), sync=True)
        try:
            # have access permission, but publish not allowed for given key
            sender.send(Message("a third", subject='def', properties={'x-amqp-to':'amq.topic'}), sync=True)
            assert False, "bob should not be allowed to send message to amq.topic with key 'def'"
        except UnauthorizedAccess: pass
        sender = self.bob.sender("<null>")
        try:
            # have access permission, but no publish
            sender.send(Message("a fourth", subject='abc', properties={'x-amqp-to':'amq.direct'}), sync=True)
            assert False, "bob should not be allowed to send message to amq.direct"
        except UnauthorizedAccess: pass
        sender = self.bob.sender("<null>")
        try:
            # have no access permission
            sender.send(Message("a fiftth", subject='abc', properties={'x-amqp-to':'amq.fanout'}), sync=True)
            assert False, "bob should not be allowed to send message to amq.fanout"
        except UnauthorizedAccess: pass
        sender = self.bob.sender("<null>")
        try:
            # have no access permission
            sender.send(Message("a sixth", properties={'x-amqp-to':'somewhereelse'}), sync=True)
            assert False, "bob should not be allowed to send message to somewhere else"
        except UnauthorizedAccess: pass
        sender = self.alice.sender("<null>")
        sender.send(Message("alice's message", properties={'x-amqp-to':'abc'}), sync=True)
        sender.send(Message("another from alice", properties={'x-amqp-to':'def'}), sync=True)

    def test_resolution_for_sender_to_exchange(self):
        self.acl.allow('alice', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('alice', 'access', 'queue', 'name=amq.topic')
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.topic')
        self.acl.deny().apply()
        try:
            self.ssn.sender("amq.topic")
            assert False, "anonymous should not be allowed to create sender to amq.topic"
        except UnauthorizedAccess: pass
        self.bob.sender("amq.topic; {node:{type:topic}}")
        try:
            self.bob.sender("amq.topic")
            assert False, "bob should not be allowed to create sender to amq.topic without specifying the node type"
        except UnauthorizedAccess: pass
        self.alice.sender("amq.topic; {node:{type:topic}}")
        self.alice.sender("amq.topic")
        try:
            self.alice.sender("amq.direct")
            assert False, "alice should not be allowed to create sender to amq.direct"
        except UnauthorizedAccess: pass

    def test_resolution_for_sender_to_queue(self):
        self.acl.allow('alice', 'access', 'exchange', 'name=acl_test_queue')
        self.acl.allow('alice', 'access', 'queue', 'name=acl_test_queue')
        self.acl.allow('alice', 'publish', 'exchange', 'routingkey=acl_test_queue')
        self.acl.allow('bob', 'access', 'queue', 'name=acl_test_queue')
        self.acl.allow('bob', 'publish', 'exchange', 'routingkey=acl_test_queue')
        self.acl.deny().apply()
        try:
            self.ssn.sender("acl_test_queue")
            assert False, "anonymous should not be allowed to create sender to acl_test_queue"
        except UnauthorizedAccess: pass
        self.bob.sender("acl_test_queue; {node:{type:queue}}")
        try:
            self.bob.sender("acl_test_queue")
            assert False, "bob should not be allowed to create sender to acl_test_queue without specifying the node type"
        except UnauthorizedAccess: pass
        self.alice.sender("acl_test_queue; {node:{type:queue}}")
        self.alice.sender("acl_test_queue")

    def test_resolution_for_receiver_from_exchange(self):
        self.acl.allow('alice', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('alice', 'access', 'queue', 'name=amq.topic')
        self.acl.allow('alice', 'create', 'queue')
        self.acl.allow('alice', 'consume', 'queue')
        self.acl.allow('alice', 'bind', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'access', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'bind', 'exchange', 'name=amq.topic')
        self.acl.allow('bob', 'create', 'queue', 'name=bob*', 'autodelete=true')
        self.acl.allow('bob', 'consume', 'queue', 'name=bob*')
        self.acl.deny().apply()
        try:
            self.ssn.receiver("amq.topic")
            assert False, "anonymous should not be allowed to create receiver from amq.topic"
        except UnauthorizedAccess: pass
        self.bob.receiver("amq.topic; {node:{type:topic}}")
        try:
            self.bob.receiver("amq.topic")
            assert False, "bob should not be allowed to create receiver from amq.topic without specifying the node type"
        except UnauthorizedAccess: pass
        self.alice.receiver("amq.topic; {node:{type:topic}}")
        self.alice.receiver("amq.topic")
        try:
            self.alice.receiver("amq.direct")
            assert False, "alice should not be allowed to create receiver from amq.direct"
        except UnauthorizedAccess: pass

    def test_resolution_for_receiver_from_queue(self):
        self.acl.allow('alice', 'access', 'exchange', 'name=acl_test_queue')
        self.acl.allow('alice', 'access', 'queue', 'name=acl_test_queue')
        self.acl.allow('alice', 'consume', 'queue', 'name=acl_test_queue')
        self.acl.allow('bob', 'access', 'queue', 'name=acl_test_queue')
        self.acl.allow('bob', 'consume', 'queue', 'name=acl_test_queue')
        self.acl.deny().apply()
        try:
            self.ssn.receiver("acl_test_queue")
            assert False, "anonymous should not be allowed to create receiver from acl_test_queue"
        except UnauthorizedAccess: pass
        self.bob.receiver("acl_test_queue; {node:{type:queue}}")
        try:
            self.bob.receiver("acl_test_queue")
            assert False, "bob should not be allowed to create receiver from acl_test_queue without specifying the node type"
        except UnauthorizedAccess: pass
        self.alice.receiver("acl_test_queue; {node:{type:queue}}")
        self.alice.receiver("acl_test_queue")
