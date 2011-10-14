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

import static org.apache.qpid.messaging.address.AddressProperty.*;
import static org.apache.qpid.messaging.address.amqp_0_10.AddressProperty_0_10.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.configuration.Accessor;
import org.apache.qpid.configuration.Accessor.MapAccessor;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.address.AddressHelper;


public class AddressHelper_0_10 extends AddressHelper 
{
    protected Accessor declareProps;
    
    public AddressHelper_0_10(Address address) 
    {
        super(address);
    }
    
    // Node properties --------------------
    
    public boolean isNodeExclusive()
    {
        Boolean b = addressProps.getBoolean(getFQN(NODE,X_DECLARE,EXCLUSIVE));
        return b == null ? false : b.booleanValue();
    }
    
    public boolean isNodeAutoDelete()
    {
        Boolean b = addressProps.getBoolean(getFQN(NODE,X_DECLARE,AUTO_DELETE));
        return b == null ? false : b.booleanValue();
    }
    
    public String getNodeAltExchange()
    {
       return addressProps.getString(getFQN(NODE,X_DECLARE,ALT_EXCHANGE));
    }

    public Map getNodeDeclareArgs()
    {
        Map map = addressProps.getMap(getFQN(NODE,X_DECLARE,ARGUMENTS));
        return map == null ? Collections.EMPTY_MAP : map;
    }
    
    public String getNodeExchangeType() 
    {
        String type = addressProps.getString(getFQN(NODE,X_DECLARE,EXCHANGE_TYPE));
        return type == null ? "topic" : type;
    }
    
    public List<Binding> getNodeBindings()
    {
       return getBindings(addressProps.getList(getFQN(NODE,X_BINDINGS)));
    }

    // Link properties --------------------
    
    public List<Binding> getLinkQueueBindings()
    {
       return getBindings(addressProps.getList(getFQN(LINK,X_BINDINGS)));
    }
    
    public Map getLinkQueueDeclareArgs()
    {
        Map map = addressProps.getMap(getFQN(LINK,X_DECLARE,ARGUMENTS));
        return map == null ? Collections.EMPTY_MAP : map;
    }   
    
    public Boolean isLinkQueueExclusive()
    {
        return addressProps.getBoolean(getFQN(LINK,X_DECLARE,EXCLUSIVE));
    }
    
    public Boolean isLinkQueueAutoDelete()
    {
        return addressProps.getBoolean(getFQN(LINK,X_DECLARE,AUTO_DELETE));
    }
    
    public String getLinkQueueAltExchange()
    {
       return addressProps.getString(getFQN(LINK,X_DECLARE,ALT_EXCHANGE));
    }

    public Boolean isSubscriptionExclusive()
    {
       return addressProps.getBoolean(getFQN(LINK,X_SUBSCRIBE,EXCLUSIVE));
    }
    
    public Map getSubscriptionArguments()
    {
       Map m = addressProps.getMap(getFQN(LINK,X_SUBSCRIBE,ARGUMENTS));
       return m == null ? Collections.EMPTY_MAP : m;
    }
    
    @SuppressWarnings("unchecked")
    private List<Binding> getBindings(List<Map> bindingList)
    {
        List<Binding> bindings = new ArrayList<Binding>();
        if (bindingList != null)
        {
            for (Map map : bindingList)
            {
                MapAccessor bindingMap = new MapAccessor(map);
                Binding binding = new Binding(
                        bindingMap.getString(EXCHANGE),
                        bindingMap.getString(QUEUE),
                        bindingMap.getString(KEY),
                        bindingMap.getMap(ARGUMENTS) == null ? Collections.EMPTY_MAP
                                : bindingMap.getMap(ARGUMENTS));
                bindings.add(binding);
            }
        }
        return bindings;
    }
}
