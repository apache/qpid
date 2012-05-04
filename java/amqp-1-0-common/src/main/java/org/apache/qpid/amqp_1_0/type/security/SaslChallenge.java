
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

public class SaslChallenge
  implements SaslFrameBody
  {


    private Binary _challenge;

    public Binary getChallenge()
    {
        return _challenge;
    }

    public void setChallenge(Binary challenge)
    {
        _challenge = challenge;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("SaslChallenge{");
        final int origLength = builder.length();

        if(_challenge != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("challenge=").append(_challenge);
        }

        builder.append('}');
        return builder.toString();
    }

    public void invoke(SASLEndpoint conn)
    {
        conn.receiveSaslChallenge(this);
    }


  }
