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
 */
package org.apache.qpid.amqp_1_0.jms.impl;

import org.apache.qpid.amqp_1_0.client.Sender;
import org.apache.qpid.amqp_1_0.jms.MessageConsumer;
import org.apache.qpid.amqp_1_0.jms.TemporaryQueue;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TemporaryQueueImpl extends QueueImpl implements TemporaryQueue
{
    private Sender _sender;
    private SessionImpl _session;
    private final Set<MessageConsumer> _consumers =
            Collections.synchronizedSet(new HashSet<MessageConsumer>());

    protected TemporaryQueueImpl(String address, Sender sender, SessionImpl session)
    {
        super(address);
        _sender = sender;
        _session = session;
        _session.getConnection().addOnCloseTask(new ConnectionImpl.CloseTask()
        {
            public void onClose() throws JMSException
            {
                synchronized (TemporaryQueueImpl.this)
                {
                    close();
                }
            }
        });
    }

    public synchronized void delete() throws JMSException
    {
        if(_consumers.isEmpty())
        {
           close();
        }
        else
        {
            throw new IllegalStateException("Cannot delete destination as it has consumers");
        }
    }

    private void close() throws JMSException
    {
        if(_sender != null)
        {
            try
            {
                _sender.close();
                _sender = null;
            }
            catch (Sender.SenderClosingException e)
            {
                final JMSException jmsException = new JMSException(e.getMessage());
                jmsException.setLinkedException(e);
                throw jmsException;
            }
        }

    }

    public SessionImpl getSession()
    {
        return _session;
    }

    public void addConsumer(MessageConsumer consumer)
    {
        _consumers.add(consumer);
    }

    public void removeConsumer(MessageConsumer consumer)
    {
        _consumers.remove(consumer);
    }

    public boolean isDeleted()
    {
        return _sender == null;
    }
}
