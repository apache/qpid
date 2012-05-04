
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

public class SenderSettleMode
  implements RestrictedType<UnsignedByte>
  
  {



    private final UnsignedByte _val;

    
    public static final SenderSettleMode UNSETTLED = new SenderSettleMode(UnsignedByte.valueOf((byte) 0));
    
    public static final SenderSettleMode SETTLED = new SenderSettleMode(UnsignedByte.valueOf((byte) 1));
    
    public static final SenderSettleMode MIXED = new SenderSettleMode(UnsignedByte.valueOf((byte) 2));
    


    private SenderSettleMode(UnsignedByte val)
    {
        _val = val;
    }

    public UnsignedByte getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == UNSETTLED)
        {
            return "unsettled";
        }
        
        if(this == SETTLED)
        {
            return "settled";
        }
        
        if(this == MIXED)
        {
            return "mixed";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static SenderSettleMode valueOf(Object obj)
    {
        UnsignedByte val = (UnsignedByte) obj;

        if(UNSETTLED._val.equals(val))
        {
            return UNSETTLED;
        }
    
        if(SETTLED._val.equals(val))
        {
            return SETTLED;
        }
    
        if(MIXED._val.equals(val))
        {
            return MIXED;
        }
    
        // TODO ERROR
        return null;
    }



  }
