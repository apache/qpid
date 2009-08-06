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
package org.apache.qpid.server.logging;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.logging.subjects.AbstractTestLogSubject;

import java.util.List;

/**
 * The MessageStore test suite validates that the follow log messages as
 * specified in the Functional Specification.
 *
 * This suite of tests validate that the MessageStore messages occur correctly
 * and according to the following format:
 *
 * MST-1001 : Created : <name>
 * MST-1003 : Closed
 *
 * NOTE: Only for Persistent Stores
 * MST-1002 : Store location : <path>
 * MST-1004 : Recovery Start [: <queue.name>]
 * MST-1005 : Recovered <count> messages for queue <queue.name>
 * MST-1006 : Recovery Complete [: <queue.name>]
 */
public class DerbyMessageStoreLoggingTest extends MemoryMessageStoreLoggingTest
{

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        // MemoryMessageStoreLoggingTest setUp itself does not call super.setUp
        //We call super.setUp but this will not start the broker as that is
        //part of the test case.

        // Load current configuration file to get the list of defined vhosts
        Configuration configuration = ServerConfiguration.flatConfig(_configFile);
        List<String> vhosts = configuration.getList("virtualhosts.virtualhost.name");

        // Make them all persistent i.e. Use DerbyMessageStore and
        // test that it logs correctly.
        for (String vhost : vhosts)
        {
            makeVirtualHostPersistent(vhost);
        }
    }

    /**
     * Description:
     * Persistent MessageStores will require space on disk to persist the data.
     * This value will be logged on startup after the MessageStore has been
     * created.
     * Input:
     * Default configuration                      
     * Output:
     *
     * <date> MST-1002 : Store location : <path>
     *
     * Validation Steps:
     *
     * 1. The MST ID is correct
     * 2. This must occur after MST-1001
     */
    public void testMessageStoreStoreLocation() throws Exception
    {
        assertLoggingNotYetOccured(MESSAGES_STORE_PREFIX);

        startBroker();

        List<String> results = _monitor.findMatches(MESSAGES_STORE_PREFIX);

        // Validation

        assertTrue("MST messages not logged", results.size() > 0);

        // Load VirtualHost list from file.
        Configuration configuration = ServerConfiguration.flatConfig(_configFile);
        List<String> vhosts = configuration.getList("virtualhosts.virtualhost.name");

        //Validate each vhost logs a creation
        results = _monitor.findMatches("MST-1002");

        assertEquals("Each vhost did not close its store.", vhosts.size(), results.size());

        for (int index = 0; index < results.size(); index++)
        {
            String result = getLog(results.get(index));

            // getSlize will return extract the vhost from vh(/test) -> '/test'
            // so remove the '/' to get the name
            String vhostName = AbstractTestLogSubject.getSlice("vh", result).substring(1);

            // To get the store class used in the configuration we need to know
            // the virtualhost name, found above. AND
            // the index that the virtualhost is within the configuration.
            // we can retrive that from the vhosts list previously extracted.
            String fullStoreName = configuration.getString("virtualhosts.virtualhost(" + vhosts.indexOf(vhostName) + ")." + vhostName + ".store.class");

            // Get the Simple class name from the expected class name of o.a.q.s.s.MMS
            String storeName = fullStoreName.substring(fullStoreName.lastIndexOf(".") + 1);

            assertTrue("MST-1002 does not contain a store path" + getMessageString(result),
                       getMessageString(result).length() > 0);

            assertEquals("The store name does not match expected value",
                         storeName, AbstractTestLogSubject.getSlice("ms", fromSubject(result)));
        }
    }

    /**
     * Description:
     * Persistent message stores may have state on disk that they must recover
     * during startup. As the MessageStore starts up it will report that it is
     * about to start the recovery process by logging MST-1004. This message
     * will always be logged for persistent MessageStores. If there is no data
     * to recover then there will be no subsequent recovery messages.
     * Input:
     * Default persistent configuration
     * Output:
     * <date> MST-1004 : Recovery Start
     *
     * Validation Steps:
     *
     * 1. The MST ID is correct
     * 2. The MessageStore must have first logged a creation event.
     */
    public void testMessageStoreRecoveryStart() throws Exception
    {
        assertLoggingNotYetOccured(MESSAGES_STORE_PREFIX);

        startBroker();

        List<String> results = _monitor.findMatches(MESSAGES_STORE_PREFIX);

        // Validation

        assertTrue("MST messages not logged", results.size() > 0);

        // Load VirtualHost list from file.
        Configuration configuration = ServerConfiguration.flatConfig(_configFile);
        List<String> vhosts = configuration.getList("virtualhosts.virtualhost.name");

        //Validate each vhost logs a creation
        results = _monitor.findMatches("MST-1004");

        assertEquals("Each vhost did not close its store.", vhosts.size(), results.size());

        for (int index = 0; index < results.size(); index++)
        {
            String result = getLog(results.get(index));

            // getSlize will return extract the vhost from vh(/test) -> '/test'
            // so remove the '/' to get the name
            String vhostName = AbstractTestLogSubject.getSlice("vh", result).substring(1);

            // To get the store class used in the configuration we need to know
            // the virtualhost name, found above. AND
            // the index that the virtualhost is within the configuration.
            // we can retrive that from the vhosts list previously extracted.
            String fullStoreName = configuration.getString("virtualhosts.virtualhost(" + vhosts.indexOf(vhostName) + ")." + vhostName + ".store.class");

            // Get the Simple class name from the expected class name of o.a.q.s.s.MMS
            String storeName = fullStoreName.substring(fullStoreName.lastIndexOf(".") + 1);

            assertEquals("MST-1004 does have expected message", "Recovery Start",
                         getMessageString(result));

            assertEquals("The store name does not match expected value",
                         storeName, AbstractTestLogSubject.getSlice("ms", fromSubject(result)));
        }
    }

    /**
     * Description:
     * Once all persistent queues have been recovered and the MessageStore has completed all recovery it must logged that the recovery process has completed.
     * Input:
     * Default persistent configuration
     * Output:
     *
     * <date> MST-1006 : Recovery Complete
     *
     * Validation Steps:
     *
     * 1. The MST ID is correct
     * 2. This is the last message from the MessageStore during startup.
     * 3. This must be proceeded by a MST-1006 Recovery Start.
     */
    public void testMessageStoreRecoveryComplete() throws Exception
    {
        assertLoggingNotYetOccured(MESSAGES_STORE_PREFIX);

        startBroker();

        List<String> results = _monitor.findMatches(MESSAGES_STORE_PREFIX);

        // Validation

        assertTrue("MST messages not logged", results.size() > 0);

        // Load VirtualHost list from file.
        Configuration configuration = ServerConfiguration.flatConfig(_configFile);
        List<String> vhosts = configuration.getList("virtualhosts.virtualhost.name");

        //Validate each vhost logs a creation
        results = _monitor.findMatches("MST-1006");

        assertEquals("Each vhost did not close its store.", vhosts.size(), results.size());

        for (int index = 0; index < results.size(); index++)
        {
            String result = getLog(results.get(index));

            // getSlize will return extract the vhost from vh(/test) -> '/test'
            // so remove the '/' to get the name
            String vhostName = AbstractTestLogSubject.getSlice("vh", result).substring(1);

            // To get the store class used in the configuration we need to know
            // the virtualhost name, found above. AND
            // the index that the virtualhost is within the configuration.
            // we can retrive that from the vhosts list previously extracted.
            String fullStoreName = configuration.getString("virtualhosts.virtualhost(" + vhosts.indexOf(vhostName) + ")." + vhostName + ".store.class");

            // Get the Simple class name from the expected class name of o.a.q.s.s.MMS
            String storeName = fullStoreName.substring(fullStoreName.lastIndexOf(".") + 1);

            assertEquals("MST-1006 does have expected message", "Recovery Complete",
                         getMessageString(result));

            assertEquals("The store name does not match expected value",
                         storeName, AbstractTestLogSubject.getSlice("ms", fromSubject(result)));
        }
    }

}
