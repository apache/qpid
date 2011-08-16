
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



import org.apache.qpid.amqp_1_0.type.*;

public class TransactionErrors
  implements ErrorCondition, RestrictedType<Symbol>
  
  {



    private final Symbol _val;

    
    public static final TransactionErrors UNKNOWN_ID = new TransactionErrors(Symbol.valueOf("amqp:transaction:unknown-id"));
    
    public static final TransactionErrors TRANSACTION_ROLLBACK = new TransactionErrors(Symbol.valueOf("amqp:transaction:rollback"));
    
    public static final TransactionErrors TRANSACTION_TIMEOUT = new TransactionErrors(Symbol.valueOf("amqp:transaction:timeout"));
    


    private TransactionErrors(Symbol val)
    {
        _val = val;
    }

    public Symbol getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == UNKNOWN_ID)
        {
            return "unknown-id";
        }
        
        if(this == TRANSACTION_ROLLBACK)
        {
            return "transaction-rollback";
        }
        
        if(this == TRANSACTION_TIMEOUT)
        {
            return "transaction-timeout";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static TransactionErrors valueOf(Object obj)
    {
        Symbol val = (Symbol) obj;

        if(UNKNOWN_ID._val.equals(val))
        {
            return UNKNOWN_ID;
        }
    
        if(TRANSACTION_ROLLBACK._val.equals(val))
        {
            return TRANSACTION_ROLLBACK;
        }
    
        if(TRANSACTION_TIMEOUT._val.equals(val))
        {
            return TRANSACTION_TIMEOUT;
        }
    
        // TODO ERROR
        return null;
    }



  }
