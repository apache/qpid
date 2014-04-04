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

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.qpid.server.connection.IConnectionRegistry;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.ConfiguredObjectRecordRecoveverAndUpgrader;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.replication.ReplicatedEnvironmentFacadeFactory;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;
import org.apache.qpid.server.virtualhost.MessageStoreRecoverer;
import org.apache.qpid.server.virtualhost.State;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;

public class BDBHAVirtualHost extends AbstractVirtualHost
{
    private static final Logger LOGGER = Logger.getLogger(BDBHAVirtualHost.class);

    private BDBMessageStore _messageStore;
    private MessageStoreLogSubject _messageStoreLogSubject;

    BDBHAVirtualHost(VirtualHostRegistry virtualHostRegistry,
                     StatisticsGatherer brokerStatisticsGatherer,
                     org.apache.qpid.server.security.SecurityManager parentSecurityManager,
                     VirtualHost virtualHost)
    {
        super(virtualHostRegistry, brokerStatisticsGatherer, parentSecurityManager, virtualHost);
    }

    protected void initialiseStorage(VirtualHost virtualHost)
    {
        setState(State.PASSIVE);

        _messageStoreLogSubject = new MessageStoreLogSubject(getName(), BDBMessageStore.class.getSimpleName());
        _messageStore = new BDBMessageStore(new ReplicatedEnvironmentFacadeFactory());
        getEventLogger().message(_messageStoreLogSubject, MessageStoreMessages.CREATED());

        Map<String, Object> messageStoreSettings = new HashMap<String, Object>(virtualHost.getMessageStoreSettings());
        messageStoreSettings.put(DurableConfigurationStore.IS_MESSAGE_STORE_TOO, true);

        _messageStore.openConfigurationStore(virtualHost, messageStoreSettings);
        _messageStore.openMessageStore(virtualHost, messageStoreSettings);

        getEventLogger().message(_messageStoreLogSubject, MessageStoreMessages.STORE_LOCATION(_messageStore.getStoreLocation()));

        // Make the virtualhost model object a replication group listener
        ReplicatedEnvironmentFacade environmentFacade = (ReplicatedEnvironmentFacade) _messageStore.getEnvironmentFacade();
        environmentFacade.setStateChangeListener(new BDBHAMessageStoreStateChangeListener());

    }

    @Override
    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return _messageStore;
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    private void activate()
    {
        try
        {
            _messageStore.getEnvironmentFacade().getEnvironment().flushLog(true);

            ConfiguredObjectRecordHandler upgraderRecoverer = new ConfiguredObjectRecordRecoveverAndUpgrader(this, getDurableConfigurationRecoverers());
            _messageStore.visitConfiguredObjectRecords(upgraderRecoverer);

            initialiseModel();

            new MessageStoreRecoverer(this, getMessageStoreLogSubject()).recover();

            attainActivation();
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to activate on hearing MASTER change event", e);
        }
    }

    private void passivate()
    {
        State finalState = State.ERRORED;

        try
        {
            /* the approach here is not ideal as there is a race condition where a
             * queue etc could be created while the virtual host is on the way to
             * the passivated state.  However the store state change from MASTER to UNKNOWN
             * is documented as exceptionally rare.
             */

            getConnectionRegistry().close(IConnectionRegistry.VHOST_PASSIVATE_REPLY_TEXT);
            removeHouseKeepingTasks();

            getQueueRegistry().stopAllAndUnregisterMBeans();
            getExchangeRegistry().clearAndUnregisterMbeans();
            getDtxRegistry().close();

            finalState = State.PASSIVE;
        }
        finally
        {
            setState(finalState);
            reportIfError(getState());
        }
    }

    @Override
    protected MessageStoreLogSubject getMessageStoreLogSubject()
    {
        return _messageStoreLogSubject;
    }

    private class BDBHAMessageStoreStateChangeListener implements StateChangeListener
    {

        @Override
        public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
        {
            com.sleepycat.je.rep.ReplicatedEnvironment.State state = stateChangeEvent.getState();

            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Received BDB event indicating transition to state " + state);
            }

            switch (state)
            {
            case MASTER:
                activate();
                break;
            case REPLICA:
                passivate();
                break;
            case DETACHED:
                LOGGER.error("BDB replicated node in detached state, therefore passivating.");
                passivate();
                break;
            case UNKNOWN:
                LOGGER.warn("BDB replicated node in unknown state (hopefully temporarily)");
                break;
            default:
                LOGGER.error("Unexpected state change: " + state);
                throw new IllegalStateException("Unexpected state change: " + state);
            }
        }
    }

}
