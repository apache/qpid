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
package org.apache.qpidity.jms.message;

import javax.jms.Message;
import javax.jms.JMSException;


public class QpidMessage
{
    /**
     * The underlying qpidity message 
     */
    private org.apache.qpidity.api.Message _qpidityMessage;


    //---  javax.jsm.Message API
    /**
     * Get the message ID.
     * <p> The JMS sprec says:
     * <p>The messageID header field contains a value that uniquely
     * identifies each message sent by a provider.
     * <p>When a message is sent, messageID can be ignored. When
     * the send method returns it contains a provider-assigned value.
     * <P>All JMSMessageID values must start with the prefix `ID:'.
     * Uniqueness of message ID values across different providers is
     * not required.
     *
     * @return The message ID
     * @throws JMSException If getting the message Id fails due to internal JMS error.
     */
    public String getJMSMessageID() throws JMSException
    {
        return "ID:" + _qpidityMessage.getMessageProperties().getMessageId();
    }



    public Message getJMSMessage()
    {
        // todo
        return null;
    }

    public Long getMessageID()
    {
        //todo
        return new Long(1);
    }

}


