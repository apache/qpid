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

import static org.apache.qpid.server.model.ReplicationNode.COALESCING_SYNC;
import static org.apache.qpid.server.model.ReplicationNode.DESIGNATED_PRIMARY;
import static org.apache.qpid.server.model.ReplicationNode.DURABILITY;
import static org.apache.qpid.server.model.ReplicationNode.GROUP_NAME;
import static org.apache.qpid.server.model.ReplicationNode.HELPER_HOST_PORT;
import static org.apache.qpid.server.model.ReplicationNode.HOST_PORT;
import static org.apache.qpid.server.model.ReplicationNode.PARAMETERS;
import static org.apache.qpid.server.model.ReplicationNode.REPLICATION_PARAMETERS;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.replication.ReplicationGroupListener;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.berkeleydb.replication.DatabasePinger;
import org.apache.qpid.server.store.berkeleydb.replication.RemoteReplicationNode;
import org.apache.qpid.server.store.berkeleydb.replication.RemoteReplicationNodeFactory;
import org.apache.qpid.server.util.DaemonThreadFactory;

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
    public static final String GROUP_CHECK_INTERVAL_PROPERTY_NAME = "qpid.bdb.ha.group_check_interval";

    private static final Logger LOGGER = Logger.getLogger(ReplicatedEnvironmentFacade.class);
    private static final long DEFAULT_GROUP_CHECK_INTERVAL = 1000l;
    private static final long GROUP_CHECK_INTERVAL = Long.getLong(GROUP_CHECK_INTERVAL_PROPERTY_NAME, DEFAULT_GROUP_CHECK_INTERVAL);

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

    // TODO: JMX will change to observe the model, at that point these names will disappear
    public static final String GRP_MEM_COL_NODE_HOST_PORT = "NodeHostPort";
    public static final String GRP_MEM_COL_NODE_NAME = "NodeName";

    private final String _prettyGroupNodeName;
    private final String _groupName;
    private final String _nodeName;
    private final String _nodeHostPort;
    private final String _helperHostPort;
    private final Durability _durability;
    private final boolean _designatedPrimary;
    private final boolean _coalescingSync;
    private final String _environmentPath;
    private final Map<String, String> _environmentParameters;
    private final Map<String, String> _replicationEnvironmentParameters;
    private final String _name;
    private final ExecutorService _restartEnvironmentExecutor;
    private final ScheduledExecutorService _groupChangeExecutor;
    private final AtomicReference<State> _state = new AtomicReference<State>(State.INITIAL);
    private final ConcurrentMap<String, DatabaseHolder> _databases = new ConcurrentHashMap<String, DatabaseHolder>();
    private final ConcurrentMap<String, RemoteReplicationNode> _remoteReplicationNodes = new ConcurrentHashMap<String, RemoteReplicationNode>();
    private final RemoteReplicationNodeFactory _remoteReplicationNodeFactory;

    private final AtomicReference<ReplicationGroupListener> _replicationGroupListener = new AtomicReference<ReplicationGroupListener>();
    private final AtomicReference<StateChangeListener> _stateChangeListener = new AtomicReference<StateChangeListener>();
    private volatile CommitThreadWrapper _commitThreadWrapper;
    private volatile ReplicatedEnvironment _environment;
    private long _joinTime;
    private String _lastKnownReplicationTransactionId;

    @SuppressWarnings("unchecked")
    public ReplicatedEnvironmentFacade(String virtualHostName, String environmentPath,
            org.apache.qpid.server.model.ReplicationNode replicationNode,
            RemoteReplicationNodeFactory remoteReplicationNodeFactory)
    {
         _name = virtualHostName;
        _environmentPath = environmentPath;
        _groupName = (String)replicationNode.getAttribute(GROUP_NAME);
        _nodeName = replicationNode.getName();
        _nodeHostPort = (String)replicationNode.getAttribute(HOST_PORT);;
        _helperHostPort = (String)replicationNode.getAttribute(HELPER_HOST_PORT);
        _durability = Durability.parse((String)replicationNode.getAttribute(DURABILITY));
        _designatedPrimary = (Boolean)replicationNode.getAttribute(DESIGNATED_PRIMARY);
        _coalescingSync = (Boolean)replicationNode.getAttribute(COALESCING_SYNC);
        _environmentParameters = (Map<String, String>)replicationNode.getAttribute(PARAMETERS);
        _replicationEnvironmentParameters = (Map<String, String>)replicationNode.getAttribute(REPLICATION_PARAMETERS);
        _prettyGroupNodeName = _groupName + ":" + _nodeName;

        _restartEnvironmentExecutor = Executors.newFixedThreadPool(1, new DaemonThreadFactory("Environment-Starter:" + _prettyGroupNodeName));
        _groupChangeExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 1, new DaemonThreadFactory("Group-Change-Learner:" + _prettyGroupNodeName));

        _remoteReplicationNodeFactory = remoteReplicationNodeFactory;
        _state.set(State.OPENING);
        _groupChangeExecutor.scheduleWithFixedDelay(new GroupChangeLearner(), 0, GROUP_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
        _groupChangeExecutor.schedule(new RemoteNodeStateLearner(), _remoteReplicationNodeFactory.getRemoteNodeMonitorInterval(), TimeUnit.MILLISECONDS);

        // create environment in a separate thread to avoid renaming of the current thread by JE
        Future<ReplicatedEnvironment> environmentFuture = _restartEnvironmentExecutor.submit(new Callable<ReplicatedEnvironment>(){
            @Override
            public ReplicatedEnvironment call() throws Exception
            {
                String originalThreadName = Thread.currentThread().getName();
                try
                {
                    return createEnvironment();
                }
                finally
                {
                    Thread.currentThread().setName(originalThreadName);
                }
            }});

        // TODO: evaluate the future timeout from JE ENVIRONMENT_SETUP
        try
        {
            _environment = environmentFuture.get(15 * 2, TimeUnit.MINUTES);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException("Unexpected exception on environment creation", e.getCause());
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException("JE environment has not been created in due time");
        }
        populateExistingRemoteReplicationNodes();
        _commitThreadWrapper = startCommitThread(_name, _environment);
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
                LOGGER.debug("Closing replicated environment facade for " + _prettyGroupNodeName);
                _restartEnvironmentExecutor.shutdown();
                _groupChangeExecutor.shutdown();
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
    public AMQStoreException handleDatabaseException(String contextMessage, final DatabaseException dbe)
    {
        boolean restart = (dbe instanceof InsufficientReplicasException || dbe instanceof InsufficientReplicasException);
        if (restart)
        {
            if (_state.compareAndSet(State.OPEN, State.RESTARTING))
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Environment restarting due to exception " + dbe.getMessage(), dbe);
                }
                _restartEnvironmentExecutor.execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            restartEnvironment(dbe);
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
        return new AMQStoreException(contextMessage, dbe);
    }

    @Override
    public void openDatabases(DatabaseConfig dbConfig, String... databaseNames)
    {
        if (_state.get() != State.OPEN)
        {
            throw new IllegalStateException("Environment facade is not in opened state");
        }

        if (!_environment.isValid())
        {
            throw new IllegalStateException("Environment is not valid");
        }

        if (_environment.getState() != ReplicatedEnvironment.State.MASTER)
        {
            throw new IllegalStateException("Databases can only be opened on Master node");
        }

        for (String databaseName : databaseNames)
        {
            _databases.put(databaseName, new DatabaseHolder(dbConfig));
        }
        for (String databaseName : databaseNames)
        {
            DatabaseHolder holder = _databases.get(databaseName);
            openDatabaseInternally(databaseName, holder);
        }
    }

    private void openDatabaseInternally(String databaseName, DatabaseHolder holder)
    {
        LOGGER.debug("Opening database " + databaseName + " on " + _prettyGroupNodeName);
        Database database = _environment.openDatabase(null, databaseName, holder.getConfig());
        holder.setDatabase(database);
    }

    @Override
    public Database getOpenDatabase(String name)
    {
        if (_state.get() != State.OPEN)
        {
            throw new IllegalStateException("Environment facade is not in opened state");
        }

        if (!_environment.isValid())
        {
            throw new IllegalStateException("Environment is not valid");
        }
        DatabaseHolder databaseHolder = _databases.get(name);
        if (databaseHolder == null)
        {
            throw new IllegalArgumentException("Database with name '" + name + "' has never been requested to be opened");
        }
        Database database = databaseHolder.getDatabase();
        if (database == null)
        {
            throw new IllegalArgumentException("Database with name '" + name + "' has not been opened");
        }
        return database;
    }

    @Override
    public void stateChange(final StateChangeEvent stateChangeEvent)
    {
        _groupChangeExecutor.submit(new Runnable()
        {
            @Override
            public void run()
            {
                stateChanged(stateChangeEvent);
            }
        });
    }

    private void stateChanged(StateChangeEvent stateChangeEvent)
    {
        ReplicatedEnvironment.State state = stateChangeEvent.getState();
        LOGGER.info("The node '" + _prettyGroupNodeName + "' state is " + state);
        if (state == ReplicatedEnvironment.State.REPLICA || state == ReplicatedEnvironment.State.MASTER)
        {
            if (_state.compareAndSet(State.OPENING, State.OPEN) || _state.compareAndSet(State.RESTARTING, State.OPEN))
            {
                LOGGER.info("The environment facade is in open state for node " + _prettyGroupNodeName);
                _joinTime = System.currentTimeMillis();
            }
        }

        if (state == ReplicatedEnvironment.State.MASTER)
        {
            reopenDatabases();
            _commitThreadWrapper = startCommitThread(_name, _environment);
            StateChangeListener listener = _stateChangeListener.get();
            LOGGER.debug("Application state change listener " + listener);
            if (listener != null)
            {
                listener.stateChange(stateChangeEvent);
            }
        }
        else
        {
            if (_state.get() != State.CLOSING && _state.get() != State.CLOSED)
            {
                StateChangeListener listener = _stateChangeListener.get();
                if (listener != null)
                {
                    listener.stateChange(stateChangeEvent);
                }
            }
        }
    }

    private void reopenDatabases()
    {
        DatabaseConfig pingDbConfig = new DatabaseConfig();
        pingDbConfig.setTransactional(true);
        pingDbConfig.setAllowCreate(true);

        _databases.putIfAbsent(DatabasePinger.PING_DATABASE_NAME, new DatabaseHolder(pingDbConfig));

        for (Map.Entry<String, DatabaseHolder> entry : _databases.entrySet())
        {
            openDatabaseInternally(entry.getKey(), entry.getValue());
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

    public String getHostPort()
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

    public int getQuorumOverride()
    {
        return _environment.getRepMutableConfig().getElectableGroupSizeOverride();
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
                LOGGER.info("Node " + _prettyGroupNodeName + " successfully set as designated primary for group");
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

    public int getPriority()
    {
        ReplicationMutableConfig repConfig = _environment.getRepMutableConfig();
        return repConfig.getNodePriority();
    }

    public int getElectableGroupSizeOverride()
    {
        ReplicationMutableConfig repConfig = _environment.getRepMutableConfig();
        return repConfig.getElectableGroupSizeOverride();
    }

    public long getJoinTime()
    {
        return _joinTime ;
    }

    public String getLastKnownReplicationTransactionId()
    {
        return _lastKnownReplicationTransactionId;
    }

    public ReplicatedEnvironment getEnvironment()
    {
        return _environment;
    }

    public State getFacadeState()
    {
        return _state.get();
    }

    public void setReplicationGroupListener(ReplicationGroupListener replicationGroupListener)
    {
        if (_replicationGroupListener.compareAndSet(null, replicationGroupListener))
        {
            notifyExistingRemoteReplicationNodes(replicationGroupListener);
        }
        else
        {
            throw new IllegalStateException("ReplicationGroupListener is already set on " + _prettyGroupNodeName);
        }
    }

    public void setStateChangeListener(StateChangeListener stateChangeListener)
    {
        if (_stateChangeListener.compareAndSet(null, stateChangeListener))
        {
            _environment.setStateChangeListener(this);
        }
        else
        {
            throw new IllegalStateException("StateChangeListener is already set on " + _prettyGroupNodeName);
        }
    }

    private void populateExistingRemoteReplicationNodes()
    {
        ReplicationGroup group = _environment.getGroup();
        Set<ReplicationNode> nodes = new HashSet<ReplicationNode>(group.getElectableNodes());
        String localNodeName = getNodeName();

        for (ReplicationNode replicationNode : nodes)
        {
            String discoveredNodeName = replicationNode.getName();
            if (!discoveredNodeName.equals(localNodeName))
            {
                RemoteReplicationNode remoteNode = _remoteReplicationNodeFactory.create(replicationNode, group.getName());

                _remoteReplicationNodes.put(replicationNode.getName(), remoteNode);
            }
        }
     }

    private void notifyExistingRemoteReplicationNodes(ReplicationGroupListener listener)
    {
        for (org.apache.qpid.server.model.ReplicationNode value : _remoteReplicationNodes.values())
        {
            listener.onReplicationNodeRecovered(value);
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

    private CommitThreadWrapper startCommitThread(String name, Environment environment)
    {
        CommitThreadWrapper commitThreadWrapper = null;
        if (_coalescingSync)
        {
            commitThreadWrapper = new CommitThreadWrapper("Commit-Thread-" + name, environment);
            commitThreadWrapper.startCommitThread();
        }
        return commitThreadWrapper;
    }

    private void stopCommitThread(RuntimeException dbe)
    {
        if (_coalescingSync)
        {
            try
            {
                _commitThreadWrapper.stopCommitThread(dbe);
            }
            catch (InterruptedException e)
            {
                LOGGER.warn("Stopping of commit thread is interrupted", e);
                Thread.interrupted();
            }
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

    private void restartEnvironment(DatabaseException dbe) throws AMQStoreException
    {
        LOGGER.info("Restarting environment");

        stopCommitThread(dbe);

        closeEnvironmentSafely();

        _environment = createEnvironment();

        if (_stateChangeListener.get() != null)
        {
            _environment.setStateChangeListener(this);
        }

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
        LOGGER.debug("Closing databases " + _databases);
        for (Map.Entry<String, DatabaseHolder> entry : _databases.entrySet())
        {
            DatabaseHolder databaseHolder = entry.getValue();
            Database database = databaseHolder.getDatabase();
            if (database != null)
            {
                try
                {
                    LOGGER.debug("Closing database " + entry.getKey() + " on " + _prettyGroupNodeName);
                    database.close();
                }
                catch(RuntimeException e)
                {
                    LOGGER.error("Failed to close database on " + _prettyGroupNodeName, e);
                    if (firstThrownException == null)
                    {
                        firstThrownException = e;
                    }
                }
                finally
                {
                    databaseHolder.setDatabase(null);
                }
            }
        }
        if (firstThrownException != null)
        {
            throw firstThrownException;
        }
    }

    private ReplicatedEnvironment createEnvironment()
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Creating environment");
            LOGGER.info("Environment path " + _environmentPath);
            LOGGER.info("Group name " + _groupName);
            LOGGER.info("Node name " + _nodeName);
            LOGGER.info("Node host port " + _nodeHostPort);
            LOGGER.info("Helper host port " + _helperHostPort);
            LOGGER.info("Durability " + _durability);
            LOGGER.info("Coalescing sync " + _coalescingSync);
            LOGGER.info("Designated primary (applicable to 2 node case only) " + _designatedPrimary);
        }

        Map<String, String> replicationEnvironmentSettings = new HashMap<String, String>(REPCONFIG_DEFAULTS);
        if (_replicationEnvironmentParameters != null && !_replicationEnvironmentParameters.isEmpty())
        {
            replicationEnvironmentSettings.putAll(_replicationEnvironmentParameters);
        }
        Map<String, String> environmentSettings = new HashMap<String, String>(EnvironmentFacade.ENVCONFIG_DEFAULTS);
        if (_environmentParameters != null && !_environmentParameters.isEmpty())
        {
            environmentSettings.putAll(_environmentParameters);
        }

        final ReplicationConfig replicationConfig = new ReplicationConfig(_groupName, _nodeName, _nodeHostPort);
        replicationConfig.setHelperHosts(_helperHostPort);
        replicationConfig.setDesignatedPrimary(_designatedPrimary);

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
        envConfig.setDurability(_durability);

        for (Map.Entry<String, String> configItem : environmentSettings.entrySet())
        {
            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Setting EnvironmentConfig key " + configItem.getKey() + " to '" + configItem.getValue() + "'");
            }
            envConfig.setConfigParam(configItem.getKey(), configItem.getValue());
        }

        ReplicatedEnvironment environment = null;
        File environmentPathFile = new File(_environmentPath);
        try
        {
            environment = new ReplicatedEnvironment(environmentPathFile, replicationConfig, envConfig);
        }
        catch (final InsufficientLogException ile)
        {
            LOGGER.info("InsufficientLogException thrown and so full network restore required", ile);
            NetworkRestore restore = new NetworkRestore();
            NetworkRestoreConfig config = new NetworkRestoreConfig();
            config.setRetainLogFiles(false);
            restore.execute(ile, config);
            environment = new ReplicatedEnvironment(environmentPathFile, replicationConfig, envConfig);
        }
        return environment;
    }

    private final class GroupChangeLearner implements Runnable
    {
        @Override
        public void run()
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Checking for changes in the group " + _groupName);
            }

            ReplicatedEnvironment env = _environment;
            ReplicationGroupListener replicationGroupListener = _replicationGroupListener.get();
            if (env != null && env.isValid())
            {
                ReplicationGroup group = env.getGroup();
                Set<ReplicationNode> nodes = new HashSet<ReplicationNode>(group.getElectableNodes());
                String localNodeName = getNodeName();

                Map<String, org.apache.qpid.server.model.ReplicationNode> removalMap = new HashMap<String, org.apache.qpid.server.model.ReplicationNode>(_remoteReplicationNodes);
                for (ReplicationNode replicationNode : nodes)
                {
                    String discoveredNodeName = replicationNode.getName();
                    if (!discoveredNodeName.equals(localNodeName))
                    {
                        if (!_remoteReplicationNodes.containsKey(discoveredNodeName))
                        {
                            if (LOGGER.isDebugEnabled())
                            {
                                LOGGER.debug("Remote replication node added '" + replicationNode + "' to '" + _groupName + "'");
                            }

                            RemoteReplicationNode remoteNode = _remoteReplicationNodeFactory.create(replicationNode, group.getName());

                            _remoteReplicationNodes.put(discoveredNodeName, remoteNode);
                            if (replicationGroupListener != null)
                            {
                                replicationGroupListener.onReplicationNodeAddedToGroup(remoteNode);
                            }
                        }
                        else
                        {
                            removalMap.remove(discoveredNodeName);
                        }
                    }
                }

                if (!removalMap.isEmpty())
                {
                    for (Map.Entry<String, org.apache.qpid.server.model.ReplicationNode> replicationNodeEntry : removalMap.entrySet())
                    {
                        String replicationNodeName = replicationNodeEntry.getKey();
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("Remote replication node removed '" + replicationNodeName + "' from '" + _groupName + "'");
                        }
                        _remoteReplicationNodes.remove(replicationNodeName);
                        if (replicationGroupListener != null)
                        {
                            replicationGroupListener.onReplicationNodeRemovedFromGroup(replicationNodeEntry.getValue());
                        }
                    }
                }
            }
        }
    }

    //TODO: move the class into external class
    private class RemoteNodeStateLearner implements Callable<Void>
    {
        private Map<String, String> _previousGroupState = Collections.emptyMap();
        @Override
        public Void call()
        {
            long remoteNodeMonitorInterval = _remoteReplicationNodeFactory.getRemoteNodeMonitorInterval();
            try
            {
                Set<Future<Void>> futures = new HashSet<Future<Void>>();
                for (final RemoteReplicationNode node : _remoteReplicationNodes.values())
                {
                    Future<Void> future = _groupChangeExecutor.submit(new Callable<Void>()
                    {
                        @Override
                        public Void call()
                        {
                            node.updateNodeState();
                            return null;
                        }
                    });
                    futures.add(future);
                }

                for (Future<Void> future : futures)
                {
                    try
                    {
                        future.get(remoteNodeMonitorInterval, TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                    catch (ExecutionException e)
                    {
                        LOGGER.warn("Cannot update node state for group " + _groupName, e.getCause());
                    }
                    catch (TimeoutException e)
                    {
                        LOGGER.warn("Timeout whilst updating node state for group " + _groupName);
                        future.cancel(true);
                    }
                }

                if (ReplicatedEnvironment.State.MASTER == _environment.getState())
                {
                    Map<String, String> currentGroupState = new HashMap<String, String>();
                    for (final RemoteReplicationNode node : _remoteReplicationNodes.values())
                    {
                        currentGroupState.put(node.getName(), (String)node.getAttribute(org.apache.qpid.server.model.ReplicationNode.ROLE));
                    }
                    boolean stateChanged = !_previousGroupState.equals(currentGroupState);
                    _previousGroupState = currentGroupState;
                    if (stateChanged && State.OPEN == _state.get())
                    {
                        new DatabasePinger().pingDb(ReplicatedEnvironmentFacade.this);
                    }
                }
            }
            finally
            {
                _groupChangeExecutor.schedule(this, remoteNodeMonitorInterval, TimeUnit.MILLISECONDS);
            }
            return null;
        }
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
        INITIAL,  // TODO unused remove
        OPENING,
        OPEN,
        RESTARTING,
        CLOSING,
        CLOSED
    }

    private static class DatabaseHolder
    {
        private final DatabaseConfig _config;
        private Database _database;

        public DatabaseHolder(DatabaseConfig config)
        {
            _config = config;
        }

        public Database getDatabase()
        {
            return _database;
        }

        public void setDatabase(Database database)
        {
            _database = database;
        }

        public DatabaseConfig getConfig()
        {
            return _config;
        }

        @Override
        public String toString()
        {
            return "DatabaseHolder [_config=" + _config + ", _database=" + _database + "]";
        }

    }
}
