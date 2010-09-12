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
package org.apache.qpid.server.logging.actors;

import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.logging.LogMessage;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.UnitTestMessageLogger;

import org.apache.qpid.server.util.InternalBrokerBaseCase;

public class BaseActorTestCase extends InternalBrokerBaseCase
{
    protected LogActor _amqpActor;
    protected UnitTestMessageLogger _rawLogger;
    protected RootMessageLogger _rootLogger;

    @Override
    public void configure()
    {
        getConfiguration().getConfig().setProperty(ServerConfiguration.STATUS_UPDATES, "on");
    }

    @Override
    public void createBroker() throws Exception
    {
        super.createBroker();

        _rawLogger = new UnitTestMessageLogger(getConfiguration());
        _rootLogger = _rawLogger;
    }

    public void tearDown() throws Exception
    {
        _rawLogger.clearLogMessages();

        super.tearDown();
    }

    public String sendTestLogMessage(LogActor actor)
    {
        String message = "Test logging: " + getName();
        sendTestLogMessage(actor, message);
        
        return message;
    }
    
    public void sendTestLogMessage(LogActor actor, final String message)
    {
        actor.message(new LogSubject()
        {
            public String toLogString()
            {
                return message;
            }

        }, new LogMessage()
        {
            public String toString()
            {
                return message;
            }

            public String getLogHierarchy()
            {
                return "test.hierarchy";
            }
        });
    }

}
