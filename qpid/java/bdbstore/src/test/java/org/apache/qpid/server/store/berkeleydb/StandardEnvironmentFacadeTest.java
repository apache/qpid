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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

public class StandardEnvironmentFacadeTest extends QpidTestCase
{
    protected File _storePath;
    protected EnvironmentFacade _environmentFacade;

    protected void setUp() throws Exception
    {
        super.setUp();
        _storePath = new File(TMP_FOLDER + File.separator + "bdb" + File.separator + getTestName());
    }

    protected void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
            if (_environmentFacade != null)
            {
                _environmentFacade.close();
            }
        }
        finally
        {
            if (_storePath != null)
            {
                FileUtils.delete(_storePath, true);
            }
        }
    }

    public void testEnvironmentFacade() throws Exception
    {
        EnvironmentFacade ef = createEnvironmentFacade();
        assertNotNull("Environment should not be null", ef);
        Environment e = ef.getEnvironment();
        assertTrue("Environment is not valid", e.isValid());
    }

    public void testSecondEnvironmentFacadeUsingSamePathRejected() throws Exception
    {
        EnvironmentFacade ef = createEnvironmentFacade();
        assertNotNull("Environment should not be null", ef);
        try
        {
            createEnvironmentFacade();
            fail("Exception not thrown");
        }
        catch (IllegalArgumentException iae)
        {
            // PASS
        }

        ef.close();

        EnvironmentFacade ef2 = createEnvironmentFacade();
        assertNotNull("Environment should not be null", ef2);
    }

    public void testClose() throws Exception
    {
        EnvironmentFacade ef = createEnvironmentFacade();
        ef.close();
        Environment e = ef.getEnvironment();

        assertNull("Environment should be null after facade close", e);
    }

    public void testOverrideJeParameter() throws Exception
    {
        String statCollectVarName = EnvironmentConfig.STATS_COLLECT;

        EnvironmentFacade ef = createEnvironmentFacade();
        assertEquals("false", ef.getEnvironment().getMutableConfig().getConfigParam(statCollectVarName));
        ef.close();

        ef = createEnvironmentFacade(Collections.singletonMap(statCollectVarName, "true"));
        assertEquals("true", ef.getEnvironment().getMutableConfig().getConfigParam(statCollectVarName));
        ef.close();
    }


    public void testOpenDatabaseReusesCachedHandle() throws Exception
    {
        DatabaseConfig createIfAbsentDbConfig = DatabaseConfig.DEFAULT.setAllowCreate(true);

        EnvironmentFacade ef = createEnvironmentFacade();
        Database handle1 = ef.openDatabase("myDatabase", createIfAbsentDbConfig);
        assertNotNull(handle1);

        Database handle2 = ef.openDatabase("myDatabase", createIfAbsentDbConfig);
        assertSame("Database handle should be cached", handle1, handle2);

        ef.closeDatabase("myDatabase");

        Database handle3 = ef.openDatabase("myDatabase", createIfAbsentDbConfig);
        assertNotSame("Expecting a new handle after database closure", handle1, handle3);
    }

    EnvironmentFacade createEnvironmentFacade()
    {
        _environmentFacade = createEnvironmentFacade(Collections.<String, String>emptyMap());
        return _environmentFacade;

    }

    EnvironmentFacade createEnvironmentFacade(Map<String, String> map)
    {
        StandardEnvironmentConfiguration sec = mock(StandardEnvironmentConfiguration.class);
        when(sec.getName()).thenReturn(getTestName());
        when(sec.getParameters()).thenReturn(map);
        when(sec.getStorePath()).thenReturn(_storePath.getAbsolutePath());

        return new StandardEnvironmentFacade(sec);
    }

}
