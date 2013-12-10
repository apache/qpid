
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
import org.apache.qpid.amqp_1_0.type.transport.Open;


import java.util.List;
import java.util.Map;

public class OpenConstructor extends DescribedTypeConstructor<Open>
{
    private static final Object[] DESCRIPTORS =
    {
            Symbol.valueOf("amqp:open:list"),UnsignedLong.valueOf(0x0000000000000010L),
    };

    private static final OpenConstructor INSTANCE = new OpenConstructor();

    public static void register(DescribedTypeConstructorRegistry registry)
    {
        for(Object descriptor : DESCRIPTORS)
        {
            registry.register(descriptor, INSTANCE);
        }
    }

    public Open construct(Object underlying)
    {
        if(underlying instanceof List)
        {
            List list = (List) underlying;
            Open obj = new Open();
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
                        obj.setContainerId( (String) val );
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
                        obj.setHostname( (String) val );
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
                        obj.setMaxFrameSize( (UnsignedInteger) val );
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
                        obj.setChannelMax( (UnsignedShort) val );
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
                        obj.setIdleTimeOut( (UnsignedInteger) val );
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


                    if (val instanceof Symbol[] )
                    {
                        obj.setOutgoingLocales( (Symbol[]) val );
                    }
                    else
		            {
                        try
                        {
                            obj.setOutgoingLocales( new Symbol[] { (Symbol) val } );
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

            if(position < size)
            {
                Object val = list.get(position);
                position++;

                if(val != null)
                {


                    if (val instanceof Symbol[] )
                    {
                        obj.setIncomingLocales( (Symbol[]) val );
                    }
                    else
		            {
                        try
                        {
                            obj.setIncomingLocales( new Symbol[] { (Symbol) val } );
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

            if(position < size)
            {
                Object val = list.get(position);
                position++;

                if(val != null)
                {


                    if (val instanceof Symbol[] )
                    {
                        obj.setOfferedCapabilities( (Symbol[]) val );
                    }
                    else
		            {
                        try
                        {
                            obj.setOfferedCapabilities( new Symbol[] { (Symbol) val } );
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

            if(position < size)
            {
                Object val = list.get(position);
                position++;

                if(val != null)
                {


                    if (val instanceof Symbol[] )
                    {
                        obj.setDesiredCapabilities( (Symbol[]) val );
                    }
                    else
		            {
                        try
                        {
                            obj.setDesiredCapabilities( new Symbol[] { (Symbol) val } );
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
