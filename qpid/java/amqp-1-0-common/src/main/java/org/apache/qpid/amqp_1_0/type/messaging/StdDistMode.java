
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



import org.apache.qpid.amqp_1_0.type.*;

public class StdDistMode
  implements DistributionMode, RestrictedType<Symbol>
  
  {



    private final Symbol _val;

    
    public static final StdDistMode MOVE = new StdDistMode(Symbol.valueOf("move"));
    
    public static final StdDistMode COPY = new StdDistMode(Symbol.valueOf("copy"));
    


    private StdDistMode(Symbol val)
    {
        _val = val;
    }

    public Symbol getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == MOVE)
        {
            return "move";
        }
        
        if(this == COPY)
        {
            return "copy";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static StdDistMode valueOf(Object obj)
    {
        Symbol val = (Symbol) obj;

        if(MOVE._val.equals(val))
        {
            return MOVE;
        }
    
        if(COPY._val.equals(val))
        {
            return COPY;
        }
    
        // TODO ERROR
        return null;
    }



  }
