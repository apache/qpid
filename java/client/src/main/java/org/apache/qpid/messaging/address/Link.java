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

import static org.apache.qpid.messaging.address.Link.Reliability.UNSPECIFIED;

public class Link 
{
    public enum FilterType { SQL92, XQUERY, SUBJECT }
        
    public enum Reliability 
    { 
        UNRELIABLE, AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE, UNSPECIFIED; 
        
        public static Reliability getReliability(String reliability) throws AddressException
        {
            if (reliability == null)
            {
                return UNSPECIFIED;
            }
            else if (reliability.equalsIgnoreCase("unreliable"))
            {
                return UNRELIABLE;
            }
            else if (reliability.equalsIgnoreCase("at-least-once"))
            {
                return AT_LEAST_ONCE;
            }
            else
            {
                throw new AddressException("The reliability mode '" + 
                        reliability + "' is not yet supported");
            }
        }
    }
    
    protected String name;
    protected String filter;
    protected FilterType filterType = FilterType.SUBJECT;
    protected boolean noLocal;
    protected boolean durable;
    protected int consumerCapacity = 0;
    protected int producerCapacity = 0;
    protected Reliability reliability = UNSPECIFIED;
    
    public Link(AddressHelper helper) throws AddressException
    {
        name = helper.getLinkName();
        durable = helper.isLinkDurable();
        reliability = Reliability.getReliability(helper.getLinkReliability());
        consumerCapacity = helper.getLinkConsumerCapacity();
        producerCapacity = helper.getLinkProducerCapacity();
    }
    
    public Reliability getReliability()
    {
        return reliability;
    }

    public boolean isDurable()
    {
        return durable;
    }
     
    public String getFilter()
    {
        return filter;
    }

    public FilterType getFilterType()
    {
        return filterType;
    }

    public boolean isNoLocal()
    {
        return noLocal;
    }

    public int getConsumerCapacity()
    {
        return consumerCapacity;
    }
   
    public int getProducerCapacity()
    {
        return producerCapacity;
    }
    
    public String getName()
    {
        return name;
    }
}
