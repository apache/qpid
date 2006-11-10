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
package org.apache.qpid.server;

import junit.framework.JUnit4TestAdapter;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        org.apache.qpid.server.configuration.UnitTests.class,
        org.apache.qpid.server.exchange.UnitTests.class,
        org.apache.qpid.server.protocol.UnitTests.class,
        org.apache.qpid.server.queue.UnitTests.class,
        org.apache.qpid.server.store.UnitTests.class,
        org.apache.qpid.server.txn.UnitTests.class,
        org.apache.qpid.server.util.UnitTests.class
        })
public class UnitTests
{
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(UnitTests.class);
    }
}
