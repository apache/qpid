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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Link
{

    private final String _name;
    private final boolean _durable;
    private final int _consumerCapacity;
    private final int _producerCapacity;
    private final Reliability _reliability;

    private final Map<String, Object> _xDeclareProps;
    private final List<Object> _xBindingProps;
    private final Map<String, Object> _xSubscribeProps;

    // TODO - these should all be made final and added to the constructor

    private boolean _noLocal;
    private String _filter;
    private FilterType _filterType;

    public Link(String name,
                boolean durable,
                Reliability reliability,
                int producerCapacity,
                int consumerCapacity,
                Map<String, Object> xDeclareProps,
                List<Object> xBindingProps,
                Map<String, Object> xSubscribeProps)
    {
        _name = name;
        _durable = durable;
        _reliability = reliability;
        _producerCapacity = producerCapacity;
        _consumerCapacity = consumerCapacity;
        _xDeclareProps = xDeclareProps == null
                ? Collections.EMPTY_MAP
                : Collections.unmodifiableMap(new HashMap<String, Object>(xDeclareProps));
        _xBindingProps = xBindingProps == null
                ? Collections.EMPTY_LIST
                : Collections.unmodifiableList(new ArrayList<Object>(xBindingProps));
        _xSubscribeProps = xSubscribeProps == null
                ? Collections.EMPTY_MAP
                : Collections.unmodifiableMap(new HashMap<String, Object>(xSubscribeProps));
    }

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
        return _name;
    }

    public Map<String, Object> getDeclareProperties()
    {
        return Collections.unmodifiableMap(_xDeclareProps);
    }

    public List<Object> getBindingProperties()
    {
        return Collections.unmodifiableList(_xBindingProps);
    }

    public Map<String, Object> getSubscribeProperties()
    {
        return Collections.unmodifiableMap(_xSubscribeProps);
    }

}
