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
package org.apache.qpidity.jms;

import org.apache.qpidity.QpidException;
import org.apache.qpidity.Option;
import org.apache.qpidity.url.BindingURL;

import javax.jms.Destination;

/**
 * Implementation of the JMS Destination interface
 */
public class DestinationImpl implements Destination
{
    /**
     * The destination's name
     */
    protected String _name = null;

    /**
     * The session used to create this destination
     */
    protected SessionImpl _session;

    /**
     * The excahnge name
     */
    protected String _exchangeName;

     /**
     * The excahnge class
     */
    protected String _exchangeClass;

     /**
     * The queu name
     */
    protected String _queueName;

    //--- Constructor
    /**
     * Create a new DestinationImpl with a given name.
     *
     * @param name    The name of this destination.
     * @param session The session used to create this destination.
     * @throws QpidException If the destiantion name is not valid
     */
    protected DestinationImpl(SessionImpl session, String name) throws QpidException
    {
        _session = session;
        _name = name;
    }

    /**
     * Create a destiantion from a binding URL
     *
     * @param session The session used to create this queue.
     * @param binding The URL
     * @throws QpidException If the URL is not valid
     */
    protected DestinationImpl(SessionImpl session, BindingURL binding) throws QpidException
    {
        _session = session;
        _exchangeName = binding.getExchangeName();
        _exchangeClass = binding.getExchangeClass();
        _name = binding.getDestinationName();
        //       _isExclusive = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCLUSIVE));
        boolean isAutoDelete = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_AUTODELETE));
        boolean isDurable = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_DURABLE));
        _queueName = binding.getQueueName();
        // create this exchange
        _session.getQpidSession().exchangeDeclare(_exchangeName, _exchangeClass, null, null,
                                                  isDurable ? Option.DURABLE : Option.NO_OPTION,
                                                  isAutoDelete ? Option.AUTO_DELETE : Option.NO_OPTION);
    }

    //---- Getters and Setters

    /**
     * Gets the name of this destination.
     *
     * @return The destination name.
     */
    public String getName()
    {
        return _name;
    }

    /**
     * set the destination name
     * <p> This name is not verified until producing or consuming messages for that destination.
     *
     * @param name The destination name.
     */
    public void setName(String name)
    {
        _name = name;
    }

    /**
     * Overrides Object.toString();
     *
     * @return Stringified destination representation.
     */
    public String toString()
    {
        return _name;
    }

    // getter methods 
    public String getQpidQueueName()
    {
        return _queueName;
    }

    public String getExchangeName()
    {
        return _exchangeName;
    }

    public String getExchangeClass()
    {
        return _exchangeClass;
    }
}

