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
package org.apache.qpid.server.store.berkeleydb.upgrade;

import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.NONEXCLUSIVE_WITH_ERRONEOUS_OWNER;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.NON_DURABLE_QUEUE_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.PRIORITY_QUEUE_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.QUEUE_NAME;
import static org.apache.qpid.server.store.berkeleydb.BDBStoreUpgradeTestPreparer.QUEUE_WITH_DLQ_NAME;

import java.io.File;
import java.io.InputStream;

import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.subjects.TestBlankSubject;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;

public abstract class AbstractUpgradeTestCase extends QpidTestCase
{
    protected static final class StaticAnswerHandler implements UpgradeInteractionHandler
    {
        private UpgradeInteractionResponse _response;

        public StaticAnswerHandler(UpgradeInteractionResponse response)
        {
            _response = response;
        }

        @Override
        public UpgradeInteractionResponse requireResponse(String question, UpgradeInteractionResponse defaultResponse,
                UpgradeInteractionResponse... possibleResponses)
        {
            return _response;
        }
    }

    public static final String[] QUEUE_NAMES = { "clientid:myDurSubName", "clientid:mySelectorDurSubName", QUEUE_NAME, NON_DURABLE_QUEUE_NAME,
            NONEXCLUSIVE_WITH_ERRONEOUS_OWNER, PRIORITY_QUEUE_NAME, QUEUE_WITH_DLQ_NAME, QUEUE_WITH_DLQ_NAME + "_DLQ" };
    public static int[] QUEUE_SIZES = { 1, 1, 10, 3, 0, 0, 0, 1};
    public static int TOTAL_MESSAGE_NUMBER = 16;
    protected static final LogSubject LOG_SUBJECT = new TestBlankSubject();

    // myQueueWithDLQ_DLQ is not bound to the default exchange
    protected static final int TOTAL_BINDINGS = QUEUE_NAMES.length * 2 - 1;
    protected static final int TOTAL_EXCHANGES = 6;

    private File _storeLocation;
    protected Environment _environment;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _storeLocation = copyStore(getStoreDirectoryName());

        _environment = createEnvironment(_storeLocation);
    }

    /** @return eg "bdbstore-v4" - used for copying store */
    protected abstract String getStoreDirectoryName();

    protected Environment createEnvironment(File storeLocation)
    {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setConfigParam("je.lock.nLockTables", "7");
        envConfig.setReadOnly(false);
        envConfig.setSharedCache(false);
        envConfig.setCacheSize(0);
        return new Environment(storeLocation, envConfig);
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            _environment.close();
        }
        finally
        {
            _environment = null;
            deleteDirectoryIfExists(_storeLocation);
        }
        super.tearDown();
    }

    private File copyStore(String storeDirectoryName) throws Exception
    {
        File storeLocation = new File(new File(TMP_FOLDER), "test-store");
        deleteDirectoryIfExists(storeLocation);
        storeLocation.mkdirs();
        int index = 0;
        String prefix = "0000000";
        String extension = ".jdb";
        InputStream is = null;
        do
        {
            String fileName = prefix + index + extension;
            is = getClass().getClassLoader().getResourceAsStream("upgrade/" + storeDirectoryName + "/test-store/" + fileName);
            if (is != null)
            {
                FileUtils.copy(is, new File(storeLocation, fileName));
            }
            index++;
        }
        while (is != null);
        return storeLocation;
    }

    protected void deleteDirectoryIfExists(File dir)
    {
        if (dir.exists())
        {
            assertTrue("The provided file " + dir + " is not a directory", dir.isDirectory());

            boolean deletedSuccessfully = FileUtils.delete(dir, true);

            assertTrue("Files at '" + dir + "' should have been deleted", deletedSuccessfully);
        }
    }

    protected void assertDatabaseRecordCount(String databaseName, final long expectedCountNumber)
    {
        long count = getDatabaseCount(databaseName);
        assertEquals("Unexpected database '" + databaseName + "' entry number", expectedCountNumber, count);
    }

    protected long getDatabaseCount(String databaseName)
    {
        DatabaseCallable<Long> operation = new DatabaseCallable<Long>()
        {

            @Override
            public Long call(Database sourceDatabase, Database targetDatabase, Transaction transaction)
            {
                return new Long(sourceDatabase.count());

            }
        };
        Long count = new DatabaseTemplate(_environment, databaseName, null).call(operation);
        return count.longValue();
    }

    public String getVirtualHostName()
    {
        return getName();
    }
}
