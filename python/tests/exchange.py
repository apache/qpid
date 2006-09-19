#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
Tests for exchange behaviour.

Test classes ending in 'RuleTests' are derived from rules in amqp.xml.
"""

import logging, Queue
from qpid.testlib import TestBase
from qpid.content import Content


# TODO aconway 2006-09-01: Investigate and add tests as appropriate.
# Observered on C++:
#
# No exception raised for basic_consume on non-existent queue name.
# No exception for basic_publish with bad routing key.
# No exception for binding to non-existent exchange?
# queue_bind hangs with invalid exchange name
# 
# Do server exceptions get propagated properly?
# Do Java exceptions propagate with any data (or just Closed())

class StandardExchangeVerifier:
    """Verifies standard exchange behavior.

    Used as base class for classes that test standard exchanges."""

    def verifyDirectExchange(self, ex):
        """Verify that ex behaves like a direct exchange."""
        self.queue_declare(queue="q")
        self.channel.queue_bind(queue="q", exchange=ex, routing_key="k")
        self.assertPublishConsume(exchange=ex, queue="q", routing_key="k")
        try:
            self.assertPublishConsume(exchange=ex, queue="q", routing_key="kk")
            self.fail("Expected Empty exception")
        except Queue.Empty: None # Expected

    def verifyFanOutExchange(self, ex):
        """Verify that ex behaves like a fanout exchange."""
        self.queue_declare(queue="q") 
        self.channel.queue_bind(queue="q", exchange=ex)
        self.queue_declare(queue="p") 
        self.channel.queue_bind(queue="p", exchange=ex)
        self.assertPublishConsume(exchange=ex, queue=["q","p"])

    
class RecommendedTypesRuleTests(TestBase, StandardExchangeVerifier):
    """
    The server SHOULD implement these standard exchange types: topic, headers.
    
    Client attempts to declare an exchange with each of these standard types.
    """

    def testDirect(self):
        """Declare and test a direct exchange"""
        self.exchange_declare(0, exchange="d", type="direct")
        self.verifyDirectExchange("d")

    def testFanout(self):
        """Declare and test a fanout exchange"""
        self.exchange_declare(0, exchange="f", type="fanout")
        self.verifyFanOutExchange("f")
        

class RequiredInstancesRuleTests(TestBase, StandardExchangeVerifier):
    """
    The server MUST, in each virtual host, pre-declare an exchange instance
    for each standard exchange type that it implements, where the name of the
    exchange instance is amq. followed by the exchange type name.
    
    Client creates a temporary queue and attempts to bind to each required
    exchange instance (amq.fanout, amq.direct, and amq.topic, amq.headers if
    those types are defined).
    """
    # TODO aconway 2006-09-01: Add tests for 3.1.3.1:
    # - Test auto binding by q name
    # - Test the nameless "default publish" exchange.
    # - Auto created amq.fanout exchange

    def testAmqDirect(self): self.verifyDirectExchange("amq.direct")

    def testAmqFanOut(self): self.verifyFanOutExchange("amq.fanout")

    def testAmqTopic(self): 
        self.exchange_declare(0, exchange="amq.topic", passive="true")
        # TODO aconway 2006-09-14: verify topic behavior
        
    def testAmqHeaders(self): 
        self.exchange_declare(0, exchange="amq.headers", passive="true")
        # TODO aconway 2006-09-14: verify headers behavior

class DefaultExchangeRuleTests(TestBase):
    """
    The server MUST predeclare a direct exchange to act as the default exchange
    for content Publish methods and for default queue bindings.
    
    Client checks that the default exchange is active by specifying a queue
    binding with no exchange name, and publishing a message with a suitable
    routing key but without specifying the exchange name, then ensuring that
    the message arrives in the queue correctly.
    """


class DefaultAccessRuleTests(TestBase):
    """
    The server MUST NOT allow clients to access the default exchange except
    by specifying an empty exchange name in the Queue.Bind and content Publish
    methods.
    """


class ExtensionsRuleTests(TestBase):
    """
    The server MAY implement other exchange types as wanted.
    """


class DeclareMethodMinimumRuleTests(TestBase):
    """
    The server SHOULD support a minimum of 16 exchanges per virtual host and
    ideally, impose no limit except as defined by available resources.
    
    The client creates as many exchanges as it can until the server reports
    an error; the number of exchanges successfuly created must be at least
    sixteen.
    """


class DeclareMethodTicketFieldValidityRuleTests(TestBase):
    """
    The client MUST provide a valid access ticket giving "active" access to
    the realm in which the exchange exists or will be created, or "passive"
    access if the if-exists flag is set.
    
    Client creates access ticket with wrong access rights and attempts to use
    in this method.
    """


class DeclareMethodExchangeFieldReservedRuleTests(TestBase):
    """
    Exchange names starting with "amq." are reserved for predeclared and
    standardised exchanges. The client MUST NOT attempt to create an exchange
    starting with "amq.".
    
    
    """


class DeclareMethodTypeFieldTypedRuleTests(TestBase):
    """
    Exchanges cannot be redeclared with different types.  The client MUST not
    attempt to redeclare an existing exchange with a different type than used
    in the original Exchange.Declare method.
    
    
    """


class DeclareMethodTypeFieldSupportRuleTests(TestBase):
    """
    The client MUST NOT attempt to create an exchange with a type that the
    server does not support.
    
    
    """


class DeclareMethodPassiveFieldNotFoundRuleTests(TestBase):
    """
    If set, and the exchange does not already exist, the server MUST raise a
    channel exception with reply code 404 (not found).
    
    
    """


class DeclareMethodDurableFieldSupportRuleTests(TestBase):
    """
    The server MUST support both durable and transient exchanges.
    
    
    """


class DeclareMethodDurableFieldStickyRuleTests(TestBase):
    """
    The server MUST ignore the durable field if the exchange already exists.
    
    
    """


class DeclareMethodAutoDeleteFieldStickyRuleTests(TestBase):
    """
    The server MUST ignore the auto-delete field if the exchange already
    exists.
    
    
    """


class DeleteMethodTicketFieldValidityRuleTests(TestBase):
    """
    The client MUST provide a valid access ticket giving "active" access
    rights to the exchange's access realm.
    
    Client creates access ticket with wrong access rights and attempts to use
    in this method.
    """


class DeleteMethodExchangeFieldExistsRuleTests(TestBase):
    """
    The client MUST NOT attempt to delete an exchange that does not exist.
    """


