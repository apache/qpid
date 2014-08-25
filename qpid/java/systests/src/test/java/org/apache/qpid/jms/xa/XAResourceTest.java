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
package org.apache.qpid.jms.xa;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.test.unit.xa.AbstractXATestCase;
import org.apache.qpid.client.AMQXAResource;

import org.apache.qpid.dtx.XidImpl;

import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

public class XAResourceTest extends AbstractXATestCase
{

    private static final String FACTORY_NAME = "default";
    private static final String ALT_FACTORY_NAME = "connection2";

    public void init() throws Exception
    {
    }

    public void testIsSameRMJoin() throws Exception
    {
        XAConnectionFactory factory = getConnectionFactory(FACTORY_NAME);
        XAConnection conn1 = factory.createXAConnection("guest", "guest");
        XAConnection conn2 = factory.createXAConnection("guest", "guest");
        XAConnection conn3 = factory.createXAConnection("guest", "guest");

        XASession session1 = conn1.createXASession();
        XASession session2 = conn2.createXASession();
        XASession session3 = conn3.createXASession();

        AMQXAResource xaResource1 = (AMQXAResource)session1.getXAResource();
        AMQXAResource xaResource2 = (AMQXAResource)session2.getXAResource();
        AMQXAResource xaResource3 = (AMQXAResource)session3.getXAResource();

        Xid xid = getNewXid();

        xaResource1.start(xid, XAResource.TMNOFLAGS);
        assertTrue("XAResource isSameRM", xaResource1.isSameRM(xaResource2));
        xaResource2.start(xid, XAResource.TMJOIN);
        assertTrue("AMQXAResource siblings should be 1", xaResource1.getSiblings().size() == 1);

        assertTrue("AMQXAResource TMJOIN resource siblings should be 0", xaResource2.getSiblings().size() == 0);

        assertTrue("XAResource isSameRM", xaResource2.isSameRM(xaResource3));


        xaResource3.start(xid, XAResource.TMJOIN);
        assertTrue("AMQXAResource siblings should be 1", xaResource2.getSiblings().size() == 1);

        xaResource1.end(xid, XAResource.TMSUCCESS);
        assertTrue("AMQXAResource TMJOIN resource siblings should be 0", xaResource1.getSiblings().size() == 0);

        xaResource1.prepare(xid);
        xaResource1.commit(xid, false);

        conn3.close();
        conn2.close();
        conn1.close();
    }

    /*
     * Test with multiple XAResources originating from the same connection factory. XAResource(s) will be equal,
     * as they originate from the same session.
     */
    public void testIsSameRMSingleCF() throws Exception
    {
        XAConnectionFactory factory = getConnectionFactory(FACTORY_NAME);
        XAConnection conn = factory.createXAConnection("guest","guest");
        XASession session = conn.createXASession();
        XAResource xaResource1 = session.getXAResource();
        XAResource xaResource2 = session.getXAResource();

        assertEquals("XAResource objects not equal", xaResource1, xaResource2);
        assertTrue("isSameRM not true for identical objects", xaResource1.isSameRM(xaResource2));

        session.close();
        conn.close();
    }

    /*
     * Test with multiple XAResources originating from different connection factory's and different sessions. XAResources will not be
     * equal as they do not originate from the same session. As the UUID from the broker will be the same, isSameRM will be true.
     *
     */
    public void testIsSameRMMultiCF() throws Exception
    {
        startBroker(FAILING_PORT);
        ConnectionURL url = getConnectionFactory(FACTORY_NAME).getConnectionURL();
        XAConnectionFactory factory = new AMQConnectionFactory(url);
        XAConnectionFactory factory2 = new AMQConnectionFactory(url);
        XAConnectionFactory factory3 = getConnectionFactory(ALT_FACTORY_NAME);

        XAConnection conn = factory.createXAConnection("guest","guest");
        XAConnection conn2 = factory2.createXAConnection("guest","guest");
        XAConnection conn3 = factory3.createXAConnection("guest","guest");

        XASession session = conn.createXASession();
        XASession session2 = conn2.createXASession();
        XASession session3 = conn3.createXASession();

        XAResource xaResource1 = session.getXAResource();
        XAResource xaResource2 = session2.getXAResource();
        XAResource xaResource3 = session3.getXAResource();

        assertFalse("XAResource objects should not be equal", xaResource1.equals(xaResource2));
        assertTrue("isSameRM not true for identical objects", xaResource1.isSameRM(xaResource2));
        assertFalse("isSameRM true for XA Resources created by two different brokers", xaResource1.isSameRM(xaResource3));

        conn.close();
        conn2.close();
        conn3.close();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            // Ensure we shutdown any secondary brokers
            stopBroker(FAILING_PORT);
            FileUtils.deleteDirectory(System.getProperty("QPID_WORK") + "/" + getFailingPort());
        }
    }

}
