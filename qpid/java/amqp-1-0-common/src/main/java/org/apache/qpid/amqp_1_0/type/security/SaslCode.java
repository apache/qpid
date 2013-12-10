
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



import org.apache.qpid.amqp_1_0.type.*;

public class SaslCode
  implements RestrictedType<UnsignedByte>
  
  {



    private final UnsignedByte _val;

    
    public static final SaslCode OK = new SaslCode(UnsignedByte.valueOf((byte) 0));
    
    public static final SaslCode AUTH = new SaslCode(UnsignedByte.valueOf((byte) 1));
    
    public static final SaslCode SYS = new SaslCode(UnsignedByte.valueOf((byte) 2));
    
    public static final SaslCode SYS_PERM = new SaslCode(UnsignedByte.valueOf((byte) 3));
    
    public static final SaslCode SYS_TEMP = new SaslCode(UnsignedByte.valueOf((byte) 4));
    


    private SaslCode(UnsignedByte val)
    {
        _val = val;
    }

    public UnsignedByte getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == OK)
        {
            return "ok";
        }
        
        if(this == AUTH)
        {
            return "auth";
        }
        
        if(this == SYS)
        {
            return "sys";
        }
        
        if(this == SYS_PERM)
        {
            return "sys-perm";
        }
        
        if(this == SYS_TEMP)
        {
            return "sys-temp";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static SaslCode valueOf(Object obj)
    {
        UnsignedByte val = (UnsignedByte) obj;

        if(OK._val.equals(val))
        {
            return OK;
        }
    
        if(AUTH._val.equals(val))
        {
            return AUTH;
        }
    
        if(SYS._val.equals(val))
        {
            return SYS;
        }
    
        if(SYS_PERM._val.equals(val))
        {
            return SYS_PERM;
        }
    
        if(SYS_TEMP._val.equals(val))
        {
            return SYS_TEMP;
        }
    
        // TODO ERROR
        return null;
    }



  }
