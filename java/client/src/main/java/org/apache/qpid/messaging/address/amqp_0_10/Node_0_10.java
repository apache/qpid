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

import java.util.Map;

import org.apache.qpid.messaging.address.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Node_0_10 extends Node 
{
    private static final Logger _logger = LoggerFactory.getLogger(Node_0_10.class);
    
    private boolean autoDelete = false;    
    private String altExchange;
    private Map<String,Object> declareArgs;
             
    public Node_0_10(AddressHelper_0_10 helper)
    {
        super(helper);
        declareArgs = helper.getNodeDeclareArgs();  
        autoDelete = helper.isNodeAutoDelete();
        altExchange = helper.getNodeAltExchange();
    }
    
    public boolean isAutoDelete() 
    {
        return autoDelete;
    }

    public String getAltExchange() 
    {
        return altExchange;
    }

    public Map<String, Object> getDeclareArgs() 
    {
        return declareArgs;
    }

    public boolean matchProps(Map<String,Object> target)
    {
        boolean match = true;
        Map<String,Object> source = declareArgs;
        for (String key: source.keySet())
        {
            match = target.containsKey(key) && 
                    target.get(key).equals(source.get(key));
            
            if (!match) 
            { 
                StringBuffer buf = new StringBuffer();
                buf.append("Property given in address did not match with the args sent by the broker.");
                buf.append(" Expected { ").append(key).append(" : ").append(source.get(key)).append(" }, ");
                buf.append(" Actual { ").append(key).append(" : ").append(target.get(key)).append(" }");
                _logger.debug(buf.toString());
                return match;
            }
        }
        
        return match;
    }
}