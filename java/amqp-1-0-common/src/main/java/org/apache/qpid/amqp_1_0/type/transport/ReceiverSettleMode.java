
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


package org.apache.qpid.amqp_1_0.type.transport;



import org.apache.qpid.amqp_1_0.type.*;

public class ReceiverSettleMode
  implements RestrictedType<UnsignedByte>
  
  {



    private final UnsignedByte _val;

    
    public static final ReceiverSettleMode FIRST = new ReceiverSettleMode(UnsignedByte.valueOf((byte) 0));
    
    public static final ReceiverSettleMode SECOND = new ReceiverSettleMode(UnsignedByte.valueOf((byte) 1));
    


    private ReceiverSettleMode(UnsignedByte val)
    {
        _val = val;
    }

    public UnsignedByte getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == FIRST)
        {
            return "first";
        }
        
        if(this == SECOND)
        {
            return "second";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static ReceiverSettleMode valueOf(Object obj)
    {
        UnsignedByte val = (UnsignedByte) obj;

        if(FIRST._val.equals(val))
        {
            return FIRST;
        }
    
        if(SECOND._val.equals(val))
        {
            return SECOND;
        }
    
        // TODO ERROR
        return null;
    }



  }
