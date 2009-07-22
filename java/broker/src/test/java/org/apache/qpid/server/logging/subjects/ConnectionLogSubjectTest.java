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
package org.apache.qpid.server.logging.subjects;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.MockProtocolSession;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class ConnectionLogSubjectTest extends AbstractTestLogSubject
{
    AMQProtocolSession _session;

    public void setUp() throws Exception
    {
        super.setUp();

        // Create a single session for this test.
        // Re-use is ok as we are testing the LogActor object is set correctly,
        // not the value of the output.
        _session = new MockProtocolSession(new MemoryMessageStore());
        // Use the first Virtualhost that has been defined to initialise
        // the MockProtocolSession. This prevents a NPE when the
        // AMQPActor attempts to lookup the name of the VHost.
        try
        {
            _session.setVirtualHost(ApplicationRegistry.getInstance().
                    getVirtualHostRegistry().getVirtualHosts().
                    toArray(new VirtualHost[1])[0]);
        }
        catch (AMQException e)
        {
            fail("Unable to set virtualhost on session:" + e.getMessage());
        }

        _subject = new ConnectionLogSubject(_session);
    }

    /**
     * MESSAGE [Blank][con:0(MockProtocolSessionUser@null/test)] <Log Message>
     *
     * @param message the message whos format needs validation
     */
    protected void validateLogStatement(String message)
    {
        verifyConnection(_session.getSessionID(), "MockProtocolSessionUser", "null", "test", message);
    }

}
