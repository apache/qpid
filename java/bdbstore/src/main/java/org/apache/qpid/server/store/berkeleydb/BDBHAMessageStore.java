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
 *
 */
package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.actors.AbstractActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.store.HAMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreRecoveryHandler;
import org.apache.qpid.server.store.State;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.TransactionLogRecoveryHandler;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationFailureException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.InsufficientLogException;
import com.sleepycat.je.rep.NetworkRestore;
import com.sleepycat.je.rep.NetworkRestoreConfig;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.ReplicationMutableConfig;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;
import com.sleepycat.je.rep.util.ReplicationGroupAdmin;

public class BDBHAMessageStore extends AbstractBDBMessageStore implements HAMessageStore
{
    private static final Logger LOGGER = Logger.getLogger(BDBHAMessageStore.class);

    private static final Durability DEFAULT_DURABILITY = new Durability(SyncPolicy.NO_SYNC, SyncPolicy.NO_SYNC, ReplicaAckPolicy.SIMPLE_MAJORITY);

    public static final String GRP_MEM_COL_NODE_HOST_PORT = "NodeHostPort";
    public static final String GRP_MEM_COL_NODE_NAME = "NodeName";

    @SuppressWarnings("serial")
    private static final Map<String, String> REPCONFIG_DEFAULTS = Collections.unmodifiableMap(new HashMap<String, String>()
    {{
        /**
         * Parameter decreased as the 24h default may lead very large log files for most users.
         */
        put(ReplicationConfig.REP_STREAM_TIMEOUT, "1 h");
        /**
         * Parameter increased as the 5 s default may lead to spurious timeouts.
         */
        put(ReplicationConfig.REPLICA_ACK_TIMEOUT, "15 s");
        /**
         * Parameter increased as the 10 s default may lead to spurious timeouts.
         */
        put(ReplicationConfig.INSUFFICIENT_REPLICAS_TIMEOUT, "20 s");
        /**
         * Parameter increased as the 10 h default may cause user confusion.
         */
        put(ReplicationConfig.ENV_SETUP_TIMEOUT, "15 min");
        /**
         * Parameter changed from default true so we adopt immediately adopt the new behaviour early. False
         * is scheduled to become default after JE 5.0.48.
         */
        put(ReplicationConfig.PROTOCOL_OLD_STRING_ENCODING, Boolean.FALSE.toString());
        /**
         * Parameter decreased as a default 5min interval may lead to bigger data losses on Node
         * with NO_SYN durability in case if such Node crushes.
         */
        put(ReplicationConfig.LOG_FLUSH_TASK_INTERVAL, "1 min");
    }});

    public static final String TYPE = "BDB-HA";

    private String _groupName;
    private String _nodeName;
    private String _nodeHostPort;
    private String _helperHostPort;
    private Durability _durability;

    private String _name;

    private CommitThreadWrapper _commitThreadWrapper;
    private boolean _coalescingSync;
    private boolean _designatedPrimary;
    private Map<String, String> _repConfig;

    @Override
    public void configure(String name, Configuration storeConfig) throws Exception
    {
        //Mandatory configuration
        _groupName = getValidatedPropertyFromConfig("highAvailability.groupName", storeConfig);
        _nodeName = getValidatedPropertyFromConfig("highAvailability.nodeName", storeConfig);
        _nodeHostPort = getValidatedPropertyFromConfig("highAvailability.nodeHostPort", storeConfig);
        _helperHostPort = getValidatedPropertyFromConfig("highAvailability.helperHostPort", storeConfig);
        _name = name;

        //Optional configuration
        String durabilitySetting = storeConfig.getString("highAvailability.durability");
        if (durabilitySetting == null)
        {
            _durability = DEFAULT_DURABILITY;
        }
        else
        {
            _durability = Durability.parse(durabilitySetting);
        }
        _designatedPrimary = storeConfig.getBoolean("highAvailability.designatedPrimary", Boolean.FALSE);
        _coalescingSync = storeConfig.getBoolean("highAvailability.coalescingSync", Boolean.TRUE);
        _repConfig = getConfigMap(REPCONFIG_DEFAULTS, storeConfig, "repConfig");

        if (_coalescingSync && _durability.getLocalSync() == SyncPolicy.SYNC)
        {
            throw new ConfigurationException("Coalescing sync cannot be used with master sync policy " + SyncPolicy.SYNC
                    + "! Please set highAvailability.coalescingSync to false in store configuration.");
        }

        super.configure(name, storeConfig);
    }

