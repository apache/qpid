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
package org.apache.qpid.nclient.jms;

import javax.jms.MessageProducer;
import javax.jms.JMSException;
import javax.jms.Destination;
import javax.jms.Message;

/**
 *  Implements  MessageProducer
 */
public class MessageProducerImpl extends MessageActor implements MessageProducer
{

    public MessageProducerImpl(SessionImpl session, DestinationImpl destination)
    {
        super(session, destination);
    }

    // Interface javax.jms.MessageProducer

    public void setDisableMessageID(boolean b) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean getDisableMessageID() throws JMSException
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setDisableMessageTimestamp(boolean b) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean getDisableMessageTimestamp() throws JMSException
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setDeliveryMode(int i) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getDeliveryMode() throws JMSException
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setPriority(int i) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getPriority() throws JMSException
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setTimeToLive(long l) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getTimeToLive() throws JMSException
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Destination getDestination() throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void send(Message message) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void send(Message message, int i, int i1, long l) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void send(Destination destination, Message message) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void send(Destination destination, Message message, int i, int i1, long l) throws JMSException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
