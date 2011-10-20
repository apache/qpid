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
package org.apache.qpid.config;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQConnectionURL;
import org.apache.qpid.config.ConnectionFactoryInitialiser;
import org.apache.qpid.config.ConnectorConfig;
import org.apache.qpid.jms.ConnectionURL;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.ConnectionFactory;

class AMQConnectionFactoryInitialiser implements ConnectionFactoryInitialiser
{
    public ConnectionFactory getFactory(ConnectorConfig config)
    {
        try
        {
            final ConnectionURL connectionUrl = new AMQConnectionURL(ConnectionURL.AMQ_PROTOCOL + 
                    "://guest:guest@/test_path?brokerlist='tcp://" + config.getHost() + ":" + config.getPort() + "'");
            return new AMQConnectionFactory(connectionUrl);
        }
        catch (URLSyntaxException e)
        {
            throw new RuntimeException("Problem building URL", e);
        }
    }
}
