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

import javax.jms.Destination;
import javax.jms.JMSException;

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

    //--- Constructor
    /**
     * Create a new DestinationImpl with a given name.
     *
     * @param name The name of this destination.
     * @param session The session used to create this destination.
     * @throws JMSException If the destiantion name is not valid 
     */
    protected DestinationImpl(SessionImpl session,  String name)  throws JMSException
    {
        // TODO validate that this destination name exists
        //_session.getQpidSession()
        _session = session;
        _name = name;
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

}

