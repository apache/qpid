
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


package org.apache.qpid.amqp_1_0.type.transport.codec;

import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructor;
import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructorRegistry;
import org.apache.qpid.amqp_1_0.type.*;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionErrors;
import org.apache.qpid.amqp_1_0.type.transport.*;


import java.util.List;
import java.util.Map;

public class ErrorConstructor extends DescribedTypeConstructor<org.apache.qpid.amqp_1_0.type.transport.Error>
{
    private static final Object[] DESCRIPTORS =
    {
            Symbol.valueOf("amqp:error:list"),UnsignedLong.valueOf(0x000000000000001dL),
    };

    private static final ErrorConstructor INSTANCE = new ErrorConstructor();

    public static void register(DescribedTypeConstructorRegistry registry)
    {
        for(Object descriptor : DESCRIPTORS)
        {
            registry.register(descriptor, INSTANCE);
        }
    }

    public org.apache.qpid.amqp_1_0.type.transport.Error construct(Object underlying)
    {
        if(underlying instanceof List)
        {
            List list = (List) underlying;
            org.apache.qpid.amqp_1_0.type.transport.Error obj = new org.apache.qpid.amqp_1_0.type.transport.Error();
            int position = 0;
            final int size = list.size();

            if(position < size)
            {
                Object val = list.get(position);
                position++;

                if(val != null)
                {
                    if(val instanceof ErrorCondition)
                    {
                        obj.setCondition( (ErrorCondition) val );
                    }
                    else if(val instanceof Symbol)
                    {
                        ErrorCondition condition = null;
                        condition = AmqpError.valueOf(val);
                        if(condition == null)
                        {
                            condition = ConnectionError.valueOf(val);
                            if(condition == null)
                            {
                                condition = SessionError.valueOf(val);
                                if(condition == null)
                                {
                                    condition = LinkError.valueOf(val);
                                    if(condition == null)
                                    {
                                        condition = TransactionErrors.valueOf(val);
                                        if(condition == null)
                                        {
                                            condition = new UnknownErrorCondition((Symbol)val);
                                        }
                                    }
                                }
                            }
                        }
                        obj.setCondition(condition);
                    }
                }


            }
            else
            {
                return obj;
            }

            if(position < size)
            {
                Object val = list.get(position);
                position++;

                if(val != null)
                {

                    try
                    {
                        obj.setDescription( (String) val );
                    }
                    catch(ClassCastException e)
                    {

                        // TODO Error
                    }

                }


            }
            else
            {
                return obj;
            }

            if(position < size)
            {
                Object val = list.get(position);
                position++;

                if(val != null)
                {

                    try
                    {
                        obj.setInfo( (Map) val );
                    }
                    catch(ClassCastException e)
                    {

                        // TODO Error
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


    private static final class UnknownErrorCondition implements ErrorCondition
    {
        private final Symbol _value;

        public UnknownErrorCondition(final Symbol value)
        {
            _value = value;
        }

        public Symbol getValue()
        {
            return _value;
        }

        @Override
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            final UnknownErrorCondition that = (UnknownErrorCondition) o;

            if (!_value.equals(that._value))
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return _value.hashCode();
        }

        @Override
        public String toString()
        {
            return _value.toString();
        }
    }
}
