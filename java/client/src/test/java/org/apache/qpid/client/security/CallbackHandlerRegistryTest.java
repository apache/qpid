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
package org.apache.qpid.client.security;

import java.io.IOException;
import java.util.Properties;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.test.utils.QpidTestCase;


/**
 * Tests the ability of {@link CallbackHandlerRegistry} to correctly parse
 * the properties describing the available callback handlers.   Ensures also
 * that it is able to select the mechanism and create an implementation
 * given a variety of starting conditions.
 *
 */
public class CallbackHandlerRegistryTest extends QpidTestCase
{
    private CallbackHandlerRegistry _registry;  // Object under test

    public void testCreateHandlerSuccess()
    {
        final Properties props = new Properties();
        props.put("TESTA.1", TestACallbackHandler.class.getName());

        _registry = new CallbackHandlerRegistry(props);
        assertEquals(1,_registry.getMechanisms().size());

        final CallbackHandler handler = _registry.createCallbackHandler("TESTA");
        assertTrue(handler instanceof TestACallbackHandler);
    }

    public void testCreateHandlerForUnknownMechanismName()
    {
        final Properties props = new Properties();
        props.put("TEST1.1", TestACallbackHandler.class.getName());

        _registry = new CallbackHandlerRegistry(props);

        try
        {
            _registry.createCallbackHandler("NOTFOUND");
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

    public void testSelectMechanism()
    {
        final Properties props = new Properties();
        props.put("TESTA.1", TestACallbackHandler.class.getName());
        props.put("TESTB.2", TestBCallbackHandler.class.getName());

        _registry = new CallbackHandlerRegistry(props);
        assertEquals(2,_registry.getMechanisms().size());

        final String selectedMechanism = _registry.selectMechanism("TESTA");
        assertEquals("TESTA", selectedMechanism);
    }

    public void testSelectReturnsFirstMutallyAvailableMechanism()
    {
        final Properties props = new Properties();
        props.put("TESTA.1", TestACallbackHandler.class.getName());
        props.put("TESTB.2", TestBCallbackHandler.class.getName());

        _registry = new CallbackHandlerRegistry(props);

        final String selectedMechanism = _registry.selectMechanism("TESTD TESTB TESTA");
        // TESTA should be returned as it is higher than TESTB in the properties file.
        assertEquals("Selected mechanism should respect the ordinal", "TESTA", selectedMechanism);
    }

    public void testRestrictedSelectReturnsMechanismFromRestrictedList()
    {
        final Properties props = new Properties();
        props.put("TESTA.1", TestACallbackHandler.class.getName());
        props.put("TESTB.2", TestBCallbackHandler.class.getName());
        props.put("TESTC.3", TestCCallbackHandler.class.getName());

        _registry = new CallbackHandlerRegistry(props);

        final String selectedMechanism = _registry.selectMechanism("TESTC TESTB TESTA", "TESTB TESTC");
        // TESTB should be returned as client has restricted the mechanism list to TESTB and TESTC
        assertEquals("Selected mechanism should respect the ordinal and be limitted by restricted list","TESTB", selectedMechanism);
    }

    public void testOldPropertyFormatRejected()
    {
        final Properties props = new Properties();
        props.put("CallbackHandler.TESTA", TestACallbackHandler.class.getName());

        try
        {
            new CallbackHandlerRegistry(props);
            fail("exception not thrown");
        }
        catch(IllegalArgumentException iae)
        {
            // PASS
        }
    }

    public void testPropertyWithNonnumericalOrdinal()
    {
        final Properties props = new Properties();
        props.put("TESTA.z", TestACallbackHandler.class.getName());
        try
        {
            new CallbackHandlerRegistry(props);
            fail("exception not thrown");
        }
        catch(IllegalArgumentException iae)
        {
            // PASS
        }
    }

    public void testUnexpectedCallbackImplementationsIgnored()
    {
        final Properties props = new Properties();
        props.put("TESTA.1", TestACallbackHandler.class.getName());
        props.put("TESTB.2", "NotFound");
        props.put("TESTC.3", "java.lang.String");

        _registry = new CallbackHandlerRegistry(props);

        assertEquals(1,_registry.getMechanisms().size());
    }

    static class TestACallbackHandler extends TestCallbackHandler
    {
    }

    static class TestBCallbackHandler extends TestCallbackHandler
    {
    }

    static class TestCCallbackHandler extends TestCallbackHandler
    {
    }

    static abstract class TestCallbackHandler implements AMQCallbackHandler
    {
        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void initialise(ConnectionURL connectionURL)
        {
            throw new UnsupportedOperationException();
        }
    }

}
