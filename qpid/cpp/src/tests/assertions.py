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

class AssertionTests (VersionTest):
    """
    Tests for assertions with qpidd
    """
    def test_queues_alternate_exchange1(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{properties:{alternate-exchange:amq.fanout}}}" % name)
        self.ssn.sender("%s; {assert:always, node:{properties:{alternate-exchange:amq.fanout}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{properties:{alternate-exchange:amq.topic}}}" % name)
            assert False, "Expected assertion to fail on alternate-exchange"
        except AssertionFailed: None
        except MessagingError: None

    def test_queues_alternate_exchange2(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{x-declare:{alternate-exchange:amq.fanout}}}" % name)
        self.ssn.sender("%s; {assert:always, node:{x-declare:{alternate-exchange:amq.fanout}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{alternate-exchange:amq.topic}}}" % name)
            assert False, "Expected assertion to fail on alternate-exchange"
        except AssertionFailed: None
        except MessagingError: None

    def test_queue_type(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always}" % name)
        self.ssn.sender("%s; {assert:always, node:{type:queue}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{type:topic}}" % name)
            assert False, "Expected assertion to fail on type"
        except AssertionFailed: None
        except MessagingError: None

    def test_queue_not_durable(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always}" % name)
        self.ssn.sender("%s; {assert:always, node:{durable:False}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{durable:True}}" % name)
            assert False, "Expected assertion to fail on durability"
        except AssertionFailed: None
        except MessagingError: None

    def test_queue_is_durable(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{durable:True}}" % name)
        self.ssn.sender("%s; {assert:always, node:{durable:True}}" % name)

    def test_queue_is_autodelete(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{x-declare:{auto-delete:True}}}" % name)
        self.ssn.sender("%s; {assert:always, node:{x-declare:{auto-delete:True}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{auto-delete:False}}}" % name)
            assert False, "Expected assertion to fail for auto-delete"
        except AssertionFailed: None
        except MessagingError: None

    def do_test_queue_options(self, name):
        self.ssn.sender("%s; {create:always, node:{x-declare:{arguments:{foo:bar,'qpid.last_value_queue_key':abc}}}}" % name)
        self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments:{'qpid.last_value_queue_key':abc}}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments:{foo:bar}}}}" % name)
            assert False, "Expected assertion to fail on unrecognised option"
        except AssertionFailed: None
        except MessagingError: None
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments:{'qpid.max_count':10}}}}" % name)
            assert False, "Expected assertion to fail on unspecified option"
        except AssertionFailed: None
        except MessagingError: None
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments:{'qpid.last_value_key':xyz}}}}" % name)
            assert False, "Expected assertion to fail on option with different value"
        except AssertionFailed: None
        except MessagingError: None

    def test_queue_options(self):
        self.do_test_queue_options(str(uuid4()))

    def test_queue_options_from_0_10(self):
        name = str(uuid4())
        self.do_test_queue_options(name)
        ssn_0_10 = self.create_connection("amqp0-10", True).session()
        ssn_0_10.sender("%s; {assert:always, node:{x-declare:{arguments:{'qpid.last_value_queue_key':abc}}}}" % name)
        try:
            ssn_0_10.sender("%s; {assert:always, node:{x-declare:{arguments:{'qpid.last_value_key':xyz}}}}" % name)
            assert False, "Expected assertion to fail on option with different value"
        except AssertionFailed: None
        except MessagingError: None


    def test_exchanges_alternate_exchange1(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{type:topic, properties:{alternate-exchange:amq.fanout}}}" % name)
        self.ssn.sender("%s; {assert:always, node:{type:topic, properties:{alternate-exchange:amq.fanout}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{properties:{alternate-exchange:amq.topic}}}" % name)
            assert False, "Expected assertion to fail on alternate-exchange"
        except AssertionFailed: None
        except MessagingError: None

    def test_exchanges_alternate_exchange2(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{type:topic, x-declare:{alternate-exchange:amq.fanout}}}" % name)
        self.ssn.sender("%s; {assert:always, node:{type:topic, x-declare:{alternate-exchange:amq.fanout}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{alternate-exchange:amq.topic}}}" % name)
            assert False, "Expected assertion to fail on alternate-exchange"
        except AssertionFailed: None
        except MessagingError: None

    def test_exchange_type(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{type:topic}}" % name)
        self.ssn.sender("%s; {assert:always, node:{type:topic}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{type:queue}}" % name)
            assert False, "Expected assertion to fail on type"
        except AssertionFailed: None
        except MessagingError: None

    def test_exchange_durability(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{type:topic}}" % name)
        self.ssn.sender("%s; {assert:always, node:{durable:False}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{durable:True}}" % name)
            assert False, "Expected assertion to fail on durability"
        except AssertionFailed: None
        except MessagingError: None

    def test_exchange_is_autodelete(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{type:topic, x-declare:{auto-delete:True}}}" % name)
        self.ssn.sender("%s; {assert:always, node:{x-declare:{auto-delete:True}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{auto-delete:False}}}" % name)
            assert False, "Expected assertion to fail for auto-delete"
        except AssertionFailed: None
        except MessagingError: None

    def test_exchange_options(self):
        name = str(uuid4())
        self.ssn.sender("%s; {create:always, node:{type:topic, x-declare:{arguments:{foo:bar,'qpid.msg_sequence':True}}}}" % name)
        self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments:{'qpid.msg_sequence':True}}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments:{foo:bar}}}}" % name)
            assert False, "Expected assertion to fail on unrecognised option"
        except AssertionFailed: None
        except MessagingError: None
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments:{'qpid.ive':True}}}}" % name)
            assert False, "Expected assertion to fail on unspecified option"
        except AssertionFailed: None
        except MessagingError: None

    def test_queue_autodelete_timeout(self):
        name = str(uuid4())
        # create subscription queue with 0-10 to be sure of name
        ssn_0_10 = self.create_connection("amqp0-10", True).session()
        ssn_0_10.receiver("amq.direct; {link:{name:%s,timeout:120}}" % name)
        self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments: {qpid.auto_delete_timeout: 120}}}}" % name)
        ssn_0_10_other = self.create_connection("amqp0-10", True).session()
        ssn_0_10_other.sender("%s; {assert:always, node:{x-declare:{arguments: {qpid.auto_delete_timeout: 120}}}}" % name)
        try:
            self.ssn.sender("%s; {assert:always, node:{x-declare:{arguments: {qpid.auto_delete_timeout: 180}}}}" % name)
            ssn_0_10_other.sender("%s; {assert:always, node:{x-declare:{arguments: {qpid.auto_delete_timeout: 180}}}}" % name)
            assert False, "Expected assertion to fail for auto_delete_timeout"
        except AssertionFailed: None
        except MessagingError: None
