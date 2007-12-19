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
package org.apache.qpid.client.latency;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.perf.Options;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.requestreply.InitialContextHelper;

import javax.jms.*;
import javax.naming.Context;

/**
 *
 *
 */
public class MessageConsumer  extends Options  implements MessageListener 
{
    private javax.jms.MessageProducer _producer;
    private AMQConnection _connection;
    private final Object _lock = new Object();
    private Session _session;
    private int _receivedMessages = 0;
    private long _timeFirstMessage;
    private long _timeLastMessage;
   private void init()
    {
        this.parseOptions();
        try
        {
            Context context = InitialContextHelper.getInitialContext("");
            ConnectionFactory factory = (ConnectionFactory) context.lookup("local");
             _connection = (AMQConnection) factory.createConnection("guest","guest");
             _session = _connection.createSession(_transacted, Session.AUTO_ACKNOWLEDGE);
              Destination dest = Boolean.getBoolean("useQueue")? (Destination) context.lookup("testQueue") :
            (Destination) context.lookup("testTopic");
            Destination syncQueue   = (Destination) context.lookup("syncQueue");
            _producer = _session.createProducer(syncQueue);
            // this should speedup the message producer
            _producer.setDisableMessageTimestamp(true);
            javax.jms.MessageConsumer consumer = _session.createConsumer(dest);
            consumer.setMessageListener(this);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void run()
    {
        try
        {
           synchronized(_lock)
           {
               _connection.start();
               try
               {
                   _lock.wait();
               }
               catch (InterruptedException e)
               {
                   e.printStackTrace();
               }
           }
            // send sync message;
           _producer.send(_session.createMessage());
            System.out.println("Time to receive " + _logFrequency + " messages is: " + (_timeLastMessage - _timeFirstMessage) );
            double rate =  _logFrequency /  ((_timeLastMessage - _timeFirstMessage)  *1.0) *1000 ;
             System.out.println("The rate is " + rate  + " msg/s" );
            double latency =  ((_timeLastMessage - _timeFirstMessage)  *1.0) / _logFrequency;
            System.out.println("The latency is " + latency  + " milli secs" );
            _connection.close();
        }
        catch (JMSException e)
        {
            e.printStackTrace();
        }
    }

   public void onMessage(Message message)
    {
        if( _receivedMessages == 0)
        {
            _timeFirstMessage = System.currentTimeMillis();
        }
        _receivedMessages++;      
        if( _receivedMessages == _logFrequency)
        {
            _timeLastMessage = System.currentTimeMillis();
            synchronized(_lock)
            {
                _lock.notify();
            }
        }
    }

    public static void main(String[] args)
    {
        try
        {
            MessageConsumer test = new MessageConsumer();
            test.init();
            test.run();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

}