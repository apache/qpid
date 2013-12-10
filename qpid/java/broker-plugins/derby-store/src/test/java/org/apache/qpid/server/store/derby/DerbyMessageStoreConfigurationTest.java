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
package org.apache.qpid.server.store.derby;

import org.apache.qpid.server.store.AbstractDurableConfigurationStoreTestCase;

public class DerbyMessageStoreConfigurationTest extends AbstractDurableConfigurationStoreTestCase
{

    private DerbyMessageStore _derbyMessageStore;

    @Override
    protected DerbyMessageStore createMessageStore() throws Exception
    {
        createStoreIfNecessary();
        return _derbyMessageStore;
    }

    @Override
    protected void closeMessageStore() throws Exception
    {
        closeStoreIfNecessary();
    }

    private void createStoreIfNecessary()
    {
        if(_derbyMessageStore == null)
        {
            _derbyMessageStore = new DerbyMessageStore();
        }
    }

    @Override
    protected DerbyMessageStore createConfigStore() throws Exception
    {
        createStoreIfNecessary();
        return _derbyMessageStore;
    }

    @Override
    protected void closeConfigStore() throws Exception
    {
        closeStoreIfNecessary();
    }

    private void closeStoreIfNecessary() throws Exception
    {
        if (_derbyMessageStore != null)
        {
            _derbyMessageStore.close();
            _derbyMessageStore = null;
        }
    }
}
