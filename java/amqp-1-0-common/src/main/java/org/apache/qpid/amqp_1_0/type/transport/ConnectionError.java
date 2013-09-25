
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

public class ConnectionError
  implements ErrorCondition, RestrictedType<Symbol>
  
  {



    private final Symbol _val;

    
    public static final ConnectionError CONNECTION_FORCED = new ConnectionError(Symbol.valueOf("amqp:connection:forced"));
    
    public static final ConnectionError FRAMING_ERROR = new ConnectionError(Symbol.valueOf("amqp:connection:framing-error"));
    
    public static final ConnectionError REDIRECT = new ConnectionError(Symbol.valueOf("amqp:connection:redirect"));
    
    public static final ConnectionError SOCKET_ERROR = new ConnectionError(Symbol.valueOf("amqp:connection:socket-error"));
    


    private ConnectionError(Symbol val)
    {
        _val = val;
    }

    public Symbol getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == CONNECTION_FORCED)
        {
            return "connection-forced";
        }
        
        if(this == FRAMING_ERROR)
        {
            return "framing-error";
        }
        
        if(this == REDIRECT)
        {
            return "redirect";
        }
        
        if(this == SOCKET_ERROR)
        {
            return "socket-error";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static ConnectionError valueOf(Object obj)
    {
        Symbol val = (Symbol) obj;

        if(CONNECTION_FORCED._val.equals(val))
        {
            return CONNECTION_FORCED;
        }
    
        if(FRAMING_ERROR._val.equals(val))
        {
            return FRAMING_ERROR;
        }
    
        if(REDIRECT._val.equals(val))
        {
            return REDIRECT;
        }
        
        if(SOCKET_ERROR._val.equals(val))
        {
            return SOCKET_ERROR;
        }
    
        // TODO ERROR
        return null;
    }



  }
