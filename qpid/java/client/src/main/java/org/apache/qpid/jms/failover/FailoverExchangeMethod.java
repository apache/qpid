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
package org.apache.qpid.jms.failover;

import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQBrokerDetails;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.jms.ConnectionURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When using the Failover exchange a single broker is supplied in the URL.
 * The connection will then connect to the cluster using the above broker details.
 * Once connected, the membership details of the cluster will be obtained via 
 * subscribing to a queue bound to the failover exchange.
 * 
 * The failover exchange will provide a list of broker URLs in the format "transport:ip:port"
 * Out of this list we only select brokers that match the transport of the original 
 * broker supplied in the connection URL.
 * 
 * Also properties defined for the original broker will be applied to all the brokers selected
 * from the list.   
 */

public class FailoverExchangeMethod extends FailoverRoundRobinServers implements FailoverMethod, MessageListener
{
    private static final Logger _logger = LoggerFactory.getLogger(FailoverExchangeMethod.class);
   
    /** This is not safe to use until attainConnection is called */
    private AMQConnection _conn;
    
    /** Protects the broker list when modifications happens */
    private Object _brokerListLock = new Object();
    
    /** The session used to subscribe to failover exchange */
    private Session _ssn;
    
    private BrokerDetails _orginalBrokerDetail;
    
    public FailoverExchangeMethod(ConnectionURL connectionDetails, AMQConnection conn)
    {
        super(connectionDetails);        
        _orginalBrokerDetail = _connectionDetails.getBrokerDetails(0);
        
        // This is not safe to use until attainConnection is called, as this ref will not initialized fully.
        // The reason being this constructor is called inside the AMWConnection constructor.
        // It would be best if we find a way to pass this ref after AMQConnection is fully initialized.
        _conn = conn; 
    }

    private void subscribeForUpdates() throws JMSException
    {
        if (_ssn == null)
        {
            _ssn = _conn.createSession(false,Session.AUTO_ACKNOWLEDGE);
            MessageConsumer cons = _ssn.createConsumer(
                                        new AMQAnyDestination(new AMQShortString("amq.failover"),
                                                              new AMQShortString("amq.failover"),
                                                              new AMQShortString(""),
                                                              true,true,null,false,
                                                              new AMQShortString[0])); 
            cons.setMessageListener(this);
        }                               
    }
    
    public void onMessage(Message m)
    {
        _logger.info("Failover exchange notified cluster membership change");
        List<BrokerDetails> brokerList = new ArrayList<BrokerDetails>();
        try
        {            
            List<String> list = (List<String>)m.getObjectProperty("amq.failover");
            for (String brokerEntry:list)
            {                
                String[] urls = brokerEntry.substring(5) .split(",");
                // Iterate until you find the correct transport
                // Need to reconsider the logic when the C++ broker supports
                // SSL URLs.
                for (String url:urls)
                {
                    String[] tokens = url.split(":");
                    if (tokens[0].equalsIgnoreCase(_orginalBrokerDetail.getTransport()))
                    {
                        BrokerDetails broker = new AMQBrokerDetails();
                        broker.setTransport(tokens[0]);
                        broker.setHost(tokens[1]);
                        broker.setPort(Integer.parseInt(tokens[2]));
                        broker.setProperties(_orginalBrokerDetail.getProperties());
                        broker.setSSLConfiguration(_orginalBrokerDetail.getSSLConfiguration());
                        brokerList.add(broker);
                        break;
                    }
                }                
            }
        }
        catch(JMSException e)
        {
            _logger.error("Error parsing the message sent by failover exchange",e);
        }
        
        synchronized (_brokerListLock)
        {
            _connectionDetails.setBrokerDetails(brokerList);
        }
    }
    
    public void attainedConnection()
    {
        super.attainedConnection();
        try
        {
            subscribeForUpdates();
        }
        catch (JMSException e)
        {
            throw new RuntimeException("Unable to subscribe for cluster membership updates",e);
        }
    }

    public BrokerDetails getCurrentBrokerDetails()
    {
        synchronized (_brokerListLock)
        {
            return super.getCurrentBrokerDetails();
        }
    }

    public BrokerDetails getNextBrokerDetails()
    {
        synchronized(_brokerListLock)
        {
            return super.getNextBrokerDetails();
        }
    }

    public String methodName()
    {
        return "Failover Exchange";
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("FailoverExchange:\n");
        sb.append(super.toString());
        return sb.toString();
    }
}
