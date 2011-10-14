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
package org.apache.qpid.messaging.address;

import static org.apache.qpid.messaging.address.AddressProperty.*;

import org.apache.qpid.configuration.Accessor;
import org.apache.qpid.configuration.Accessor.NestedMapAccessor;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.Address.PolicyType;
import org.apache.qpid.messaging.Address.AddressType;
import org.apache.qpid.messaging.QpidDestination.CheckMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressHelper 
{
    private static final Logger _logger = LoggerFactory.getLogger(AddressHelper.class);
    
    protected Address address;
    protected Accessor addressProps;

    public AddressHelper(Address address)
    {
        this.address = address;
        addressProps = new NestedMapAccessor(address.getOptions());
    }

    public PolicyType getCreate()
    {
        return PolicyType.getPolicyType(addressProps.getString(CREATE));
    }

    public PolicyType getAssert()
    {
        return PolicyType.getPolicyType(addressProps.getString(ASSERT));
    }

    public PolicyType getDelete()
    {
        return PolicyType.getPolicyType(addressProps.getString(DELETE));
    }
    
    public boolean isBrowseOnly()
    {
        String mode = addressProps.getString(MODE);
        return mode != null && mode.equals(BROWSE) ? true : false;
    }
    
    public AddressType getNodeType()
    {
        String type = addressProps.getString(getFQN(NODE,TYPE));
        if ("topic".equalsIgnoreCase(type))
        {
           return AddressType.TOPIC_ADDRESS;
        }
        else if ("queue".equalsIgnoreCase(type))
        {
            return AddressType.QUEUE_ADDRESS;
        }
        else
        {
            return AddressType.UNSPECIFIED;
        }
    }
    
    public boolean isNodeDurable()
    {
         Boolean b = addressProps.getBoolean(getFQN(NODE,DURABLE));
         return b == null ? false : b.booleanValue();
    }
    
    public String getLinkName()
    {
        return addressProps.getString(getFQN(LINK,NAME));
    }
    
    public boolean isLinkDurable()
    {
         Boolean b = addressProps.getBoolean(getFQN(LINK,DURABLE));
         return b == null ? false : b.booleanValue();
    }
    
    public String getLinkReliability()
    {
        return addressProps.getString(getFQN(LINK,RELIABILITY));
    }
    
    public int getLinkProducerCapacity()
    {
        return getCapacity(CheckMode.FOR_SENDER);
    }
    
    public int getLinkConsumerCapacity()
    {
        return getCapacity(CheckMode.FOR_RECEIVER);
    }
    
    private int getCapacity(CheckMode  mode)
    {
        int capacity = 0;
        try
        {
            capacity = addressProps.getInt(getFQN(LINK,CAPACITY));            
        }
        catch(Exception e)
        {
            try
            {
                if (mode == CheckMode.FOR_RECEIVER)
                {
                    capacity = addressProps.getInt(getFQN(LINK,CAPACITY,CAPACITY_SOURCE));
                }
                else
                {
                    capacity = addressProps.getInt(getFQN(LINK,CAPACITY,CAPACITY_TARGET));
                }
            }
            catch(Exception ex)
            {
                if (ex instanceof NumberFormatException && !ex.getMessage().equals("null"))
                {
                    _logger.info("Unable to retrieve capacity from address: " + address,ex);
                }
            }
        }
        
        return capacity;
    }
    
    public static boolean isAllowed(PolicyType policy, CheckMode mode)
    {
        return (policy == PolicyType.ALWAYS ||
                (policy == PolicyType.RECEIVER && mode == CheckMode.FOR_RECEIVER) ||
                (policy == PolicyType.SENDER && mode == CheckMode.FOR_SENDER)
               );
        
    }
}
