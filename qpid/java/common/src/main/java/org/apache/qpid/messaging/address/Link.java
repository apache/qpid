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

import static org.apache.qpid.messaging.address.Link.Reliability.AT_LEAST_ONCE;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Link
{
    public enum FilterType
    {
        SQL92, XQUERY, SUBJECT
    }

    public enum Reliability
    {
        UNRELIABLE, AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE;

        public static Reliability getReliability(String reliability)
                throws AddressException
        {
            if (reliability == null)
            {
                return AT_LEAST_ONCE;
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
                throw new AddressException("The reliability mode '"
                        + reliability + "' is not yet supported");
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
    protected Reliability reliability = AT_LEAST_ONCE;

    protected Map<String, Object> xDeclareProps = Collections.emptyMap();
    protected List<Object> xBindingProps = Collections.emptyList();
    protected Map<String, Object> xSubscribeProps = Collections.emptyMap();

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

    public Map<String, Object> getDeclareProperties()
    {
        return xDeclareProps;
    }

    public List<Object> getBindingProperties()
    {
        return xBindingProps;
    }

    public Map<String, Object> getSubscribeProperties()
    {
        return xSubscribeProps;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setFilter(String filter)
    {
        this.filter = filter;
    }

    public void setFilterType(FilterType filterType)
    {
        this.filterType = filterType;
    }

    public void setNoLocal(boolean noLocal)
    {
        this.noLocal = noLocal;
    }

    public void setDurable(boolean durable)
    {
        this.durable = durable;
    }

    public void setConsumerCapacity(int consumerCapacity)
    {
        this.consumerCapacity = consumerCapacity;
    }

    public void setProducerCapacity(int producerCapacity)
    {
        this.producerCapacity = producerCapacity;
    }

    public void setReliability(Reliability reliability)
    {
        this.reliability = reliability;
    }

    public void setDeclareProps(Map<String, Object> xDeclareProps)
    {
        this.xDeclareProps = xDeclareProps;
    }

    public void setBindingProps(List<Object> xBindingProps)
    {
        this.xBindingProps = xBindingProps;
    }

    public void setSubscribeProps(Map<String, Object> xSubscribeProps)
    {
        this.xSubscribeProps = xSubscribeProps;
    }

}
