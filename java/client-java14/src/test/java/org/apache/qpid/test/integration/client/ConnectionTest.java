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
package org.apache.qpid.test.integration.client;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import org.apache.qpid.client.AMQConnection;

/**
 * Implements a trivial broker connection test, to a broker on the default port on localhost, to check that SASL
 * authentication works.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Check that a connection to a broker can be established.
 * </table>
 */
public class ConnectionTest extends TestCase
{
    private String BROKER_URL = "tcp://localhost:5672";

    public ConnectionTest(String name)
    {
        super(name);
    }

    /** Check that a connection to a broker can be established. */
    public void testConnection() throws Exception
    {
        // Open a connection to the broker and close it again.
        AMQConnection conn = new AMQConnection(BROKER_URL, "guest", "guest", "clientid", "test");
        conn.close();
    }

    protected void setUp()
    {
        NDC.push(getName());
    }

    protected void tearDown()
    {
        NDC.pop();
    }
}
