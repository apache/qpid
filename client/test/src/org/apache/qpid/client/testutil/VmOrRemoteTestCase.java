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
package org.apache.qpid.client.testutil;

import org.apache.qpid.vmbroker.VmPipeBroker;
import org.junit.After;
import org.junit.Before;

public class VmOrRemoteTestCase
{
    String _connectionString = "vm:1";

    VmPipeBroker _vmBroker;

    public boolean isVm() {
        return "vm:1".equals(_connectionString);
    }

    public void setConnectionString(final String connectionString) {
        this._connectionString = connectionString;
    }

    public String getConnectionString()
    {
        return _connectionString;
    }

    @Before
    public void startVmBroker() throws Exception {
        if (isVm()) {
            _vmBroker = new VmPipeBroker();
            _vmBroker.initialiseBroker();
        }
    }

    @After
    public void stopVmBroker() {
        _vmBroker.killBroker();
    }

}
