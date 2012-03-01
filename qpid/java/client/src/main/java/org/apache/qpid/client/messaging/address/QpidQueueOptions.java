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
package org.apache.qpid.client.messaging.address;

import java.util.HashMap;

public class QpidQueueOptions extends HashMap<String,Object> 
{	
	public static final String QPID_MAX_COUNT = "qpid.max_count";
    public static final String QPID_MAX_SIZE = "qpid.max_size";
    public static final String QPID_POLICY_TYPE = "qpid.policy_type";    
    public static final String QPID_PERSIST_LAST_NODE = "qpid.persist_last_node";
    public static final String QPID_LVQ_KEY = "qpid.LVQ_key";
    public static final String QPID_LAST_VALUE_QUEUE = "qpid.last_value_queue";
    public static final String QPID_LAST_VALUE_QUEUE_NO_BROWSE = "qpid.last_value_queue_no_browse";
    public static final String QPID_QUEUE_EVENT_GENERATION = "qpid.queue_event_generation";

    public void validatePolicyType(String type)
    {
        if (type == null || 
           !("reject".equals(type) || "flow_to_disk".equals(type) || 
            "ring".equals(type) || "ring_strict".equals(type)))
        {
            throw new IllegalArgumentException("Invalid Queue Policy Type" +
                    " should be one of {reject|flow_to_disk|ring|ring_strict}");
        }
    }
    
	public void setPolicyType(String s)
	{
	    validatePolicyType(s);
	    this.put(QPID_POLICY_TYPE, s);
	}

    public void setMaxCount(Integer i)
    {
        this.put(QPID_MAX_COUNT, i);
    }
    
    public void setMaxSize(Integer i)
    {
        this.put(QPID_MAX_SIZE, i);
    }
    
    public void setPersistLastNode()
    {
        this.put(QPID_PERSIST_LAST_NODE, 1);
    }
    
    public void setOrderingPolicy(String s)
    {
        if (QpidQueueOptions.QPID_LAST_VALUE_QUEUE.equals(s))
        {
            this.put(QPID_LAST_VALUE_QUEUE, 1);
        }
        else if (QpidQueueOptions.QPID_LAST_VALUE_QUEUE_NO_BROWSE.equals(s))
        {
            this.put(QPID_LAST_VALUE_QUEUE_NO_BROWSE,1);
        }
        else
        {
            throw new IllegalArgumentException("Invalid Ordering Policy" +
            " should be one of {" + QpidQueueOptions.QPID_LAST_VALUE_QUEUE + "|" + 
            QPID_LAST_VALUE_QUEUE_NO_BROWSE + "}");
        }
    }
    
    public void setLvqKey(String key)
    {
        this.put(QPID_LVQ_KEY, key);
    }
    
    public void setQueueEvents(String value)
    {
        if (value != null &&  (value.equals("1") || value.equals("2")))
        {
            this.put(QPID_QUEUE_EVENT_GENERATION, value);
        }
        else
        {
            throw new IllegalArgumentException("Invalid value for " + 
                    QPID_QUEUE_EVENT_GENERATION + " should be one of {1|2}");
        }
    }
}
