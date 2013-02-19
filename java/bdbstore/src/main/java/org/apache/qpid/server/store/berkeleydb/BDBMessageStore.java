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

import java.io.File;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreFuture;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

/**
 * BDBMessageStore implements a persistent {@link MessageStore} using the BDB high performance log.
 *
 * <p/><table id="crc"><caption>CRC Card</caption> <tr><th> Responsibilities <th> Collaborations <tr><td> Accept
 * transaction boundary demarcations: Begin, Commit, Abort. <tr><td> Store and remove queues. <tr><td> Store and remove
 * exchanges. <tr><td> Store and remove messages. <tr><td> Bind and unbind queues to exchanges. <tr><td> Enqueue and
 * dequeue messages to queues. <tr><td> Generate message identifiers. </table>
 */
public class BDBMessageStore extends AbstractBDBMessageStore
{
    private static final Logger LOGGER = Logger.getLogger(BDBMessageStore.class);
    public static final String TYPE = "BDB";
    private CommitThreadWrapper _commitThreadWrapper;

    @Override
    protected void setupStore(File storePath, String name) throws DatabaseException, AMQStoreException
    {
        super.setupStore(storePath, name);

        _commitThreadWrapper = new CommitThreadWrapper("Commit-Thread-" + name, getEnvironment());
        _commitThreadWrapper.startCommitThread();
    }

    protected Environment createEnvironment(File environmentPath) throws DatabaseException
    {
        LOGGER.info("BDB message store using environment path " + environmentPath.getAbsolutePath());
        EnvironmentConfig envConfig = createEnvironmentConfig();

        try
        {
            return new Environment(environmentPath, envConfig);
        }
        catch (DatabaseException de)
        {
            if (de.getMessage().contains("Environment.setAllowCreate is false"))
            {
                //Allow the creation this time
                envConfig.setAllowCreate(true);
                return new Environment(environmentPath, envConfig);
            }
            else
            {
                throw de;
            }
        }
    }

    @Override
    protected void closeInternal() throws Exception
    {
        _commitThreadWrapper.stopCommitThread();

        super.closeInternal();
    }

    @Override
    protected StoreFuture commit(com.sleepycat.je.Transaction tx, boolean syncCommit) throws DatabaseException
    {
        try
        {
            tx.commitNoSync();
        }
        catch(DatabaseException de)
        {
            LOGGER.error("Got DatabaseException on commit, closing environment", de);

            closeEnvironmentSafely();

            throw de;
        }

        return _commitThreadWrapper.commit(tx, syncCommit);
    }

    @Override
    public String getStoreType()
    {
        return TYPE;
    }

}
