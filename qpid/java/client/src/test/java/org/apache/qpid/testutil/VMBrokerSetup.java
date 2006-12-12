/*
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
package org.apache.qpid.testutil;

import junit.extensions.TestSetup;
import junit.framework.Test;

import org.apache.qpid.client.transport.TransportConnection;

public class VMBrokerSetup extends TestSetup
{
    public VMBrokerSetup(Test t)
    {
        super(t);
    }

    protected void setUp() throws Exception
    {
        super.setUp();
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (Exception e)
        {
            fail("Unable to create broker: " + e);
        }
    }

    protected void tearDown() throws Exception
    {
        TransportConnection.killVMBroker(1);
        super.tearDown();
    }
}
