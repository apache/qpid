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

import org.apache.qpid.server.protocol.AMQConnectionModel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validate ConnectionLogSubjects are logged as expected
 */
public class ConnectionLogSubjectTest extends AbstractTestLogSubject
{

    private static final long CONNECTION_ID = 456l;
    private static final String USER = "InternalTestProtocolSession";
    private static final String IP_STRING = "127.0.0.1:1";
    private static final String VHOST = "test";

    private AMQConnectionModel _connection;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _connection = mock(AMQConnectionModel.class);
        when(_connection.getConnectionId()).thenReturn(CONNECTION_ID);
        when(_connection.getPrincipalAsString()).thenReturn(USER);
        when(_connection.getRemoteAddressString()).thenReturn("/"+IP_STRING);
        when(_connection.getVirtualHostName()).thenReturn(VHOST);
        _subject = new ConnectionLogSubject(_connection);
    }

    /**
     * MESSAGE [Blank][con:0(MockProtocolSessionUser@null/test)] <Log Message>
     *
     * @param message the message whos format needs validation
     */
    protected void validateLogStatement(String message)
    {
        verifyConnection(CONNECTION_ID, USER, IP_STRING, VHOST, message);
    }

    public AMQConnectionModel getConnection()
    {
        return _connection;
    }

}
