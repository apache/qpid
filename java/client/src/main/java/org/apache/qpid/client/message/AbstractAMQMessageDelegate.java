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
package org.apache.qpid.client.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;

/**
 * This abstract class provides exchange lookup functionality that is shared
 * between all MessageDelegates. Update facilities are provided so that the 0-10
 * code base can update the mappings. The 0-8 code base does not have the
 * facility to update the exchange map so it can only use the default mappings.
 *
 * That said any updates that a 0-10 client performs will also benefit any 0-8
 * connections in this VM.
 *
 */
public abstract class AbstractAMQMessageDelegate implements AMQMessageDelegate
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQMessageDelegate.class);

    private static Map<String, Integer> _exchangeTypeToDestinationType = new ConcurrentHashMap<String, Integer>();
    private static Map<String,ExchangeInfo> _exchangeMap = new  ConcurrentHashMap<String, ExchangeInfo>();

    /**
     * Add default Mappings for the Direct, Default, Topic and Fanout exchanges.
     */
    static
    {
        _exchangeTypeToDestinationType.put("", AMQDestination.QUEUE_TYPE);
        _exchangeTypeToDestinationType.put(ExchangeDefaults.DIRECT_EXCHANGE_CLASS, AMQDestination.QUEUE_TYPE);
        _exchangeTypeToDestinationType.put(ExchangeDefaults.TOPIC_EXCHANGE_CLASS, AMQDestination.TOPIC_TYPE);
        _exchangeTypeToDestinationType.put(ExchangeDefaults.FANOUT_EXCHANGE_CLASS, AMQDestination.TOPIC_TYPE);
        _exchangeTypeToDestinationType.put(ExchangeDefaults.HEADERS_EXCHANGE_CLASS, AMQDestination.QUEUE_TYPE);

        _exchangeMap.put(ExchangeDefaults.DEFAULT_EXCHANGE_NAME,
                         new ExchangeInfo(ExchangeDefaults.DEFAULT_EXCHANGE_NAME,
                                          ExchangeDefaults.DIRECT_EXCHANGE_CLASS,
                                          AMQDestination.QUEUE_TYPE));

        _exchangeMap.put(ExchangeDefaults.DIRECT_EXCHANGE_NAME,
                         new ExchangeInfo(ExchangeDefaults.DIRECT_EXCHANGE_NAME,
                                          ExchangeDefaults.DIRECT_EXCHANGE_CLASS,
                                          AMQDestination.QUEUE_TYPE));

        _exchangeMap.put(ExchangeDefaults.TOPIC_EXCHANGE_NAME,
                         new ExchangeInfo(ExchangeDefaults.TOPIC_EXCHANGE_NAME,
                                          ExchangeDefaults.TOPIC_EXCHANGE_CLASS,
                                          AMQDestination.TOPIC_TYPE));

        _exchangeMap.put(ExchangeDefaults.FANOUT_EXCHANGE_NAME,
                         new ExchangeInfo(ExchangeDefaults.FANOUT_EXCHANGE_NAME,
                                          ExchangeDefaults.FANOUT_EXCHANGE_CLASS,
                                          AMQDestination.TOPIC_TYPE));

        _exchangeMap.put(ExchangeDefaults.HEADERS_EXCHANGE_NAME,
                         new ExchangeInfo(ExchangeDefaults.HEADERS_EXCHANGE_NAME,
                                          ExchangeDefaults.HEADERS_EXCHANGE_CLASS,
                                          AMQDestination.QUEUE_TYPE));
    }

    /** If the acknowledge mode is CLIENT_ACKNOWLEDGE the session is required */
    private AMQSession<?,?> _session;
    private final long _deliveryTag;

    protected AbstractAMQMessageDelegate(long deliveryTag)
    {
        _deliveryTag = deliveryTag;
    }

    /**
     * Get the AMQ message number assigned to this message
     *
     * @return the message number
     */
    public long getDeliveryTag()
    {
        return _deliveryTag;
    }

    /**
     * Called when a Destination is requried.
     *
     * This will create the AMQDestination that is the correct type and value
     * based on the incomming values.
     * @param exchange The exchange name
     * @param routingKey The routing key to be used for the Destination
     * @return AMQDestination of the correct subtype
     */
    protected AMQDestination generateDestination(AMQShortString exchange, AMQShortString routingKey)
    {
        AMQDestination dest;
        ExchangeInfo exchangeInfo = _exchangeMap.get(exchange.asString());

        if (exchangeInfo == null)
        {
            exchangeInfo = new ExchangeInfo(exchange.asString(),"",AMQDestination.UNKNOWN_TYPE);
        }

        if ("topic".equals(exchangeInfo.getExchangeType()))
        {
            dest = new AMQTopic(exchange, routingKey, null);
        }
        else if ("direct".equals(exchangeInfo.getExchangeType()))
        {
            dest = new AMQQueue(exchange, routingKey, routingKey);
        }
        else
        {
            dest = new AMQAnyDestination(exchange,
                                         new AMQShortString(exchangeInfo.getExchangeType()),
                                         routingKey,
                                         false,
                                         false,
                                         routingKey,
                                         false,
                                         new AMQShortString[] {routingKey});
        }

        return dest;
    }

    /**
     * Update the exchange name to type mapping.
     *
     * If the newType is not known then an UNKNOWN_TYPE is created. Only if the
     * exchange is of a known type: amq.direct, amq.topic, amq.fanout can we
     * create a suitable AMQDestination representation
     *
     * @param exchange the name of the exchange
     * @param newtype the AMQP exchange class name i.e. direct
     */
    protected static void updateExchangeType(String exchange, String newtype)
    {
        Integer type = _exchangeTypeToDestinationType.get(newtype);
        if (type == null)
        {
            type = AMQDestination.UNKNOWN_TYPE;
        }

        _exchangeMap.put(exchange, new ExchangeInfo(exchange,newtype,type));
    }

    /**
     * Accessor method to allow lookups of the given exchange name.
     *
     * This check allows the prevention of extra work required such as asking
     * the broker for the exchange class name.
     *
     * @param exchange the exchange name to lookup
     * @return true if there is a mapping for this exchange
     */
    protected static boolean exchangeMapContains(String exchange)
    {
        return _exchangeMap.containsKey(exchange);
    }

    public void acknowledgeThis() throws JMSException
    {
        // the JMS 1.1 spec says in section 3.6 that calls to acknowledge are ignored when client acknowledge
        // is not specified. In our case, we only set the session field where client acknowledge mode is specified.
        if (_session != null && _session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
        {
            if (_session.getAMQConnection().isClosed())
            {
                throw new javax.jms.IllegalStateException("Connection is already closed");
            }

            // we set multiple to true here since acknowledgement implies acknowledge of all previous messages
            // received on the session
            _session.acknowledgeMessage(getDeliveryTag(), true);
        }
    }

    public void acknowledge() throws JMSException
    {
        if (_session != null && _session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
        {
            _session.acknowledge();
        }
    }

     /**
     * The session is set when CLIENT_ACKNOWLEDGE mode is used so that the CHANNEL ACK can be sent when the user calls
     * acknowledge()
     *
     * @param s the AMQ session that delivered this message
     */
    public void setAMQSession(AMQSession<?,?> s)
    {
        _session = s;
    }

    public AMQSession<?,?> getAMQSession()
    {
        return _session;
    }

    protected Destination convertToAddressBasedDestination(String exchange,
                                                           String routingKey,
                                                           String subject,
                                                           boolean useNodeTypeForDestinationType,
                                                           int type)
    {
        String addr;
        boolean isQueue = true;
        if ("".equals(exchange)) // type Queue
        {
            subject = (subject == null) ? "" : "/" + subject;
            addr = routingKey + subject;

        }
        else
        {
            addr = exchange + "/" + routingKey;
            isQueue = false;
        }

        if(useNodeTypeForDestinationType)
        {
            if(type == AMQDestination.UNKNOWN_TYPE && "".equals(exchange))
            {
                type = AMQDestination.QUEUE_TYPE;
            }

            switch(type)
            {
                case AMQDestination.QUEUE_TYPE:
                    addr = addr + " ; { node: { type: queue } } ";
                    break;
                case AMQDestination.TOPIC_TYPE:
                    addr = addr + " ; { node: { type: topic } } ";
                    break;
                default:
                    // do nothing
            }
        }


        try
        {
            AMQDestination dest = (AMQDestination)AMQDestination.createDestination("ADDR:" + addr,
                                                                                   useNodeTypeForDestinationType);
            if (isQueue)
            {
                dest.setQueueName(new AMQShortString(routingKey));
                dest.setRoutingKey(new AMQShortString(routingKey));
                dest.setExchangeName(new AMQShortString(""));
            }
            else
            {
                dest.setRoutingKey(new AMQShortString(routingKey));
                dest.setExchangeName(new AMQShortString(exchange));
            }
            return dest;
        }
        catch(Exception e)
        {
            // An exception is only thrown here if the address syntax is invalid.
            // Logging the exception, but not throwing as this is only important to Qpid developers.
            // An exception here means a bug in the code.
            _logger.error("Exception when constructing an address string from the ReplyTo struct");

            // falling back to the old way of doing it to ensure the application continues.
            return generateDestination(new AMQShortString(exchange), new AMQShortString(routingKey));
        }
    }
}

class ExchangeInfo
{
    private String exchangeName;
    private String exchangeType;
    private int destType = AMQDestination.QUEUE_TYPE;

    public ExchangeInfo(String exchangeName, String exchangeType,
                        int destType)
    {
        super();
        this.exchangeName = exchangeName;
        this.exchangeType = exchangeType;
        this.destType = destType;
    }

    public String getExchangeName()
    {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName)
    {
        this.exchangeName = exchangeName;
    }

    public String getExchangeType()
    {
        return exchangeType;
    }

    public void setExchangeType(String exchangeType)
    {
        this.exchangeType = exchangeType;
    }

    public int getDestType()
    {
        return destType;
    }

    public void setDestType(int destType)
    {
        this.destType = destType;
    }
}
