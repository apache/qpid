/*
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
 */

package org.apache.qpid.server.store.berkeleydb;

import java.io.File;

import junit.framework.TestCase;

import org.apache.qpid.test.utils.QpidTestCase;

public class EnvHomeRegistryTest extends TestCase
{

    private final EnvHomeRegistry _ehr = new EnvHomeRegistry();

    public void testDuplicateEnvHomeRejected() throws Exception
    {
        File home = new File(QpidTestCase.TMP_FOLDER, getName());

        _ehr.registerHome(home);
        try
        {
            _ehr.registerHome(home);
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }
    }

    public void testUniqueEnvHomesAllowed() throws Exception
    {
        File home1 = new File(QpidTestCase.TMP_FOLDER, getName() + "1");
        File home2 = new File(QpidTestCase.TMP_FOLDER, getName() + "2");

        _ehr.registerHome(home1);
        _ehr.registerHome(home2);
    }

    public void testReuseOfEnvHomesAllowed() throws Exception
    {
        File home = new File(QpidTestCase.TMP_FOLDER, getName() + "1");

        _ehr.registerHome(home);

        _ehr.deregisterHome(home);

        _ehr.registerHome(home);
    }
}