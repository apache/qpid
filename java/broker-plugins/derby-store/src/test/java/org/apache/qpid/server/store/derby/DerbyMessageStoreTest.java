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


import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreTestCase;
import org.apache.qpid.util.FileUtils;

public class DerbyMessageStoreTest extends MessageStoreTestCase
{
    private String _storeLocation;

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            deleteStoreIfExists();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testOnDelete() throws Exception
    {
        File location = new File(_storeLocation);
        assertTrue("Store does not exist at " + _storeLocation, location.exists());

        getStore().closeMessageStore();
        assertTrue("Store does not exist at " + _storeLocation, location.exists());

        getStore().onDelete();
        assertFalse("Store exists at " + _storeLocation, location.exists());
    }

    @Override
    protected Map<String, Object> getStoreSettings() throws Exception
    {
        _storeLocation = TMP_FOLDER + File.separator + getTestName();
        deleteStoreIfExists();
        Map<String, Object> messageStoreSettings = new HashMap<String, Object>();
        messageStoreSettings.put(MessageStore.STORE_PATH, _storeLocation);
        return messageStoreSettings;
    }

    private void deleteStoreIfExists()
    {
        if (_storeLocation != null)
        {
            File location = new File(_storeLocation);
            if (location.exists())
            {
                FileUtils.delete(location, true);
            }
        }
    }

    @Override
    protected MessageStore createMessageStore()
    {
        return new DerbyMessageStore();
    }

}