    @Override
    protected void setupStore(File storePath, String name) throws DatabaseException, AMQStoreException
    {
        super.setupStore(storePath, name);

        if(_coalescingSync)
        {
            _commitThreadWrapper = new CommitThreadWrapper("Commit-Thread-" + name, getEnvironment());
            _commitThreadWrapper.startCommitThread();
        }
    }

    @Override
    protected Environment createEnvironment(File environmentPath) throws DatabaseException
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Environment path " + environmentPath.getAbsolutePath());
            LOGGER.info("Group name " + _groupName);
            LOGGER.info("Node name " + _nodeName);
            LOGGER.info("Node host port " + _nodeHostPort);
            LOGGER.info("Helper host port " + _helperHostPort);
            LOGGER.info("Durability " + _durability);
            LOGGER.info("Coalescing sync " + _coalescingSync);
            LOGGER.info("Designated primary (applicable to 2 node case only) " + _designatedPrimary);
        }

        final ReplicationConfig replicationConfig = new ReplicationConfig(_groupName, _nodeName, _nodeHostPort);

        replicationConfig.setHelperHosts(_helperHostPort);
        replicationConfig.setDesignatedPrimary(_designatedPrimary);
        setReplicationConfigProperties(replicationConfig);

        final EnvironmentConfig envConfig = createEnvironmentConfig();
        envConfig.setDurability(_durability);

        ReplicatedEnvironment replicatedEnvironment = null;
        try
        {
            replicatedEnvironment = new ReplicatedEnvironment(environmentPath, replicationConfig, envConfig);
        }
        catch (final InsufficientLogException ile)
        {
            LOGGER.info("InsufficientLogException thrown and so full network restore required", ile);
            NetworkRestore restore = new NetworkRestore();
            NetworkRestoreConfig config = new NetworkRestoreConfig();
            config.setRetainLogFiles(false);
            restore.execute(ile, config);
            replicatedEnvironment = new ReplicatedEnvironment(environmentPath, replicationConfig, envConfig);
        }

        return replicatedEnvironment;
    }

    @Override
    public void configureMessageStore(String name, MessageStoreRecoveryHandler messageRecoveryHandler,
                                        TransactionLogRecoveryHandler tlogRecoveryHandler,
                                        Configuration config) throws Exception
    {
        super.configureMessageStore(name, messageRecoveryHandler, tlogRecoveryHandler, config);

        final ReplicatedEnvironment replicatedEnvironment = getReplicatedEnvironment();

        replicatedEnvironment.setStateChangeListener(new BDBHAMessageStoreStateChangeListener());
    }

    @Override
    public synchronized void activate() throws Exception
    {
        // Before proceeding, perform a log flush with an fsync
        getEnvironment().flushLog(true);

        super.activate();
    }

    @Override
    public synchronized void passivate()
    {
        if (_stateManager.isNotInState(State.INITIALISED))
        {
            LOGGER.debug("Store becoming passive");
            _stateManager.attainState(State.INITIALISED);
        }
    }

    public String getName()
    {
        return _name;
    }

    public String getGroupName()
    {
        return _groupName;
    }

    public String getNodeName()
    {
        return _nodeName;
    }

    public String getNodeHostPort()
    {
        return _nodeHostPort;
    }

    public String getHelperHostPort()
    {
        return _helperHostPort;
    }

    public String getDurability()
    {
        return _durability.toString();
    }

    public boolean isCoalescingSync()
    {
        return _coalescingSync;
    }

    public String getNodeState()
    {
        ReplicatedEnvironment.State state = getReplicatedEnvironment().getState();
        return state.toString();
    }

    public Boolean isDesignatedPrimary()
    {
        return getReplicatedEnvironment().getRepMutableConfig().getDesignatedPrimary();
    }

    public List<Map<String, String>> getGroupMembers()
    {
        List<Map<String, String>> members = new ArrayList<Map<String,String>>();

        for (ReplicationNode node : getReplicatedEnvironment().getGroup().getNodes())
        {
            Map<String, String> nodeMap = new HashMap<String, String>();
            nodeMap.put(BDBHAMessageStore.GRP_MEM_COL_NODE_NAME, node.getName());
            nodeMap.put(BDBHAMessageStore.GRP_MEM_COL_NODE_HOST_PORT, node.getHostName() + ":" + node.getPort());
            members.add(nodeMap);
        }

        return members;
    }

    public void removeNodeFromGroup(String nodeName)  throws AMQStoreException
    {
        try
        {
            createReplicationGroupAdmin().removeMember(nodeName);
        }
        catch (OperationFailureException ofe)
        {
            throw new AMQStoreException("Failed to remove '" + nodeName + "' from group. " + ofe.getMessage(), ofe);
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Failed to remove '" + nodeName + "' from group. " + e.getMessage(), e);
        }
    }

    public void setDesignatedPrimary(boolean isPrimary) throws AMQStoreException
    {
        try
        {
            final ReplicatedEnvironment replicatedEnvironment = getReplicatedEnvironment();
            synchronized(replicatedEnvironment)
            {
                final ReplicationMutableConfig oldConfig = replicatedEnvironment.getRepMutableConfig();
                final ReplicationMutableConfig newConfig = oldConfig.setDesignatedPrimary(isPrimary);
                replicatedEnvironment.setRepMutableConfig(newConfig);
            }

            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Node " + _nodeName + " successfully set as designated primary for group");
            }
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Failed to set '" + _nodeName + "' as designated primary for group. " + e.getMessage(), e);
        }
    }

    ReplicatedEnvironment getReplicatedEnvironment()
    {
        return (ReplicatedEnvironment)getEnvironment();
    }

    public void updateAddress(String nodeName, String newHostName, int newPort) throws AMQStoreException
    {
        try
        {
            createReplicationGroupAdmin().updateAddress(nodeName, newHostName, newPort);
        }
        catch (OperationFailureException ofe)
        {
            throw new AMQStoreException("Failed to update address for '" + nodeName +
                                        "' with new host " + newHostName + " and new port " + newPort + ". " + ofe.getMessage(), ofe);
        }
        catch (DatabaseException e)
        {
            throw new AMQStoreException("Failed to update address for '" + nodeName +
                                        "' with new host " + newHostName + " and new port " + newPort + ". " + e.getMessage(),  e);
        }
    }

    @Override
    protected StoreFuture commit(Transaction tx, boolean syncCommit) throws DatabaseException
    {
        // Using commit() instead of commitNoSync() for the HA store to allow
        // the HA durability configuration to influence resulting behaviour.
        try
        {
            tx.commit();
        }
        catch (DatabaseException de)
        {
            LOGGER.error("Got DatabaseException on commit, closing environment", de);

            closeEnvironmentSafely();

            throw de;
        }

        if(_coalescingSync)
        {
            return _commitThreadWrapper.commit(tx, syncCommit);
        }
        else
        {
            return StoreFuture.IMMEDIATE_FUTURE;
        }
    }

    @Override
    protected void closeInternal() throws Exception
    {
        substituteNoOpStateChangeListenerOn(getReplicatedEnvironment());

        try
        {
            if(_coalescingSync)
            {
                _commitThreadWrapper.stopCommitThread();
            }
        }
        finally
        {
            super.closeInternal();
        }
    }

    /**
     * Replicas emit a state change event {@link com.sleepycat.je.rep.ReplicatedEnvironment.State#DETACHED} during
     * {@link Environment#close()}.  We replace the StateChangeListener so we silently ignore this state change.
     */
    private void substituteNoOpStateChangeListenerOn(ReplicatedEnvironment replicatedEnvironment)
    {
        LOGGER.debug("Substituting no-op state change listener for environment close");
        replicatedEnvironment.setStateChangeListener(new NoOpStateChangeListener());
    }

    private ReplicationGroupAdmin createReplicationGroupAdmin()
    {
        final Set<InetSocketAddress> helpers = new HashSet<InetSocketAddress>();
        helpers.addAll(getReplicatedEnvironment().getRepConfig().getHelperSockets());

        final ReplicationConfig repConfig = getReplicatedEnvironment().getRepConfig();
        helpers.add(InetSocketAddress.createUnresolved(repConfig.getNodeHostname(), repConfig.getNodePort()));

        return new ReplicationGroupAdmin(_groupName, helpers);
    }


    private void setReplicationConfigProperties(ReplicationConfig replicationConfig)
    {
        for (Map.Entry<String, String> configItem : _repConfig.entrySet())
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Setting ReplicationConfig key " + configItem.getKey() + " to '" + configItem.getValue() + "'");
            }
            replicationConfig.setConfigParam(configItem.getKey(), configItem.getValue());
        }
    }

    private String getValidatedPropertyFromConfig(String key, Configuration config) throws ConfigurationException
    {
        if (!config.containsKey(key))
        {
            throw new ConfigurationException("BDB HA configuration key not found. Please specify configuration key with XPath: "
                                                + key.replace('.', '/'));
        }
        return config.getString(key);
    }

    private class BDBHAMessageStoreStateChangeListener implements StateChangeListener
    {
        private final Executor _executor = Executors.newSingleThreadExecutor();

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
                activateStoreAsync();
                break;
            case REPLICA:
                passivateStoreAsync();
                break;
            case DETACHED:
                LOGGER.error("BDB replicated node in detached state, therefore passivating.");
                passivateStoreAsync();
                break;
            case UNKNOWN:
                LOGGER.warn("BDB replicated node in unknown state (hopefully temporarily)");
                break;
            default:
                LOGGER.error("Unexpected state change: " + state);
                throw new IllegalStateException("Unexpected state change: " + state);
            }
        }

        /**
         * Calls {@link MessageStore#activate()}.
         *
         * <p/>
         *
         * This is done a background thread, in line with
         * {@link StateChangeListener#stateChange(StateChangeEvent)}'s JavaDoc, because
         * activate may execute transactions, which can't complete until
         * {@link StateChangeListener#stateChange(StateChangeEvent)} has returned.
         */
        private void activateStoreAsync()
        {
            String threadName = "BDBHANodeActivationThread-" + _name;
            executeStateChangeAsync(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    try
                    {
                        activate();
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Failed to activate on hearing MASTER change event",e);
                        throw e;
                    }
                    return null;
                }
            }, threadName);
        }

        /**
         * Calls {@link #passivate()}.
         *
         * <p/>
         * This is done a background thread, in line with
         * {@link StateChangeListener#stateChange(StateChangeEvent)}'s JavaDoc, because
         * passivation due to the effect of state change listeners.
         */
        private void passivateStoreAsync()
        {
            String threadName = "BDBHANodePassivationThread-" + _name;
            executeStateChangeAsync(new Callable<Void>()
            {

                @Override
                public Void call() throws Exception
                {
                    try
                    {
                        passivate();
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Failed to passivate on hearing REPLICA or DETACHED change event",e);
                        throw e;
                    }
                    return null;
                }
            }, threadName);
        }

        private void executeStateChangeAsync(final Callable<Void> callable, final String threadName)
        {
            final RootMessageLogger _rootLogger = CurrentActor.get().getRootMessageLogger();

            _executor.execute(new Runnable()
            {

                @Override
                public void run()
                {
                    final String originalThreadName = Thread.currentThread().getName();
                    Thread.currentThread().setName(threadName);
                    try
                    {
                        CurrentActor.set(new AbstractActor(_rootLogger)
                        {
                            @Override
                            public String getLogMessage()
                            {
                                return threadName;
                            }
                        });

                        try
                        {
                            callable.call();
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Exception during state change", e);
                        }
                    }
                    finally
                    {
                        Thread.currentThread().setName(originalThreadName);
                    }
                }
            });
        }
    }

    private class NoOpStateChangeListener implements StateChangeListener
    {
        @Override
        public void stateChange(StateChangeEvent stateChangeEvent)
                throws RuntimeException
        {
        }
    }

    @Override
    public String getStoreType()
    {
        return TYPE;
    }
}
