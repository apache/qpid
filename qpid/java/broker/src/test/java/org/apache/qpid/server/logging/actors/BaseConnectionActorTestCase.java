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

import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.util.BrokerTestHelper;

public class BaseConnectionActorTestCase extends BaseActorTestCase
{
    private AMQProtocolSession _session;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _session = BrokerTestHelper.createSession();

        setAmqpActor(new AMQPConnectionActor(_session, getRootLogger()));
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_session != null)
            {
                _session.getVirtualHost().close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    public AMQProtocolSession getSession()
    {
        return _session;
    }

}
