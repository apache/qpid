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
package org.apache.qpid.server.security.auth.manager.ldap;

import static org.apache.qpid.server.security.auth.manager.ldap.LDAPSSLSocketFactoryGenerator.TARGET_PACKAGE_NAME;

import static org.mockito.Mockito.mock;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import junit.framework.TestCase;

public class LDAPSSLSocketFactoryGeneratorTest extends TestCase
{
    private SSLSocketFactory _sslSocketFactory = mock(SSLSocketFactory.class);

    public void testPackageAndClassName() throws Exception
    {
        Class<? extends SocketFactory> socketFactoryClass = LDAPSSLSocketFactoryGenerator.createSubClass("MyNewClass", _sslSocketFactory);
        assertEquals("MyNewClass", socketFactoryClass.getSimpleName());
        assertEquals(TARGET_PACKAGE_NAME, socketFactoryClass.getPackage().getName());
    }

    public void testLoadingWithClassForName() throws Exception
    {
        Class<? extends AbstractLDAPSSLSocketFactory> socketFactoryClass = LDAPSSLSocketFactoryGenerator.createSubClass("MyNewClass", _sslSocketFactory);
        String fqcn = socketFactoryClass.getName();

        try
        {
            Class.forName(fqcn);
            fail("Class loading by name should not have been successful");
        }
        catch (ClassNotFoundException cnfe)
        {
           // PASS
        }

        final ClassLoader sfClassloader = socketFactoryClass.getClassLoader();
        // Note: Oracle's com.sun.jndi.ldap.LdapClient uses the following form passing the context loader
        Class<?> loaded = Class.forName(fqcn, true, sfClassloader);
        assertEquals(socketFactoryClass, loaded);
    }

    public void testClassloaderDelegatesToParent() throws Exception
    {
        ClassLoader classLoader = LDAPSSLSocketFactoryGenerator.createSubClass("MyNewClass", _sslSocketFactory).getClassLoader();
        assertEquals(String.class, classLoader.loadClass("java.lang.String"));
        assertEquals(TestClassForLoading.class, classLoader.loadClass(TestClassForLoading.class.getName()));
    }

    public void testGetDefaultCreatesInstance() throws Exception
    {
        Class<? extends AbstractLDAPSSLSocketFactory> socketFactoryClass = LDAPSSLSocketFactoryGenerator.createSubClass("MyNewClass", _sslSocketFactory);

        AbstractLDAPSSLSocketFactory socketFactory = invokeGetDefaultMethod(socketFactoryClass);
        assertTrue(socketFactory instanceof AbstractLDAPSSLSocketFactory);
        assertEquals("MyNewClass", socketFactory.getClass().getSimpleName());
    }

    private AbstractLDAPSSLSocketFactory invokeGetDefaultMethod(Class<? extends AbstractLDAPSSLSocketFactory> socketFactoryClass) throws Exception
    {
        return (AbstractLDAPSSLSocketFactory) socketFactoryClass.getMethod("getDefault").invoke(null);
    }

    class TestClassForLoading
    {
    }
}
