/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpidity.njms;

import org.apache.qpidity.QpidException;

import javax.jms.XATopicConnection;
import javax.jms.JMSException;
import javax.jms.XATopicSession;

/**
 * implements javax.njms.XATopicConnection
 */
public class XATopicConnectionImpl extends XAConnectionImpl implements XATopicConnection
{
    //-- constructor
    /**
     * Create a XATopicConnection.
     *
     * @param host        The broker host name.
     * @param port        The port on which the broker is listening for connection.
     * @param virtualHost The virtual host on which the broker is deployed.
     * @param username    The user name used of user identification.
     * @param password    The password name used of user identification.
     * @throws QpidException If creating a XATopicConnection fails due to some internal error.
     */
    public XATopicConnectionImpl(String host, int port, String virtualHost, String username, String password)
            throws QpidException
    {
        super(host, port, virtualHost, username, password);
    }

    /**
     * Creates an XATopicSession.
     *
     * @return A newly created XATopicSession.
     * @throws JMSException If the XAConnectiono fails to create an XATopicSession due to
     *                      some internal error.
     */
    public synchronized XATopicSession createXATopicSession() throws JMSException
    {
        checkNotClosed();
        XATopicSessionImpl xaTopicSession;
        try
        {
            xaTopicSession = new XATopicSessionImpl(this);
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        // add this session with the list of session that are handled by this connection
        _sessions.add(xaTopicSession);
        return xaTopicSession;
    }
}
