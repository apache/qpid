/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.client;

import junit.framework.JUnit4TestAdapter;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * All client unit tests - even one in packages like org.apache.qpid.ack.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        org.apache.qpid.test.unit.ack.UnitTests.class,
        org.apache.qpid.test.unit.basic.UnitTests.class,
        org.apache.qpid.test.unit.client.channelclose.UnitTests.class,
        org.apache.qpid.test.unit.client.message.MessageUnitTests.class,
        org.apache.qpid.test.unit.client.forwardall.UnitTests.class,
        org.apache.qpid.test.unit.client.destinationurl.UnitTests.class,
        org.apache.qpid.test.unit.jndi.referenceabletest.UnitTests.class,
        org.apache.qpid.test.unit.transacted.UnitTests.class,
        org.apache.qpid.test.unit.client.protocol.AMQProtocolSessionTest.class,
        org.apache.qpid.test.unit.client.AMQConnectionTest.class,
        org.apache.qpid.test.unit.client.AMQSessionTest.class
        })
public class AllClientUnitTests
{
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(AllClientUnitTests.class);
    }
}
