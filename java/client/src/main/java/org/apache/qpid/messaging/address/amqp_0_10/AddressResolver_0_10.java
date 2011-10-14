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
package org.apache.qpid.messaging.address.amqp_0_10;

import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.Address.AddressType;
import org.apache.qpid.messaging.Address.PolicyType;
import org.apache.qpid.messaging.QpidDestination;
import org.apache.qpid.messaging.address.AddressException;
import org.apache.qpid.messaging.address.AddressResolver;
import org.apache.qpid.transport.ExchangeBoundResult;
import org.apache.qpid.transport.Session;

public class AddressResolver_0_10 implements AddressResolver 
{
    Session ssn;
    
    public AddressResolver_0_10(Session session)
    {
        this.ssn = session;
    }

    /**
     * 1. Check if it's an exchange (topic) or a queue
     * 2. Create a QpidTopic or a QpidQueue based on that 
     */
    public QpidDestination resolve(Address address) throws AddressException
    {
        AddressHelper_0_10 helper = new AddressHelper_0_10(address);
        
        if (!address.isResolved())
        {
            checkAddressType(address,helper);
            address.setResolved(true);
        }
        
        Link_0_10 link = new Link_0_10(helper);
        Node_0_10 node = (address.getAddressType() == AddressType.TOPIC_ADDRESS) ?
                         new ExchangeNode(helper) : new QueueNode(helper);
             
        try
        {
            QpidDestination dest = (QpidDestination) ((address.getAddressType() == AddressType.TOPIC_ADDRESS) ?
                            new QpidTopic_0_10(address,(ExchangeNode)node,link):
                            new QpidQueue_0_10(address,(QueueNode)node,link));
            
            return dest;
        }
        catch(Exception e)
        {
            throw new AddressException("Error creating destination impl",e);
        }        
    }
    
    private void checkAddressType(Address address,AddressHelper_0_10 helper) throws AddressException 
    {
        ExchangeBoundResult result = ssn.exchangeBound(address.getName(),address.getName(),
                                                      null,null).get();
        if (result.getQueueNotFound() && result.getExchangeNotFound()) {
            //neither a queue nor an exchange exists with that name; 
            //treat it as a queue unless a type is specified.
            if (helper.getNodeType() == AddressType.UNSPECIFIED)
            {
                address.setAddressType(AddressType.QUEUE_ADDRESS);
            }
            else
            {
                address.setAddressType(AddressType.TOPIC_ADDRESS);
            }
        } 
        else if (result.getExchangeNotFound()) 
        {
            //name refers to a queue
            address.setAddressType(AddressType.QUEUE_ADDRESS);
        }
        else if (result.getQueueNotFound()) 
        {
            //name refers to an exchange
            address.setAddressType(AddressType.TOPIC_ADDRESS);
        } 
        else 
        {
            //both a queue and exchange exist for that name
            if (helper.getNodeType() == AddressType.UNSPECIFIED)
            {
                throw new AddressException("Ambiguous address, please specify a node type. Ex type:{queue|topic}");
            }
        }
    }
}
