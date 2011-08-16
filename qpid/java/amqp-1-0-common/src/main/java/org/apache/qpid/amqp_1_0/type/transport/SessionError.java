
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

public class SessionError
  implements ErrorCondition, RestrictedType<Symbol>
  
  {



    private final Symbol _val;

    
    public static final SessionError WINDOW_VIOLATION = new SessionError(Symbol.valueOf("amqp:session:window-violation"));
    
    public static final SessionError ERRANT_LINK = new SessionError(Symbol.valueOf("amqp:session:errant-link"));
    
    public static final SessionError HANDLE_IN_USE = new SessionError(Symbol.valueOf("amqp:session:handle-in-use"));
    
    public static final SessionError UNATTACHED_HANDLE = new SessionError(Symbol.valueOf("amqp:session:unattached-handle"));
    


    private SessionError(Symbol val)
    {
        _val = val;
    }

    public Symbol getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == WINDOW_VIOLATION)
        {
            return "window-violation";
        }
        
        if(this == ERRANT_LINK)
        {
            return "errant-link";
        }
        
        if(this == HANDLE_IN_USE)
        {
            return "handle-in-use";
        }
        
        if(this == UNATTACHED_HANDLE)
        {
            return "unattached-handle";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static SessionError valueOf(Object obj)
    {
        Symbol val = (Symbol) obj;

        if(WINDOW_VIOLATION._val.equals(val))
        {
            return WINDOW_VIOLATION;
        }
    
        if(ERRANT_LINK._val.equals(val))
        {
            return ERRANT_LINK;
        }
    
        if(HANDLE_IN_USE._val.equals(val))
        {
            return HANDLE_IN_USE;
        }
    
        if(UNATTACHED_HANDLE._val.equals(val))
        {
            return UNATTACHED_HANDLE;
        }
    
        // TODO ERROR
        return null;
    }



  }
