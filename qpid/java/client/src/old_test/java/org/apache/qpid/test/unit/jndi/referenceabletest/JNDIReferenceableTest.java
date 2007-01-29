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
package org.apache.qpid.test.unit.jndi.referenceabletest;

import junit.framework.TestCase;
//import org.apache.qpid.testutil.VMBrokerSetup;

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
public class JNDIReferenceableTest extends TestCase
{
/*    // FIXME FSContext has been removed from repository. This needs redone with the PropertiesFileInitialContextFactory. QPID-84
    public void testReferenceable()
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
                        fail("Unable to clear bound objects for test.");
                    }
                }
                else
                {
                    fail("Unable to clear bound objects for test.");
                }
            }
        }
        catch (NoInitialContextException e)
        {
            fail("You don't have the File System SPI on you class path.\n" +
                        "This can be downloaded from sun here:\n" +
                        "http://java.sun.com/products/jndi/downloads/index.html\n" +
                        "Click : Download JNDI 1.2.1 & More button\n" +
                        "Download: File System Service Provider, 1.2 Beta 3\n" +
                        "and add the two jars in the lib dir to your class path.");
        }

        assertTrue(b.bound());

        Lookup l = new Lookup(b.getProviderURL());

        assertTrue(l.connectionFactoryValue().equals(b.connectionFactoryValue()));

        assertTrue(l.connectionValue().equals(b.connectionValue()));

        assertTrue(l.topicValue().equals(b.topicValue()));


        Unbind u = new Unbind();

        assertTrue(u.unbound());

    }

    public static junit.framework.Test suite()
    {
        return new VMBrokerSetup(new junit.framework.TestSuite(JNDIReferenceableTest.class));
    }
    */
}
