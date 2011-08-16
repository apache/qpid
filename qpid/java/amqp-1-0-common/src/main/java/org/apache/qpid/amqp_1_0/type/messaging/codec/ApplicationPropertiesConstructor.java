
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


package org.apache.qpid.amqp_1_0.type.messaging.codec;

import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructor;
import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructorRegistry;
import org.apache.qpid.amqp_1_0.type.*;
import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;


import java.util.Map;

public class ApplicationPropertiesConstructor extends DescribedTypeConstructor<ApplicationProperties>
{
    private static final Object[] DESCRIPTORS =
    {
            Symbol.valueOf("amqp:application-properties:map"),UnsignedLong.valueOf(0x0000000000000074L),
    };

    private static final ApplicationPropertiesConstructor INSTANCE = new ApplicationPropertiesConstructor();

    public static void register(DescribedTypeConstructorRegistry registry)
    {
        for(Object descriptor : DESCRIPTORS)
        {
            registry.register(descriptor, INSTANCE);
        }
    }


    public ApplicationProperties construct(Object underlying)
    {

        if(underlying instanceof Map)
        {
            return new ApplicationProperties((Map)underlying);
        }
        else
        {
            // TODO - error
            return null;
        }
    }


}
