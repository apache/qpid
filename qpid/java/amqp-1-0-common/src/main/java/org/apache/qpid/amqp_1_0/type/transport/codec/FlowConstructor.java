
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
import org.apache.qpid.amqp_1_0.type.transport.*;
import org.apache.qpid.amqp_1_0.type.transport.Flow;


import java.util.List;
import java.util.Map;

public class FlowConstructor extends DescribedTypeConstructor<Flow>
{
    private static final Object[] DESCRIPTORS =
    {
            Symbol.valueOf("amqp:flow:list"),UnsignedLong.valueOf(0x0000000000000013L),
    };

    private static final FlowConstructor INSTANCE = new FlowConstructor();

    public static void register(DescribedTypeConstructorRegistry registry)
    {
        for(Object descriptor : DESCRIPTORS)
        {
            registry.register(descriptor, INSTANCE);
        }
    }

    public Flow construct(Object underlying)
    {
        if(underlying instanceof List)
        {
            List list = (List) underlying;
            Flow obj = new Flow();
            int position = 0;
            final int size = list.size();

            if(position < size)
            {
                Object val = list.get(position);
                position++;

                if(val != null)
                {

                    try
                    {
                        obj.setNextIncomingId( (UnsignedInteger) val );
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
                        obj.setIncomingWindow( (UnsignedInteger) val );
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
                        obj.setNextOutgoingId( (UnsignedInteger) val );
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
                        obj.setOutgoingWindow( (UnsignedInteger) val );
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
                        obj.setHandle( (UnsignedInteger) val );
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
                        obj.setDeliveryCount( (UnsignedInteger) val );
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
                        obj.setLinkCredit( (UnsignedInteger) val );
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
                        obj.setAvailable( (UnsignedInteger) val );
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
                        obj.setDrain( (Boolean) val );
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
                        obj.setEcho( (Boolean) val );
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
                        obj.setProperties( (Map) val );
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


}
