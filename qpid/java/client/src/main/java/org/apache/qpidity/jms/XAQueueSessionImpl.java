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

import javax.jms.QueueSession;
import javax.jms.JMSException;
import javax.jms.XAQueueSession;

/**
 * Imeplements javax.njms.XAQueueSessionImpl
 */
public class XAQueueSessionImpl extends XASessionImpl implements XAQueueSession
{
    /**
     * The standard session
     */
    private QueueSession _jmsQueueSession;

    //-- Constructors
    /**
     * Create a JMS XAQueueSessionImpl
     *
     * @param connection The ConnectionImpl object from which the Session is created.
     * @throws org.apache.qpidity.QpidException
     *          In case of internal error.
     */
    protected XAQueueSessionImpl(ConnectionImpl connection) throws QpidException
    {
        super(connection);
    }

    //--- interface  XAQueueSession
    /**
     * Gets the topic session associated with this <CODE>XATopicSession</CODE>.
     *
     * @return the topic session object
     * @throws JMSException If an internal error occurs.
     */
    public QueueSession getQueueSession() throws JMSException
    {
        if (_jmsQueueSession == null)
        {
            _jmsQueueSession = getConnection().createQueueSession(true, getAcknowledgeMode());
        }
        return _jmsQueueSession;
    }
}
