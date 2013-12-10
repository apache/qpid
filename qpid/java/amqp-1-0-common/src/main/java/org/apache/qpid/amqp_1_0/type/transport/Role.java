
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

public class Role
  implements RestrictedType<Boolean>
  
  {



    private final boolean _val;

    
    public static final Role SENDER = new Role(false);
    
    public static final Role RECEIVER = new Role(true);
    


    private Role(boolean val)
    {
        _val = val;
    }

    public Boolean getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == SENDER)
        {
            return "sender";
        }
        
        if(this == RECEIVER)
        {
            return "receiver";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static Role valueOf(Object obj)
    {
        boolean val = (Boolean) obj;

        if(SENDER._val == (val))
        {
            return SENDER;
        }
    
        if(RECEIVER._val == (val))
        {
            return RECEIVER;
        }
    
        // TODO ERROR
        return null;
    }



  }
