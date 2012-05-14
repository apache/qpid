/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.ra;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.Session;

import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

import org.apache.qpid.client.AMQXAResource;

import org.apache.qpid.ra.QpidRAConnectionFactoryImpl;
import org.apache.qpid.ra.QpidRAManagedConnectionFactory;
import org.apache.qpid.ra.QpidResourceAdapter;

public class QpidRAXAResourceTest extends QpidBrokerTestCase
{
    private static final String FACTORY_NAME = "default";
    private static final String BROKER_PORT = "15672";
    private static final String URL = "amqp://guest:guest@client/test?brokerlist='tcp://localhost:" + BROKER_PORT + "?sasl_mechs='PLAIN''";

    public void testXAResourceIsSameRM() throws Exception
    {
        QpidResourceAdapter ra = new QpidResourceAdapter();
        QpidRAManagedConnectionFactory mcf = new QpidRAManagedConnectionFactory();
        mcf.setConnectionURL(URL);
        mcf.setResourceAdapter(ra);
        QpidRAManagedConnection mc = (QpidRAManagedConnection)mcf.createManagedConnection(null, null);
        AMQXAResource xa1 = (AMQXAResource)mc.getXAResource();

        XAConnectionFactory factory = getConnectionFactory(FACTORY_NAME);
        XAConnection connection = factory.createXAConnection("guest", "guest");
        XASession s2 = connection.createXASession();
        AMQXAResource xaResource = (AMQXAResource)connection.createXASession().getXAResource();

        assertTrue("QpidRAXAResource and XAResource should be from the same RM", xa1.isSameRM(xaResource));
        assertTrue("XAResource and QpidRAXAResource should be from the same RM", xaResource.isSameRM(xa1));

    }

}
