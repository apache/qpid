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
package org.apache.qpid.client.util;

import org.apache.qpid.test.utils.QpidTestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

public class ClassLoadingAwareObjectInputStreamTest extends QpidTestCase
{
    private InputStream _in;
    private ClassLoadingAwareObjectInputStream _claOIS;

    protected void setUp() throws Exception
    {
        //Create a viable input stream for instantiating the CLA OIS
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject("testString");
        out.flush();
        out.close();


        _in = new ByteArrayInputStream(baos.toByteArray());

        _claOIS = new ClassLoadingAwareObjectInputStream(_in);
    }

    /**
     * Test that the resolveProxyClass method returns a proxy class implementing the desired interface
     */
    public void testResolveProxyClass() throws Exception
    {
        //try to proxy an interface
        Class<?> clazz = _claOIS.resolveProxyClass(new String[]{"java.lang.CharSequence"});

        //verify the proxy supports the expected interface (only)
        List<Class<?>> interfaces = Arrays.asList(clazz.getInterfaces());
        assertTrue("Unexpected interfaces supported by proxy", interfaces.contains(CharSequence.class));
        assertEquals("Unexpected interfaces supported by proxy", 1, interfaces.size());
    }

    /**
     * Test that the resolveProxyClass method throws a ClassNotFoundException wrapping an
     * IllegalArgumentException if it is provided arguments which violate the restrictions allowed
     * by Proxy.getProxyClass (as required by the ObjectInputStream.resolveProxyClass javadoc).
     */
    public void testResolveProxyClassThrowsCNFEWrappingIAE() throws Exception
    {
        try
        {
            //try to proxy a *class* rather than an interface, which is illegal
            _claOIS.resolveProxyClass(new String[]{"java.lang.String"});
            fail("should have thrown an exception");
        }
        catch(ClassNotFoundException cnfe)
        {
            //expected, but must verify it is wrapping an IllegalArgumentException
            assertTrue(cnfe.getCause() instanceof IllegalArgumentException);
        }
    }
}
