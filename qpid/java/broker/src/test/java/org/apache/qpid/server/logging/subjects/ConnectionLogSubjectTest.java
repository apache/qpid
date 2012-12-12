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

import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.util.BrokerTestHelper;

/**
 * Validate ConnectionLogSubjects are logged as expected
 */
public class ConnectionLogSubjectTest extends AbstractTestLogSubject
{

    private InternalTestProtocolSession _session;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _session = BrokerTestHelper.createSession("test");
        _subject = new ConnectionLogSubject(_session);
    }

    @Override
    public void tearDown() throws Exception
    {
        if (_session != null)
        {
            _session.getVirtualHost().close();
        }
        super.tearDown();
    }

    /**
     * MESSAGE [Blank][con:0(MockProtocolSessionUser@null/test)] <Log Message>
     *
     * @param message the message whos format needs validation
     */
    protected void validateLogStatement(String message)
    {
        verifyConnection(_session.getSessionID(), "InternalTestProtocolSession", "127.0.0.1:1", "test", message);
    }

    public InternalTestProtocolSession getSession()
    {
        return _session;
    }

}
