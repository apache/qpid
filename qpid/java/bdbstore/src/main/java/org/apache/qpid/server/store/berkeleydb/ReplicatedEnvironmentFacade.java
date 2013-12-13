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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.replication.ReplicationGroupListener;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.berkeleydb.replication.RemoteReplicationNode;
import org.apache.qpid.server.store.berkeleydb.replication.RemoteReplicationNodeFactory;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.ExceptionEvent;
import com.sleepycat.je.ExceptionListener;
import com.sleepycat.je.OperationFailureException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.rep.InsufficientLogException;
import com.sleepycat.je.rep.InsufficientReplicasException;
import com.sleepycat.je.rep.NetworkRestore;
import com.sleepycat.je.rep.NetworkRestoreConfig;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.ReplicationGroup;
import com.sleepycat.je.rep.ReplicationMutableConfig;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;
import com.sleepycat.je.rep.util.ReplicationGroupAdmin;

public class ReplicatedEnvironmentFacade implements EnvironmentFacade, StateChangeListener
{
    private static final Logger LOGGER = Logger.getLogger(ReplicatedEnvironmentFacade.class);

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

        /**
         * Timeout to transit into UNKNOWN state if the majority is not available.
         * By default it is switched off.
         */
        put(ReplicationConfig.ENV_UNKNOWN_STATE_TIMEOUT, "5 s");
    }});

    public static final String TYPE = "BDB-HA";

    // TODO: get rid of these names
    public static final String GRP_MEM_COL_NODE_HOST_PORT = "NodeHostPort";
    public static final String GRP_MEM_COL_NODE_NAME = "NodeName";

    private volatile ReplicatedEnvironment _environment;
    private CommitThreadWrapper _commitThreadWrapper;

    private final String _groupName;
    private final String _nodeName;
    private final String _nodeHostPort;
    private final String _helperHostPort;
    private final Durability _durability;
    private final boolean _designatedPrimary;
    private final boolean _coalescingSync;
    private volatile StateChangeListener _stateChangeListener;
    private final String _environmentPath;
    private final Map<String, String> _environmentParameters;
    private final Map<String, String> _replicationEnvironmentParameters;
    private final String _name;
    private final ExecutorService _executor = Executors.newFixedThreadPool(1);
    private final AtomicReference<State> _state = new AtomicReference<State>(State.INITIAL);

    private final ConcurrentMap<String, Database> _databases = new ConcurrentHashMap<String, Database>();

    private ReplicationGroupListener _replicationGroupListener;
    private final RemoteReplicationNodeFactory _remoteReplicationNodeFactory;


    public ReplicatedEnvironmentFacade(String name, String environmentPath,
            String groupName, String nodeName, String nodeHostPort,
            String helperHostPort, Durability durability,
            Boolean designatedPrimary, Boolean coalescingSync,
            Map<String, String> envConfigMap,
            Map<String, String> replicationConfig, RemoteReplicationNodeFactory remoteReplicationNodeFactory)
    {
        _name = name;
        _environmentPath = environmentPath;
        _groupName = groupName;
        _nodeName = nodeName;
        _nodeHostPort = nodeHostPort;
        _helperHostPort = helperHostPort;
        _durability = durability;
        _designatedPrimary = designatedPrimary;
        _coalescingSync = coalescingSync;
        _environmentParameters = envConfigMap;
        _replicationEnvironmentParameters = replicationConfig;

        _remoteReplicationNodeFactory = remoteReplicationNodeFactory;
        _state.set(State.OPENING);
        _environment = createEnvironment(environmentPath, groupName, nodeName, nodeHostPort, helperHostPort, durability,
                designatedPrimary, _environmentParameters, _replicationEnvironmentParameters);
        startCommitThread(_name, _environment);
    }


    @Override
    public StoreFuture commit(final Transaction tx, final boolean syncCommit) throws AMQStoreException
    {
        try
        {
            // Using commit() instead of commitNoSync() for the HA store
            // to allow
            // the HA durability configuration to influence resulting
            // behaviour.
            tx.commit();

            if (_coalescingSync)
            {
                return _commitThreadWrapper.commit(tx, syncCommit);
            }
            else
            {
                return StoreFuture.IMMEDIATE_FUTURE;
            }
        }
        catch (DatabaseException de)
        {
            throw handleDatabaseException("Got DatabaseException on commit, closing environment", de);
        }
    }

    @Override
    public void close()
    {
        if (_state.compareAndSet(State.INITIAL, State.CLOSING) || _state.compareAndSet(State.OPENING, State.CLOSING) ||
                _state.compareAndSet(State.OPEN, State.CLOSING) || _state.compareAndSet(State.RESTARTING, State.CLOSING) )
        {
            try
            {
                _executor.shutdownNow();
                stopCommitThread();
                closeDatabases();
                closeEnvironment();
            }
            finally
            {
                _state.compareAndSet(State.CLOSING, State.CLOSED);
            }
        }
    }

    @Override
    public AMQStoreException handleDatabaseException(String contextMessage, DatabaseException e)
    {
        boolean restart = (e instanceof InsufficientReplicasException || e instanceof InsufficientReplicasException);
        if (restart)
        {
            if (_state.compareAndSet(State.OPEN, State.RESTARTING))
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Environment restarting due to exception " + e.getMessage(), e);
                }
                _executor.execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            restartEnvironment();
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Exception on environment restart", e);
                        }
                    }
                });

            }
            else
            {
                LOGGER.info("Cannot restart environment because of facade state: " + _state.get());
            }
        }
        return new AMQStoreException(contextMessage, e);
    }

    @Override
    public void openDatabases(String[] databaseNames, DatabaseConfig dbConfig) throws AMQStoreException
    {
        for (String databaseName : databaseNames)
        {
            Database database = _environment.openDatabase(null, databaseName, dbConfig);
            _databases.put(databaseName, database);
        }
    }

    @Override
    public Database getOpenDatabase(String name)
    {
        if (!_environment.isValid())
        {
            throw new IllegalStateException("Environment is not valid");
        }
        Database database = _databases.get(name);
        if (database == null)
        {
            throw new IllegalArgumentException("Database with name '" + name + "' has not been opened");
        }
        return database;
    }

    @Override
    public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
    {
        ReplicatedEnvironment.State state = stateChangeEvent.getState();
        LOGGER.info("The node state is " + state);
        if (state == ReplicatedEnvironment.State.REPLICA || state == ReplicatedEnvironment.State.MASTER)
        {
            if (_state.compareAndSet(State.OPENING, State.OPEN) || _state.compareAndSet(State.RESTARTING, State.OPEN))
            {
                LOGGER.info("The environment facade is in open state");
            }
        }
        if (_state.get() != State.CLOSING && _state.get() != State.CLOSED)
        {
            StateChangeListener listener = _stateChangeListener;
            if (listener != null)
            {
                listener.stateChange(stateChangeEvent);
            }
        }
    }

    public void setStateChangeListener(StateChangeListener listener)
    {
        _stateChangeListener = listener;
        _environment.setStateChangeListener(this);
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
        ReplicatedEnvironment.State state = _environment.getState();
        return state.toString();
    }

    public boolean isDesignatedPrimary()
    {
        return _environment.getRepMutableConfig().getDesignatedPrimary();
    }

    public List<Map<String, String>> getGroupMembers()
    {
        List<Map<String, String>> members = new ArrayList<Map<String, String>>();

        for (ReplicationNode node : _environment.getGroup().getNodes())
        {
            Map<String, String> nodeMap = new HashMap<String, String>();
            nodeMap.put(ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_NAME, node.getName());
            nodeMap.put(ReplicatedEnvironmentFacade.GRP_MEM_COL_NODE_HOST_PORT, node.getHostName() + ":" + node.getPort());
            members.add(nodeMap);
        }

        return members;
    }

    public void removeNodeFromGroup(final String nodeName) throws AMQStoreException
    {
        try
        {
            createReplicationGroupAdmin().removeMember(nodeName);
        }
        catch (OperationFailureException ofe)
        {
            // TODO: I am not sure about the exception handing here
            throw new AMQStoreException("Failed to remove '" + nodeName + "' from group. " + ofe.getMessage(), ofe);
        }
        catch (DatabaseException e)
        {
            // TODO: I am not sure about the exception handing here
            throw new AMQStoreException("Failed to remove '" + nodeName + "' from group. " + e.getMessage(), e);
        }
    }

    public void setDesignatedPrimary(final boolean isPrimary) throws AMQStoreException
    {
        try
        {
            final ReplicationMutableConfig oldConfig = _environment.getRepMutableConfig();
            final ReplicationMutableConfig newConfig = oldConfig.setDesignatedPrimary(isPrimary);
            _environment.setRepMutableConfig(newConfig);

            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Node " + _nodeName + " successfully set as designated primary for group");
            }

        }
        catch (DatabaseException e)
        {
            // TODO: I am not sure about the exception handing here
            throw handleDatabaseException("Cannot set designated primary", e);
        }
    }

    public void updateAddress(final String nodeName, final String newHostName, final int newPort) throws AMQStoreException
    {
        try
        {
            createReplicationGroupAdmin().updateAddress(nodeName, newHostName, newPort);

        }
        catch (OperationFailureException ofe)
        {
            // TODO: I am not sure about the exception handing here
            throw new AMQStoreException("Failed to update address for '" + nodeName + "' with new host " + newHostName
                    + " and new port " + newPort + ". " + ofe.getMessage(), ofe);
        }
        catch (DatabaseException e)
        {
            // TODO: I am not sure about the exception handing here
            throw handleDatabaseException("Failed to update address for '" + nodeName + "' with new host " + newHostName
                    + " and new port " + newPort + ". " + e.getMessage(), e);
        }
    }

    public int getNodePriority()
    {
        ReplicationMutableConfig repConfig = _environment.getRepMutableConfig();
        return repConfig.getNodePriority();
    }

    public int getElectableGroupSizeOverride()
    {
        ReplicationMutableConfig repConfig = _environment.getRepMutableConfig();
        return repConfig.getElectableGroupSizeOverride();
    }

    public ReplicatedEnvironment getEnvironment()
    {
        return _environment;
    }

    public State getFacadeState()
    {
        return _state.get();
    }

    /**
     * Sets the replication group listener.  Whenever a new listener is set, the listener
     * will hear {@link ReplicationGroupListener#onReplicationNodeRecovered(org.apache.qpid.server.model.ReplicationNode)
     * for every existing remote node.
     *
     * @param replicationGroupListener listener
     */
    public void setReplicationGroupListener(ReplicationGroupListener replicationGroupListener)
    {
        _replicationGroupListener = replicationGroupListener;
        if (_replicationGroupListener != null)
        {
            notifyExistingRemoteReplicationNodes(_replicationGroupListener);
        }
    }

    private void notifyExistingRemoteReplicationNodes(ReplicationGroupListener listener)
    {
        ReplicationGroup group = _environment.getGroup();
        Set<ReplicationNode> nodes = new HashSet<ReplicationNode>(group.getElectableNodes());
        String localNodeName = getNodeName();

        for (ReplicationNode replicationNode : nodes)
        {
            String discoveredNodeName = replicationNode.getName();
            if (!discoveredNodeName.equals(localNodeName))
            {
                // TODO remote replication nodes should be cached
                RemoteReplicationNode remoteNode = _remoteReplicationNodeFactory.create(group.getName(),
                                                                             replicationNode.getName(),
                                                                             replicationNode.getHostName(), replicationNode.getPort());
                listener.onReplicationNodeRecovered(remoteNode);
            }
        }
    }

    private ReplicationGroupAdmin createReplicationGroupAdmin()
    {
        final Set<InetSocketAddress> helpers = new HashSet<InetSocketAddress>();
        helpers.addAll(_environment.getRepConfig().getHelperSockets());

        final ReplicationConfig repConfig = _environment.getRepConfig();
        helpers.add(InetSocketAddress.createUnresolved(repConfig.getNodeHostname(), repConfig.getNodePort()));

        return new ReplicationGroupAdmin(_groupName, helpers);
    }

    private void closeEnvironment()
    {
        // Clean the log before closing. This makes sure it doesn't contain
        // redundant data. Closing without doing this means the cleaner may not
        // get a chance to finish.
        try
        {
            if (_environment.isValid())
            {
                _environment.cleanLog();
            }
        }
        finally
        {
            _environment.close();
            _environment = null;
        }
    }

    private void startCommitThread(String name, Environment environment)
    {
        if (_coalescingSync)
        {
            _commitThreadWrapper = new CommitThreadWrapper("Commit-Thread-" + name, environment);
            _commitThreadWrapper.startCommitThread();
        }
    }

    private void stopCommitThread()
    {
        if (_coalescingSync)
        {
            try
            {
                _commitThreadWrapper.stopCommitThread();
            }
            catch (InterruptedException e)
            {
                LOGGER.warn("Stopping of commit thread is interrupted", e);
                Thread.interrupted();
            }
        }
    }

    private void restartEnvironment() throws AMQStoreException
    {
        LOGGER.info("Restarting environment");

        stopCommitThread();

        Set<String> databaseNames = new HashSet<String>(_databases.keySet());
        closeEnvironmentSafely();

        _environment = createEnvironment(_environmentPath, _groupName, _nodeName, _nodeHostPort, _helperHostPort, _durability,
                _designatedPrimary, _environmentParameters, _replicationEnvironmentParameters);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        // TODO Alex and I think this should be removed.
        openDatabases(databaseNames.toArray(new String[databaseNames.size()]), dbConfig);

        startCommitThread(_name, _environment);

        _environment.setStateChangeListener(this);

        LOGGER.info("Environment is restarted");

    }

    private void closeEnvironmentSafely()
    {
        Environment environment = _environment;
        if (environment != null)
        {
            try
            {
                if (environment.isValid())
                {
                    try
                    {
                        closeDatabases();
                    }
                    catch(Exception e)
                    {
                        LOGGER.warn("Ignoring an exception whilst closing databases", e);
                    }
                }
                environment.close();
            }
            catch (EnvironmentFailureException efe)
            {
                LOGGER.warn("Ignoring an exception whilst closing environment", efe);
            }
        }
    }

    private void closeDatabases()
    {
        RuntimeException firstThrownException = null;
        for (Database database : _databases.values())
        {
            try
            {
                database.close();
            }
            catch(RuntimeException e)
            {
                if (firstThrownException == null)
                {
                    firstThrownException = e;
                }
            }
        }
        _databases.clear();
        if (firstThrownException != null)
        {
            throw firstThrownException;
        }
    }

    private ReplicatedEnvironment createEnvironment(String environmentPath, String groupName, String nodeName, String nodeHostPort,
            String helperHostPort, Durability durability, boolean designatedPrimary, Map<String, String> environmentParameters,
            Map<String, String> replicationEnvironmentParameters)
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Creating environment");
            LOGGER.info("Environment path " + environmentPath);
            LOGGER.info("Group name " + groupName);
            LOGGER.info("Node name " + nodeName);
            LOGGER.info("Node host port " + nodeHostPort);
            LOGGER.info("Helper host port " + helperHostPort);
            LOGGER.info("Durability " + durability);
            LOGGER.info("Coalescing sync " + _coalescingSync);
            LOGGER.info("Designated primary (applicable to 2 node case only) " + designatedPrimary);
        }

        Map<String, String> replicationEnvironmentSettings = new HashMap<String, String>(REPCONFIG_DEFAULTS);
        if (replicationEnvironmentParameters != null && !replicationEnvironmentParameters.isEmpty())
        {
            replicationEnvironmentSettings.putAll(replicationEnvironmentParameters);
        }
        Map<String, String> environmentSettings = new HashMap<String, String>(EnvironmentFacade.ENVCONFIG_DEFAULTS);
        if (environmentParameters != null && !environmentParameters.isEmpty())
        {
            environmentSettings.putAll(environmentParameters);
        }

        final ReplicationConfig replicationConfig = new ReplicationConfig(groupName, nodeName, nodeHostPort);
        replicationConfig.setHelperHosts(helperHostPort);
        replicationConfig.setDesignatedPrimary(designatedPrimary);

        for (Map.Entry<String, String> configItem : replicationEnvironmentSettings.entrySet())
        {
            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Setting ReplicationConfig key " + configItem.getKey() + " to '" + configItem.getValue() + "'");
            }
            replicationConfig.setConfigParam(configItem.getKey(), configItem.getValue());
        }

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setExceptionListener(new LoggingAsyncExceptionListener());
        envConfig.setDurability(durability);

        for (Map.Entry<String, String> configItem : environmentSettings.entrySet())
        {
            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Setting EnvironmentConfig key " + configItem.getKey() + " to '" + configItem.getValue() + "'");
            }
            envConfig.setConfigParam(configItem.getKey(), configItem.getValue());
        }

        ReplicatedEnvironment environment = null;
        try
        {
            environment = new ReplicatedEnvironment(new File(environmentPath), replicationConfig, envConfig);
        }
        catch (final InsufficientLogException ile)
        {
            LOGGER.info("InsufficientLogException thrown and so full network restore required", ile);
            NetworkRestore restore = new NetworkRestore();
            NetworkRestoreConfig config = new NetworkRestoreConfig();
            config.setRetainLogFiles(false);
            restore.execute(ile, config);
            environment = new ReplicatedEnvironment(new File(environmentPath), replicationConfig, envConfig);
        }
        return environment;
    }

    private class LoggingAsyncExceptionListener implements ExceptionListener
    {
        @Override
        public void exceptionThrown(ExceptionEvent event)
        {
            LOGGER.error("Asynchronous exception thrown by BDB thread '" + event.getThreadName() + "'", event.getException());
        }
    }

    public static enum State
    {
        INITIAL,
        OPENING,
        OPEN,
        RESTARTING,
        CLOSING,
        CLOSED
    }
}
