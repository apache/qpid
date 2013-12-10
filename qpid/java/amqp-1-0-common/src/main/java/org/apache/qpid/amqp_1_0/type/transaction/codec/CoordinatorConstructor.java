
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


package org.apache.qpid.amqp_1_0.type.transaction.codec;

import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructor;
import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructorRegistry;
import org.apache.qpid.amqp_1_0.type.*;
import org.apache.qpid.amqp_1_0.type.transaction.*;
import org.apache.qpid.amqp_1_0.type.transaction.Coordinator;
import org.apache.qpid.amqp_1_0.type.transaction.TxnCapability;

import java.util.List;

public class CoordinatorConstructor extends DescribedTypeConstructor<Coordinator>
{
    private static final Object[] DESCRIPTORS =
    {
            Symbol.valueOf("amqp:coordinator:list"),UnsignedLong.valueOf(0x0000000000000030L),
    };

    private static final CoordinatorConstructor INSTANCE = new CoordinatorConstructor();

    public static void register(DescribedTypeConstructorRegistry registry)
    {
        for(Object descriptor : DESCRIPTORS)
        {
            registry.register(descriptor, INSTANCE);
        }
    }

    public Coordinator construct(Object underlying)
    {
        if(underlying instanceof List)
        {
            List list = (List) underlying;
            Coordinator obj = new Coordinator();
            int position = 0;
            final int size = list.size();

            if(position < size)
            {
                Object val = list.get(position);
                position++;

                if(val != null)
                {


                    if (val instanceof TxnCapability[] )
                    {
                        obj.setCapabilities( (TxnCapability[]) val );
                    }
                    else if (val instanceof Symbol[])
                    {
                        Symbol[] symVal = (Symbol[]) val;
                        TxnCapability[] cap = new TxnCapability[symVal.length];
                        int i = 0;
                        for(Symbol sym : symVal)
                        {
                            cap[i++] = TxnCapability.valueOf(sym);
                        }
                        obj.setCapabilities(cap);
                    }
                    else
		            {
                        try
                        {
                            obj.setCapabilities( new TxnCapability[] { (TxnCapability) val } );
                        }
                        catch(ClassCastException e)
                        {
                            // TODO Error
                        }
                    }
                    
                }


            }
            else
            {
                return obj;
            }


            return obj;
        }
        else
        {
            // TODO - error
            return null;
        }
    }


}
