/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.security.access;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.security.access.plugins.AllowAll;

public class ExchangeDenier extends AllowAll
{

    public static final ACLPluginFactory FACTORY = new ACLPluginFactory()
    {
        public boolean supportsTag(String name)
        {
            return name.startsWith("exchangeDenier");
        }

        public ACLPlugin newInstance(Configuration config)
        {
            return new ExchangeDenier();
        }
    };
    
    @Override
    public AuthzResult authoriseDelete(AMQProtocolSession session, Exchange exchange)
    {
        return AuthzResult.DENIED;
    }

    @Override
    public String getPluginName()
    {
        return getClass().getSimpleName();
    }

    @Override
    public boolean supportsTag(String name)
    {
        return name.equals("exchangeDenier");
    }

}
