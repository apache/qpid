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

    private String name;
    private String _filter;
    private FilterType _filterType = FilterType.SUBJECT;
    private boolean _noLocal;
    private boolean _durable;
    private int _consumerCapacity = 0;
    private int _producerCapacity = 0;
    private Reliability _reliability = AT_LEAST_ONCE;

    private Map<String, Object> _xDeclareProps = Collections.emptyMap();
    private List<Object> _xBindingProps = Collections.emptyList();
    private Map<String, Object> _xSubscribeProps = Collections.emptyMap();

    public Reliability getReliability()
    {
        return _reliability;
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public String getFilter()
    {
        return _filter;
    }

    public FilterType getFilterType()
    {
        return _filterType;
    }

    public boolean isNoLocal()
    {
        return _noLocal;
    }

    public int getConsumerCapacity()
    {
        return _consumerCapacity;
    }

    public int getProducerCapacity()
    {
        return _producerCapacity;
    }

    public String getName()
    {
        return name;
    }

    public Map<String, Object> getDeclareProperties()
    {
        return _xDeclareProps;
    }

    public List<Object> getBindingProperties()
    {
        return _xBindingProps;
    }

    public Map<String, Object> getSubscribeProperties()
    {
        return _xSubscribeProps;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setFilter(String filter)
    {
        this._filter = filter;
    }

    public void setFilterType(FilterType filterType)
    {
        this._filterType = filterType;
    }

    public void setNoLocal(boolean noLocal)
    {
        this._noLocal = noLocal;
    }

    public void setDurable(boolean durable)
    {
        this._durable = durable;
    }

    public void setConsumerCapacity(int consumerCapacity)
    {
        this._consumerCapacity = consumerCapacity;
    }

    public void setProducerCapacity(int producerCapacity)
    {
        this._producerCapacity = producerCapacity;
    }

    public void setReliability(Reliability reliability)
    {
        this._reliability = reliability;
    }

    public void setDeclareProps(Map<String, Object> xDeclareProps)
    {
        this._xDeclareProps = xDeclareProps;
    }

    public void setBindingProps(List<Object> xBindingProps)
    {
        this._xBindingProps = xBindingProps;
    }

    public void setSubscribeProps(Map<String, Object> xSubscribeProps)
    {
        this._xSubscribeProps = xSubscribeProps;
    }

}
