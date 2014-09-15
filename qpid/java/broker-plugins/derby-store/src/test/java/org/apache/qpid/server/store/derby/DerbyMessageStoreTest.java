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

import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreTestCase;
import org.apache.qpid.server.virtualhost.derby.DerbyVirtualHost;
import org.apache.qpid.util.FileUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        DerbyVirtualHost mockVH = mock(DerbyVirtualHost.class);
        when(mockVH.getStorePath()).thenReturn(_storeLocation);

        getStore().onDelete(mockVH);
        assertFalse("Store exists at " + _storeLocation, location.exists());
    }

    @Override
    protected VirtualHost createVirtualHost()
    {
        _storeLocation = TMP_FOLDER + File.separator + getTestName();
        deleteStoreIfExists();

        final DerbyVirtualHost parent = mock(DerbyVirtualHost.class);
        when(parent.getStorePath()).thenReturn(_storeLocation);
        return parent;
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
