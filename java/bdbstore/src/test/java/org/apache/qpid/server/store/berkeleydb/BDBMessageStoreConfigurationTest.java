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
package org.apache.qpid.server.store.berkeleydb;

import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.DurableConfigurationStoreTest;
import org.apache.qpid.server.store.MessageStore;

public class BDBMessageStoreConfigurationTest extends DurableConfigurationStoreTest
{

    private BDBMessageStore _bdbMessageStore;

    @Override
    protected BDBMessageStore createMessageStore() throws Exception
    {
        _bdbMessageStore = new BDBMessageStore();
        return _bdbMessageStore;
    }

    // TODO - this only works so long as createConfigStore is called after createMessageStore
    @Override
    protected DurableConfigurationStore createConfigStore() throws Exception
    {
        return _bdbMessageStore;
    }
}
