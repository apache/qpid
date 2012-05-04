
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
import org.apache.qpid.amqp_1_0.type.TxnCapability;

public class TxnCapabilities
  implements TxnCapability, RestrictedType<Symbol>
  
  {



    private final Symbol _val;

    
    public static final TxnCapabilities LOCAL_TXN = new TxnCapabilities(Symbol.valueOf("amqp:local-transactions"));
    
    public static final TxnCapabilities DISTRIBUTED_TXN = new TxnCapabilities(Symbol.valueOf("amqp:distributed-transactions"));
    
    public static final TxnCapabilities PROMOTABLE_TXN = new TxnCapabilities(Symbol.valueOf("amqp:promotable-transactions"));
    
    public static final TxnCapabilities MULTI_TXNS_PER_SSN = new TxnCapabilities(Symbol.valueOf("amqp:multi-txns-per-ssn"));
    
    public static final TxnCapabilities MULTI_SSNS_PER_TXN = new TxnCapabilities(Symbol.valueOf("amqp:multi-ssns-per-txn"));
    
    public static final TxnCapabilities MULTI_CONNS_PER_TXN = new TxnCapabilities(Symbol.valueOf("amqp:multi-conns-per-txn"));
    


    private TxnCapabilities(Symbol val)
    {
        _val = val;
    }

    public Symbol getValue()
    {
        return _val;
    }

    public String toString()
    {
        
        if(this == LOCAL_TXN)
        {
            return "local-txn";
        }
        
        if(this == DISTRIBUTED_TXN)
        {
            return "distributed-txn";
        }
        
        if(this == PROMOTABLE_TXN)
        {
            return "promotable-txn";
        }
        
        if(this == MULTI_TXNS_PER_SSN)
        {
            return "multi-txns-per-ssn";
        }
        
        if(this == MULTI_SSNS_PER_TXN)
        {
            return "multi-ssns-per-txn";
        }
        
        if(this == MULTI_CONNS_PER_TXN)
        {
            return "multi-conns-per-txn";
        }
        
        else
        {
            return String.valueOf(_val);
        }
    }

    public static TxnCapabilities valueOf(Object obj)
    {
        Symbol val = (Symbol) obj;

        if(LOCAL_TXN._val.equals(val))
        {
            return LOCAL_TXN;
        }
    
        if(DISTRIBUTED_TXN._val.equals(val))
        {
            return DISTRIBUTED_TXN;
        }
    
        if(PROMOTABLE_TXN._val.equals(val))
        {
            return PROMOTABLE_TXN;
        }
    
        if(MULTI_TXNS_PER_SSN._val.equals(val))
        {
            return MULTI_TXNS_PER_SSN;
        }
    
        if(MULTI_SSNS_PER_TXN._val.equals(val))
        {
            return MULTI_SSNS_PER_TXN;
        }
    
        if(MULTI_CONNS_PER_TXN._val.equals(val))
        {
            return MULTI_CONNS_PER_TXN;
        }
    
        // TODO ERROR
        return null;
    }



  }
