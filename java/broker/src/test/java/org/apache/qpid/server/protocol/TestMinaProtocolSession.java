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
package org.apache.qpid.server.protocol;

import org.apache.qpid.AMQException;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.output.ProtocolOutputConverter;
import org.apache.qpid.server.output.ProtocolOutputConverterRegistry;

public class TestMinaProtocolSession extends AMQMinaProtocolSession
{
    public TestMinaProtocolSession() throws AMQException
    {

        super(new TestIoSession(),
              ApplicationRegistry.getInstance().getVirtualHostRegistry(),
              new AMQCodecFactory(true));

    }

    public ProtocolOutputConverter getProtocolOutputConverter()
    {
        return ProtocolOutputConverterRegistry.getConverter(this);
    }

    public byte getProtocolMajorVersion()
    {
        return (byte)8;
    }

    public byte getProtocolMinorVersion()
    {
        return (byte)0;
    }
}
