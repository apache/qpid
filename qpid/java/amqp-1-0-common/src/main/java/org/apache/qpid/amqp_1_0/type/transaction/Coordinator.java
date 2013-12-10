
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


package org.apache.qpid.amqp_1_0.type.transaction;



import java.util.Arrays;


import org.apache.qpid.amqp_1_0.type.*;

public class Coordinator
  implements Target
  {


    private TxnCapability[] _capabilities;

    public TxnCapability[] getCapabilities()
    {
        return _capabilities;
    }

    public void setCapabilities(TxnCapability[] capabilities)
    {
        _capabilities = capabilities;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder("Coordinator{");
        final int origLength = builder.length();

        if(_capabilities != null)
        {
            if(builder.length() != origLength)
            {
                builder.append(',');
            }
            builder.append("capabilities=").append(Arrays.asList(_capabilities));
        }

        builder.append('}');
        return builder.toString();
    }


  }
