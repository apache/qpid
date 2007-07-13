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
package org.apache.qpid.test.framework;

import junit.framework.TestCase;

import org.apache.log4j.NDC;

import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.server.registry.ApplicationRegistry;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * FrameworkBaseCase provides a starting point for writing test cases against the test framework. Its main purpose is
 * to provide some convenience methods for testing.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Create and clean up in-vm brokers on every test case.
 * <tr><td> Produce lists of assertions from assertion creation calls.
 * <tr><td> Produce JUnit failures from assertion failures.
 * <tr><td> Convert failed assertions to error messages.
 * </table>
 */
public class FrameworkBaseCase extends TestCase
{
    /**
     * Creates a list of assertions.
     *
     * @param asserts The assertions to compile in a list.
     *
     * @return A list of assertions.
     */
    protected List<Assertion> assertionList(Assertion... asserts)
    {
        List<Assertion> result = new ArrayList<Assertion>();

        for (Assertion assertion : asserts)
        {
            result.add(assertion);
        }

        return result;
    }

    /**
     * Generates a JUnit assertion exception (failure) if any assertions are passed into this method, also concatenating
     * all of the error messages in the assertions together to form an error message to diagnose the test failure with.
     *
     * @param asserts The list of failed assertions.
     */
    protected void assertNoFailures(List<Assertion> asserts)
    {
        // Check if there are no assertion failures, and return without doing anything if so.
        if ((asserts == null) || asserts.isEmpty())
        {
            return;
        }

        // Compile all of the assertion failure messages together.
        String errorMessage = assertionsToString(asserts);

        // Fail with the error message from all of the assertions.
        fail(errorMessage);
    }

    /**
     * Converts a list of failed assertions into an error message.
     *
     * @param asserts The failed assertions.
     *
     * @return The error message.
     */
    protected String assertionsToString(List<Assertion> asserts)
    {
        String errorMessage = "";

        for (Assertion assertion : asserts)
        {
            errorMessage += assertion.toString() + "\n";
        }

        return errorMessage;
    }

    /**
     * Ensures that the in-vm broker is created and initialized.
     *
     * @throws Exception Any exceptions allowed to fall through and fail the test.
     */
    protected void setUp() throws Exception
    {
        NDC.push(getName());

        // Ensure that the in-vm broker is created.
        TransportConnection.createVMBroker(1);
    }

    /**
     * Ensures that the in-vm broker is cleaned up after each test run.
     */
    protected void tearDown()
    {
        try
        {
            // Ensure that the in-vm broker is cleaned up so that the next test starts afresh.
            TransportConnection.killVMBroker(1);
            ApplicationRegistry.remove(1);
        }
        finally
        {
            NDC.pop();
        }
    }
}
