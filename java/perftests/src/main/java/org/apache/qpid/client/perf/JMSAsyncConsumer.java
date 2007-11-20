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
package org.apache.qpid.client.perf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 *
 */
public class JMSAsyncConsumer implements MessageListener, JMSConsumer
{
    private static final Logger _logger = LoggerFactory.getLogger(JMSSyncConsumer.class);

    private String _id;
    private Connection _connection;
    private Session _session;
    private MessageConsumer _consumer;
    private Destination _destination;
    private boolean _transacted;
    private int _ackMode = Session.AUTO_ACKNOWLEDGE;
    private AtomicBoolean _run = new AtomicBoolean(true);
    private long _currentMsgCount;
    private boolean _verifyOrder = false;

    /* Not implementing transactions for first phase */
    public JMSAsyncConsumer(String id,Connection connection, Destination destination,boolean transacted,int ackMode) throws Exception
    {
        _id = id;
        _connection = connection;
        _destination = destination;
        _transacted = transacted;
        _ackMode = ackMode;
        _session = _connection.createSession(_transacted, _ackMode);
        _consumer = _session.createConsumer(_destination);
        _consumer.setMessageListener(this);
        _verifyOrder = Boolean.getBoolean("verifyOrder");
    }



    public void onMessage(Message message)
    {
        try
        {
            long msgId = Integer.parseInt(message.getJMSCorrelationID());
            if (_verifyOrder && _currentMsgCount+1 != msgId)
            {
                _logger.error("Error : Message received out of order in JMSSyncConsumer:" + _id + " message id was " + msgId + " expected: " + _currentMsgCount+1);
            }
            _currentMsgCount ++;
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public void stopConsuming()
    {
        System.out.println("Consumer received notification to stop");
        try
        {
            _session.close();
            _connection.close();
        }
        catch(Exception e)
        {
            _logger.error("Error Closing JMSSyncConsumer:"+ _id, e);
        }
    }

    public String getId()
    {
        return _id;
    }

    /* Not worried about synchronizing as accuracy here is not that important.
     * So if this method is invoked the count maybe off by a few digits.
     * But when the test stops, this will always return the proper count.
     */
    public long getCurrentMessageCount()
    {
        return _currentMsgCount;
    }
}
