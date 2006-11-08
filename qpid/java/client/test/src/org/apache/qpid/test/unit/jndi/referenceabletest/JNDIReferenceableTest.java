/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.test.unit.jndi.referenceabletest;

import org.junit.Test;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import junit.framework.JUnit4TestAdapter;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NoInitialContextException;


/**
 * Usage: To run these you need to have the sun JNDI SPI for the FileSystem.
 * This can be downloaded from sun here:
 * http://java.sun.com/products/jndi/downloads/index.html
 * Click : Download JNDI 1.2.1 & More button
 * Download: File System Service Provider, 1.2 Beta 3
 * and add the two jars in the lib dir to your class path.
 * <p/>
 * Also you need to create the directory /temp/qpid-jndi-test
 */
public class JNDIReferenceableTest
{
    @Before
    public void createVMBroker()
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            Assert.fail("Unable to create broker: " + e);
        }
    }

    @After
    public void stopVmBroker()
    {
        TransportConnection.killVMBroker(1);
    }

    @Test
    public void referenceable()
    {
        Bind b = null;
        try
        {
            try
            {
                b = new Bind();
            }
            catch (NameAlreadyBoundException e)
            {
                if (new Unbind().unbound())
                {
                    try
                    {
                        b = new Bind();
                    }
                    catch (NameAlreadyBoundException ee)
                    {
                        Assert.fail("Unable to clear bound objects for test.");
                    }
                }
                else
                {
                    Assert.fail("Unable to clear bound objects for test.");
                }
            }
        }
        catch (NoInitialContextException e)
        {
            Assert.fail("You don't have the File System SPI on you class path.\n" +
                        "This can be downloaded from sun here:\n" +
                        "http://java.sun.com/products/jndi/downloads/index.html\n" +
                        "Click : Download JNDI 1.2.1 & More button\n" +
                        "Download: File System Service Provider, 1.2 Beta 3\n" +
                        "and add the two jars in the lib dir to your class path.");
        }

        Assert.assertTrue(b.bound());
        
        Lookup l = new Lookup();

        Assert.assertTrue(l.connectionFactoryValue().equals(b.connectionFactoryValue()));

        Assert.assertTrue(l.connectionValue().equals(b.connectionValue()));

        Assert.assertTrue(l.topicValue().equals(b.topicValue()));


        Unbind u = new Unbind();

        Assert.assertTrue(u.unbound());

    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(JNDIReferenceableTest.class);
    }
}
