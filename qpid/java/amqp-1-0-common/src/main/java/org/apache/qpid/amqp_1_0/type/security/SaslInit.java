
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


package org.apache.qpid.amqp_1_0.type.security;


import org.apache.qpid.amqp_1_0.transport.SASLEndpoint;


import org.apache.qpid.amqp_1_0.type.*;

public class SaslInit
  implements SaslFrameBody
  {


    private Symbol _mechanism;

    private Binary _initialResponse;

    private String _hostname;

    public Symbol getMechanism()
    {
        return _mechanism;
    }

    public void setMechanism(Symbol mechanism)
    {
        _mechanism = mechanism;
    }

    public Binary getInitialResponse()
    {
        return _initialResponse;
    }

    public void setInitialResponse(Binary initialResponse)
    {
        _initialResponse = initialResponse;
    }

    public String getHostname()
    {
        return _hostname;
    }

    public void setHostname(String hostname)
    {
        _hostname = hostname;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("SaslInit{");
        final int origLength = builder.length();

        if(_mechanism != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("mechanism=").append(_mechanism);
        }

        if(_initialResponse != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("initialResponse=").append(_initialResponse);
        }

        if(_hostname != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("hostname=").append(_hostname);
        }

        builder.append('}');
        return builder.toString();
    }

    public void invoke(SASLEndpoint conn)
    {
        conn.receiveSaslInit(this);
    }


  }
