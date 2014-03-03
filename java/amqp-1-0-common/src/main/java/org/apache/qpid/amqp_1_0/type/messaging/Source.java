
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


package org.apache.qpid.amqp_1_0.type.messaging;



import java.util.Arrays;
import java.util.Map;


import org.apache.qpid.amqp_1_0.type.*;

public class Source
  implements org.apache.qpid.amqp_1_0.type.Source
  {


    private String _address;

    private TerminusDurability _durable;

    private TerminusExpiryPolicy _expiryPolicy;

    private UnsignedInteger _timeout;

    private Boolean _dynamic;

    private Map _dynamicNodeProperties;

    private DistributionMode _distributionMode;

    private Map _filter;

    private Outcome _defaultOutcome;

    private Symbol[] _outcomes;

    private Symbol[] _capabilities;

    public String getAddress()
    {
        return _address;
    }

    public void setAddress(String address)
    {
        _address = address;
    }

    public TerminusDurability getDurable()
    {
        return _durable;
    }

    public void setDurable(TerminusDurability durable)
    {
        _durable = durable;
    }

    public TerminusExpiryPolicy getExpiryPolicy()
    {
        return _expiryPolicy;
    }

    public void setExpiryPolicy(TerminusExpiryPolicy expiryPolicy)
    {
        _expiryPolicy = expiryPolicy;
    }

    public UnsignedInteger getTimeout()
    {
        return _timeout;
    }

    public void setTimeout(UnsignedInteger timeout)
    {
        _timeout = timeout;
    }

    public Boolean getDynamic()
    {
        return _dynamic;
    }

    public void setDynamic(Boolean dynamic)
    {
        _dynamic = dynamic;
    }

    public Map getDynamicNodeProperties()
    {
        return _dynamicNodeProperties;
    }

    public void setDynamicNodeProperties(Map dynamicNodeProperties)
    {
        _dynamicNodeProperties = dynamicNodeProperties;
    }

    public DistributionMode getDistributionMode()
    {
        return _distributionMode;
    }

    public void setDistributionMode(DistributionMode distributionMode)
    {
        _distributionMode = distributionMode;
    }

    public Map getFilter()
    {
        return _filter;
    }

    public void setFilter(Map filter)
    {
        _filter = filter;
    }

    public Outcome getDefaultOutcome()
    {
        return _defaultOutcome;
    }

    public void setDefaultOutcome(Outcome defaultOutcome)
    {
        _defaultOutcome = defaultOutcome;
    }

    public Symbol[] getOutcomes()
    {
        return _outcomes;
    }

    public void setOutcomes(Symbol... outcomes)
    {
        _outcomes = outcomes;
    }

    public Symbol[] getCapabilities()
    {
        return _capabilities;
    }

    public void setCapabilities(Symbol[] capabilities)
    {
        _capabilities = capabilities;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("Source{");
        final int origLength = builder.length();

        if(_address != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("address=").append(_address);
        }

        if(_durable != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("durable=").append(_durable);
        }

        if(_expiryPolicy != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("expiryPolicy=").append(_expiryPolicy);
        }

        if(_timeout != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("timeout=").append(_timeout);
        }

        if(_dynamic != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("dynamic=").append(_dynamic);
        }

        if(_dynamicNodeProperties != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("dynamicNodeProperties=").append(_dynamicNodeProperties);
        }

        if(_distributionMode != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("distributionMode=").append(_distributionMode);
        }

        if(_filter != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("filter=").append(_filter);
        }

        if(_defaultOutcome != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("defaultOutcome=").append(_defaultOutcome);
        }

        if(_outcomes != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("outcomes=").append(Arrays.toString(_outcomes));
        }

        if(_capabilities != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("capabilities=").append(Arrays.toString(_capabilities));
        }

        builder.append('}');
        return builder.toString();
    }


  }
