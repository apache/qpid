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
package org.apache.qpid.server.store.berkeleydb.replication;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.rep.AppStateMonitor;
import com.sleepycat.je.rep.InsufficientAcksException;
import com.sleepycat.je.rep.InsufficientLogException;
import com.sleepycat.je.rep.InsufficientReplicasException;
import com.sleepycat.je.rep.NetworkRestore;
import com.sleepycat.je.rep.NetworkRestoreConfig;
import com.sleepycat.je.rep.NodeState;
import com.sleepycat.je.rep.NodeType;
import com.sleepycat.je.rep.RepInternal;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;
import com.sleepycat.je.rep.ReplicationGroup;
import com.sleepycat.je.rep.ReplicationMutableConfig;
import com.sleepycat.je.rep.ReplicationNode;
import com.sleepycat.je.rep.RestartRequiredException;
import com.sleepycat.je.rep.StateChangeEvent;
import com.sleepycat.je.rep.StateChangeListener;
import com.sleepycat.je.rep.util.DbPing;
import com.sleepycat.je.rep.util.ReplicationGroupAdmin;
import com.sleepycat.je.rep.utilint.HostPortPair;
import com.sleepycat.je.rep.utilint.ServiceDispatcher.ServiceConnectFailedException;
import com.sleepycat.je.rep.vlsn.VLSNRange;
import com.sleepycat.je.utilint.PropUtil;
import com.sleepycat.je.utilint.VLSN;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.berkeleydb.CoalescingCommiter;
import org.apache.qpid.server.store.berkeleydb.EnvHomeRegistry;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.LoggingAsyncExceptionListener;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.DaemonThreadFactory;

public class ReplicatedEnvironmentFacade implements EnvironmentFacade, StateChangeListener
{
    public static final String MASTER_TRANSFER_TIMEOUT_PROPERTY_NAME = "qpid.bdb.ha.master_transfer_interval";
    public static final String DB_PING_SOCKET_TIMEOUT_PROPERTY_NAME = "qpid.bdb.ha.db_ping_socket_timeout";
    public static final String REMOTE_NODE_MONITOR_INTERVAL_PROPERTY_NAME = "qpid.bdb.ha.remote_node_monitor_interval";
    public static final String ENVIRONMENT_RESTART_RETRY_LIMIT_PROPERTY_NAME = "qpid.bdb.ha.environment_restart_retry_limit";
    public static final String EXECUTOR_SHUTDOWN_TIMEOUT_PROPERTY_NAME = "qpid.bdb.ha.executor_shutdown_timeout";

    private static final Logger LOGGER = Logger.getLogger(ReplicatedEnvironmentFacade.class);

    private static final int DEFAULT_MASTER_TRANSFER_TIMEOUT = 1000 * 60;
    private static final int DEFAULT_DB_PING_SOCKET_TIMEOUT = 1000;
    private static final int DEFAULT_REMOTE_NODE_MONITOR_INTERVAL = 1000;
    private static final int DEFAULT_ENVIRONMENT_RESTART_RETRY_LIMIT = 3;
    private static final int DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT = 5000;

    /** Length of time allowed for a master transfer to complete before the operation will timeout */
    private final int _masterTransferTimeout;

    /**
     * Qpid monitors the health of the other nodes in the group.  This controls the length of time
     * between each scan of the all the nodes.
     */
    private final int _remoteNodeMonitorInterval;

    /**
     * Qpid uses DbPing to establish the health of other nodes in the group.  This controls the socket timeout
     * (so_timeout) used on the socket underlying DbPing.
     */
    private final int _dbPingSocketTimeout;

    /**
     * If the environment creation fails, Qpid will automatically retry.  This controls the number
     * of times recreation will be attempted.
     */
    private final int _environmentRestartRetryLimit;

    /**
     * Length of time for executors used by facade to shutdown gracefully
     */
    private final int _executorShutdownTimeout;

    static final SyncPolicy LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY = SyncPolicy.SYNC;
    static final SyncPolicy REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY = SyncPolicy.NO_SYNC;
    public static final ReplicaAckPolicy REPLICA_REPLICA_ACKNOWLEDGMENT_POLICY = ReplicaAckPolicy.SIMPLE_MAJORITY;

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
         * Parameter decreased as the 10 h default may cause user confusion.
         */
        put(ReplicationConfig.ENV_SETUP_TIMEOUT, "180 s");
        /**
         * Parameter changed from default (off) to allow the Environment to start in the
         * UNKNOWN state when the majority is not available.
         */
        put(ReplicationConfig.ENV_UNKNOWN_STATE_TIMEOUT, "5 s");
        /**
         * Parameter changed from default true so we adopt immediately adopt the new behaviour early. False
         * is scheduled to become default after JE 5.1.
         */
        put(ReplicationConfig.PROTOCOL_OLD_STRING_ENCODING, Boolean.FALSE.toString());
        /**
         * Parameter decreased as a default 5min interval may lead to bigger data losses on Node
         * with NO_SYN durability in case if such Node crushes.
         */
        put(ReplicationConfig.LOG_FLUSH_TASK_INTERVAL, "1 min");
    }});

    public static final String PERMITTED_NODE_LIST = "permittedNodes";

    private final ReplicatedEnvironmentConfiguration _configuration;
    private final String _prettyGroupNodeName;
    private final File _environmentDirectory;

    private final ExecutorService _environmentJobExecutor;
    private final ScheduledExecutorService _groupChangeExecutor;
    private final AtomicReference<State> _state = new AtomicReference<State>(State.OPENING);
    private final ConcurrentMap<String, ReplicationNode> _remoteReplicationNodes = new ConcurrentHashMap<String, ReplicationNode>();
    private final AtomicReference<ReplicationGroupListener> _replicationGroupListener = new AtomicReference<ReplicationGroupListener>();
    private final AtomicReference<StateChangeListener> _stateChangeListener = new AtomicReference<StateChangeListener>();
    private final Durability _defaultDurability;
    private final ConcurrentMap<String, Database> _cachedDatabases = new ConcurrentHashMap<>();
    private final ConcurrentMap<DatabaseEntry, Sequence> _cachedSequences = new ConcurrentHashMap<>();
    private final Set<String> _permittedNodes = new CopyOnWriteArraySet<String>();

    private volatile Durability _realMessageStoreDurability = null;
    private volatile Durability _messageStoreDurability;
    private volatile CoalescingCommiter _coalescingCommiter = null;
    private volatile ReplicatedEnvironment _environment;
    private volatile long _joinTime;
    private volatile ReplicatedEnvironment.State _lastKnownEnvironmentState;
    private volatile long _envSetupTimeoutMillis;

    public ReplicatedEnvironmentFacade(ReplicatedEnvironmentConfiguration configuration)
    {
        _environmentDirectory = new File(configuration.getStorePath());
        if (!_environmentDirectory.exists())
        {
            if (!_environmentDirectory.mkdirs())
            {
                throw new IllegalArgumentException("Environment path " + _environmentDirectory + " could not be read or created. "
                                                   + "Ensure the path is correct and that the permissions are correct.");
            }
        }
        else
        {
            LOGGER.debug("Environment at path " + _environmentDirectory + " already exists.");
        }

        _configuration = configuration;

        _masterTransferTimeout = configuration.getFacadeParameter(MASTER_TRANSFER_TIMEOUT_PROPERTY_NAME, DEFAULT_MASTER_TRANSFER_TIMEOUT);
        _dbPingSocketTimeout = configuration.getFacadeParameter(DB_PING_SOCKET_TIMEOUT_PROPERTY_NAME, DEFAULT_DB_PING_SOCKET_TIMEOUT);
        _remoteNodeMonitorInterval = configuration.getFacadeParameter(REMOTE_NODE_MONITOR_INTERVAL_PROPERTY_NAME, DEFAULT_REMOTE_NODE_MONITOR_INTERVAL);
        _environmentRestartRetryLimit = configuration.getFacadeParameter(ENVIRONMENT_RESTART_RETRY_LIMIT_PROPERTY_NAME, DEFAULT_ENVIRONMENT_RESTART_RETRY_LIMIT);
        _executorShutdownTimeout = configuration.getFacadeParameter(EXECUTOR_SHUTDOWN_TIMEOUT_PROPERTY_NAME, DEFAULT_EXECUTOR_SHUTDOWN_TIMEOUT);

        _defaultDurability = new Durability(LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY, REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY, REPLICA_REPLICA_ACKNOWLEDGMENT_POLICY);
        _prettyGroupNodeName = _configuration.getGroupName() + ":" + _configuration.getName();

        // we relay on this executor being single-threaded as we need to restart and mutate the environment in one thread
        _environmentJobExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("Environment-" + _prettyGroupNodeName));
        _groupChangeExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 1, new DaemonThreadFactory("Group-Change-Learner:" + _prettyGroupNodeName));

        // create environment in a separate thread to avoid renaming of the current thread by JE
        EnvHomeRegistry.getInstance().registerHome(_environmentDirectory);
        boolean success = false;
        try
        {
            _environment = createEnvironment(true);
            success = true;
        }
        finally
        {
            if (!success)
            {
                EnvHomeRegistry.getInstance().deregisterHome(_environmentDirectory);
            }
        }
        populateExistingRemoteReplicationNodes();
        _groupChangeExecutor.submit(new RemoteNodeStateLearner());
    }

    @Override
    public Transaction beginTransaction()
    {
        if (_messageStoreDurability == null)
        {
            throw new IllegalStateException("Message store durability is not set");
        }

        try
        {
            TransactionConfig transactionConfig = new TransactionConfig();
            transactionConfig.setDurability(getRealMessageStoreDurability());
            return _environment.beginTransaction(null, transactionConfig);
        }
        catch(DatabaseException e)
        {
            throw handleDatabaseException("Failure to start transaction", e);
        }
    }

    @Override
    public StoreFuture commit(final Transaction tx, boolean syncCommit)
    {
        try
        {
            // Using commit() instead of commitNoSync() for the HA store to allow
            // the HA durability configuration to influence resulting behaviour.
            tx.commit(_realMessageStoreDurability);
        }
        catch (DatabaseException de)
        {
            throw handleDatabaseException("Got DatabaseException on commit, closing environment", de);
        }

        if (_coalescingCommiter != null && _realMessageStoreDurability.getLocalSync() == SyncPolicy.NO_SYNC
                && _messageStoreDurability.getLocalSync() == SyncPolicy.SYNC)
        {
            return _coalescingCommiter.commit(tx, syncCommit);
        }
        return StoreFuture.IMMEDIATE_FUTURE;
    }

    @Override
    public void close()
    {
        if (_state.compareAndSet(State.OPENING, State.CLOSING) ||
            _state.compareAndSet(State.OPEN, State.CLOSING) ||
            _state.compareAndSet(State.RESTARTING, State.CLOSING) )
        {
            try
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("Closing replicated environment facade for " + _prettyGroupNodeName + " current state is " + _state.get());
                }

                long timeout = Math.max(_executorShutdownTimeout, _envSetupTimeoutMillis);
                shutdownAndAwaitExecutorService(_environmentJobExecutor,
                                                timeout,
                                                TimeUnit.MILLISECONDS);
                shutdownAndAwaitExecutorService(_groupChangeExecutor, _executorShutdownTimeout, TimeUnit.MILLISECONDS);

                try
                {
                    if (_coalescingCommiter != null)
                    {
                        _coalescingCommiter.stop();
                    }
                    closeSequences();
                    closeDatabases();
                }
                finally
                {
                    try
                    {
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("Closing replicated environment");
                        }

                        closeEnvironment();
                    }
                    finally
                    {
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("Deregistering environment home " + _environmentDirectory);
                        }

                        EnvHomeRegistry.getInstance().deregisterHome(_environmentDirectory);
                    }
                }
            }
            finally
            {
                _state.compareAndSet(State.CLOSING, State.CLOSED);
            }
        }
    }

    private void shutdownAndAwaitExecutorService(ExecutorService executorService, long executorShutdownTimeout, TimeUnit timeUnit)
    {
        executorService.shutdown();
        try
        {
            boolean wasShutdown = executorService.awaitTermination(executorShutdownTimeout, timeUnit);
            if (!wasShutdown)
            {
                LOGGER.warn("Executor service " + executorService +
                            " did not shutdown within allowed time period " + _executorShutdownTimeout
                            + " " + timeUnit + ", ignoring");
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            LOGGER.warn("Shutdown of executor service " + executorService + " was interrupted");
        }
    }

    @Override
    public DatabaseException handleDatabaseException(String contextMessage, final DatabaseException dbe)
    {
        boolean noMajority = dbe instanceof InsufficientReplicasException || dbe instanceof InsufficientAcksException;

        if (noMajority)
        {
            ReplicationGroupListener listener = _replicationGroupListener.get();
            if (listener != null)
            {
                listener.onNoMajority();
            }
        }

        boolean restart = (noMajority || dbe instanceof RestartRequiredException);
        if (restart)
        {
            tryToRestartEnvironment(dbe);
            throw new ConnectionScopedRuntimeException(noMajority ? "Required number of nodes not reachable" : "Underlying JE environment is being restarted", dbe);
        }
        return dbe;
    }

    private void tryToRestartEnvironment(final DatabaseException dbe)
    {
        if (_state.compareAndSet(State.OPEN, State.RESTARTING))
        {
            if (dbe != null && LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Environment restarting due to exception " + dbe.getMessage(), dbe);
            }

            _environmentJobExecutor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    int attemptNumber = 1;
                    boolean restarted = false;
                    while(_state.get() == State.RESTARTING && attemptNumber <= _environmentRestartRetryLimit)
                    {
                        try
                        {
                            restartEnvironment();
                            restarted = true;
                            break;
                        }
                        catch(EnvironmentFailureException e)
                        {
                            LOGGER.warn("Failure whilst trying to restart environment (attempt number "
                                    + attemptNumber + " of " + _environmentRestartRetryLimit + ")", e);
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Fatal failure whilst trying to restart environment", e);
                            break;
                        }
                        attemptNumber++;
                    }

                    if (!restarted)
                    {
                        LOGGER.warn("Failed to restart environment.");
                    }
                }
            });
        }
        else
        {
            LOGGER.info("Cannot restart environment because of facade state: " + _state.get());
        }
    }

    @Override
    public Database openDatabase(String name, DatabaseConfig databaseConfig)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("openDatabase " + name + " for " + _prettyGroupNodeName);
        }
        if (_state.get() != State.OPEN)
        {
            throw new IllegalStateException("Environment facade is not in opened state");
        }

        if (!_environment.isValid())
        {
            throw new IllegalStateException("Environment is not valid");
        }

        Database cachedHandle = _cachedDatabases.get(name);
        if (cachedHandle == null)
        {
            Database handle = _environment.openDatabase(null, name, databaseConfig);
            Database existingHandle = _cachedDatabases.putIfAbsent(name, handle);
            if (existingHandle == null)
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("openDatabase " + name + " new handle");
                }

                cachedHandle = handle;
            }
            else
            {
                if (LOGGER.isDebugEnabled())
                {
                    LOGGER.debug("openDatabase " + name + " existing handle");
                }
                cachedHandle = existingHandle;
                handle.close();
            }
        }
        return cachedHandle;
    }


    @Override
    public Database clearDatabase(String name, DatabaseConfig databaseConfig)
    {
        closeDatabase(name);
        _environment.removeDatabase(null, name);
        return openDatabase(name, databaseConfig);
    }

    @Override
    public void closeDatabase(final String databaseName)
    {
        Database cachedHandle = _cachedDatabases.remove(databaseName);
        if (cachedHandle != null)
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Closing " + databaseName + " on " + _prettyGroupNodeName);
            }
            if (cachedHandle.getEnvironment().isValid())
            {
                cachedHandle.close();
            }
        }
    }
    @Override
    public Sequence openSequence(final Database database,
                                 final DatabaseEntry sequenceKey,
                                 final SequenceConfig sequenceConfig)
    {
        Sequence cachedSequence = _cachedSequences.get(sequenceKey);
        if (cachedSequence == null)
        {
            Sequence handle = database.openSequence(null, sequenceKey, sequenceConfig);
            Sequence existingHandle = _cachedSequences.putIfAbsent(sequenceKey, handle);
            if (existingHandle == null)
            {
                cachedSequence = handle;
            }
            else
            {
                cachedSequence = existingHandle;
                handle.close();
            }
        }
        return cachedSequence;
    }


    private void closeSequence(final DatabaseEntry sequenceKey)
    {
        Sequence cachedHandle = _cachedSequences.remove(sequenceKey);
        if (cachedHandle != null)
        {
            cachedHandle.close();
        }
    }

    @Override
    public String getStoreLocation()
    {
        return _environmentDirectory.getAbsolutePath();
    }

    @Override
    public void stateChange(final StateChangeEvent stateChangeEvent)
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("The node '" + _prettyGroupNodeName + "' state is " + stateChangeEvent.getState());
        }

        if (_state.get() != State.CLOSING && _state.get() != State.CLOSED)
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
        else
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Ignoring the state environment change event as the environment facade for node '"
                             + _prettyGroupNodeName
                             + "' is in state "
                             + _state.get());
            }
        }
    }

    private void stateChanged(StateChangeEvent stateChangeEvent)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Received BDB event, new BDB state " + stateChangeEvent.getState() + " Facade state : " + _state.get());
        }
        ReplicatedEnvironment.State state = stateChangeEvent.getState();

        if ( _state.get() != State.CLOSED && _state.get() != State.CLOSING)
        {
            if (state == ReplicatedEnvironment.State.REPLICA || state == ReplicatedEnvironment.State.MASTER)
            {
                if (_state.compareAndSet(State.OPENING, State.OPEN) || _state.compareAndSet(State.RESTARTING, State.OPEN))
                {
                    LOGGER.info("The environment facade is in open state for node " + _prettyGroupNodeName);
                    _joinTime = System.currentTimeMillis();
                }
            }

            StateChangeListener listener = _stateChangeListener.get();
            if (listener != null && (_state.get() == State.OPEN || _state.get() == State.RESTARTING))
            {
                listener.stateChange(stateChangeEvent);
            }

            if (_lastKnownEnvironmentState == ReplicatedEnvironment.State.MASTER && state == ReplicatedEnvironment.State.DETACHED && _state.get() == State.OPEN)
            {
                tryToRestartEnvironment(null);
            }
        }
        _lastKnownEnvironmentState = state;
    }

    public String getGroupName()
    {
        return (String)_configuration.getGroupName();
    }

    public String getNodeName()
    {
        return _configuration.getName();
    }

    public String getHostPort()
    {
        return (String)_configuration.getHostPort();
    }

    public String getHelperHostPort()
    {
        return (String)_configuration.getHelperHostPort();
    }

    Durability getRealMessageStoreDurability()
    {
        return _realMessageStoreDurability;
    }

    public Durability getMessageStoreDurability()
    {
        return _messageStoreDurability;
    }

    public boolean isCoalescingSync()
    {
        return _coalescingCommiter != null;
    }

    public String getNodeState()
    {
        if (_state.get() != State.OPEN)
        {
            return ReplicatedEnvironment.State.UNKNOWN.name();
        }
        ReplicatedEnvironment.State state = _environment.getState();
        return state.toString();
    }

    public boolean isDesignatedPrimary()
    {
        if (_state.get() != State.OPEN)
        {
            throw new IllegalStateException("Environment facade is not opened");
        }
        return _environment.getRepMutableConfig().getDesignatedPrimary();
    }

    public Future<Void> setDesignatedPrimary(final boolean isPrimary)
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Submitting a job to set designated primary on " + _prettyGroupNodeName + " to " + isPrimary);
        }

        return _environmentJobExecutor.submit(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                setDesignatedPrimaryInternal(isPrimary);
                return null;
            }
        });
    }

    void setDesignatedPrimaryInternal(final boolean isPrimary)
    {
        try
        {
            final ReplicationMutableConfig oldConfig = _environment.getRepMutableConfig();
            final ReplicationMutableConfig newConfig = oldConfig.setDesignatedPrimary(isPrimary);
            _environment.setRepMutableConfig(newConfig);

            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Node " + _prettyGroupNodeName + " successfully set designated primary : " + isPrimary);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Cannot set designated primary to " + isPrimary + " on node " + _prettyGroupNodeName, e);
        }
    }

    int getPriority()
    {
        if (_state.get() != State.OPEN)
        {
            throw new IllegalStateException("Environment facade is not opened");
        }
        ReplicationMutableConfig repConfig = _environment.getRepMutableConfig();
        return repConfig.getNodePriority();
    }

    public Future<Void> setPriority(final int priority)
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Submitting a job to set priority on " + _prettyGroupNodeName + " to " + priority);
        }

        return _environmentJobExecutor.submit(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                setPriorityInternal(priority);
                return null;
            }
        });
    }

    void setPriorityInternal(int priority)
    {
        try
        {
            final ReplicationMutableConfig oldConfig = _environment.getRepMutableConfig();
            final ReplicationMutableConfig newConfig = oldConfig.setNodePriority(priority);
            _environment.setRepMutableConfig(newConfig);

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Node " + _prettyGroupNodeName + " priority has been changed to " + priority);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Cannot set priority to " + priority + " on node " + _prettyGroupNodeName, e);
        }
    }

    int getElectableGroupSizeOverride()
    {
        if (_state.get() != State.OPEN)
        {
            throw new IllegalStateException("Environment facade is not opened");
        }
        ReplicationMutableConfig repConfig = _environment.getRepMutableConfig();
        return repConfig.getElectableGroupSizeOverride();
    }

    public Future<Void> setElectableGroupSizeOverride(final int electableGroupOverride)
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Submitting a job to set electable group override on " + _prettyGroupNodeName + " to " + electableGroupOverride);
        }

        return _environmentJobExecutor.submit(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                setElectableGroupSizeOverrideInternal(electableGroupOverride);
                return null;
            }
        });
    }

    void setElectableGroupSizeOverrideInternal(int electableGroupOverride)
    {
        try
        {
            final ReplicationMutableConfig oldConfig = _environment.getRepMutableConfig();
            final ReplicationMutableConfig newConfig = oldConfig.setElectableGroupSizeOverride(electableGroupOverride);
            _environment.setRepMutableConfig(newConfig);

            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Node " + _prettyGroupNodeName + " electable group size override has been changed to " + electableGroupOverride);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Cannot set electable group size to " + electableGroupOverride + " on node " + _prettyGroupNodeName, e);
        }
    }

    public Future<Void> transferMasterToSelfAsynchronously()
    {
        final String nodeName = getNodeName();
        return transferMasterAsynchronously(nodeName);
    }

    public Future<Void> transferMasterAsynchronously(final String nodeName)
    {
        return _groupChangeExecutor.submit(new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                try
                {
                    ReplicationGroupAdmin admin = createReplicationGroupAdmin();
                    String newMaster = admin.transferMaster(Collections.singleton(nodeName),
                                                            _masterTransferTimeout, TimeUnit.MILLISECONDS, true);
                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("The mastership has been transferred to " + newMaster);
                    }
                }
                catch (DatabaseException e)
                {
                    LOGGER.warn("Exception on transferring the mastership to " + _prettyGroupNodeName
                                + " Master transfer timeout : " + _masterTransferTimeout, e);
                    throw e;
                }
                return null;
            }
        });
    }

    public void removeNodeFromGroup(final String nodeName)
    {
        createReplicationGroupAdmin().removeMember(nodeName);
    }

    public long getJoinTime()
    {
        return _joinTime;
    }

    public long getLastKnownReplicationTransactionId()
    {
        if (_state.get() == State.OPEN)
        {
            VLSNRange range = RepInternal.getRepImpl(_environment).getVLSNIndex().getRange();
            VLSN lastTxnEnd = range.getLastTxnEnd();
            return lastTxnEnd.getSequence();
        }
        else
        {
            return -1L;
        }
    }

    private ReplicationGroupAdmin createReplicationGroupAdmin()
    {
        final Set<InetSocketAddress> helpers = new HashSet<InetSocketAddress>();
        helpers.addAll(_environment.getRepConfig().getHelperSockets());

        final ReplicationConfig repConfig = _environment.getRepConfig();
        helpers.add(HostPortPair.getSocket(HostPortPair.getString(repConfig.getNodeHostname(), repConfig.getNodePort())));

        return new ReplicationGroupAdmin(_configuration.getGroupName(), helpers);
    }

    public ReplicatedEnvironment getEnvironment()
    {
        return _environment;
    }

    public State getFacadeState()
    {
        return _state.get();
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

    private void closeEnvironment()
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Closing JE environment for " + _prettyGroupNodeName);
        }

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
            // Try closing the environment but swallow EnvironmentFailureException
            // if the environment becomes invalid while closing.
            // This can be caused by potential race between facade close and DatabasePinger open.
            try
            {
                _environment.close();
            }
            catch (EnvironmentFailureException efe)
            {
                if (!_environment.isValid())
                {
                    LOGGER.debug("Environment became invalid on close, so ignore", efe);
                }
                else
                {
                    throw efe;
                }
            }
            finally
            {
                _environment = null;
            }
        }
    }

    private void restartEnvironment()
    {
        LOGGER.info("Restarting environment");

        closeEnvironmentOnRestart();

        _environment = createEnvironment(false);

        registerAppStateMonitorIfPermittedNodesSpecified();

        if (_stateChangeListener.get() != null)
        {
            _environment.setStateChangeListener(this);
        }

        LOGGER.info("Environment is restarted");
    }

    private void closeEnvironmentOnRestart()
    {
        ReplicatedEnvironment environment = _environment;
        if (environment != null)
        {
            try
            {
                try
                {
                    closeSequences();
                    closeDatabases();
                }
                catch(Exception e)
                {
                    LOGGER.warn("Ignoring an exception whilst closing databases", e);
                }

                environment.close();
            }
            catch (EnvironmentFailureException efe)
            {
                LOGGER.warn("Ignoring an exception whilst closing environment", efe);
            }
        }
    }

    private void closeSequences()
    {
        RuntimeException firstThrownException = null;
        for (DatabaseEntry  sequenceKey : _cachedSequences.keySet())
        {
            try
            {
                closeSequence(sequenceKey);
            }
            catch(DatabaseException de)
            {
                if (firstThrownException == null)
                {
                    firstThrownException = de;
                }
            }
        }
        if (firstThrownException != null)
        {
            throw firstThrownException;
        }
    }

    private void closeDatabases()
    {
        RuntimeException firstThrownException = null;

        Iterator<String> itr = _cachedDatabases.keySet().iterator();
        while (itr.hasNext())
        {
            String databaseName = itr.next();

            if (databaseName != null)
            {
                try
                {
                    closeDatabase(databaseName);
                }
                catch(RuntimeException e)
                {
                    LOGGER.error("Failed to close database " + databaseName + " on " + _prettyGroupNodeName, e);
                    if (firstThrownException == null)
                    {
                        firstThrownException = e;
                    }
                }
            }
        }

        if (firstThrownException != null)
        {
            throw firstThrownException;
        }
    }

    private ReplicatedEnvironment createEnvironment(boolean createEnvironmentInSeparateThread)
    {
        String groupName = _configuration.getGroupName();
        String helperHostPort = _configuration.getHelperHostPort();
        String hostPort = _configuration.getHostPort();
        boolean designatedPrimary = _configuration.isDesignatedPrimary();
        int priority = _configuration.getPriority();
        int quorumOverride = _configuration.getQuorumOverride();
        String nodeName = _configuration.getName();
        String helperNodeName = _configuration.getHelperNodeName();

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Creating environment");
            LOGGER.info("Environment path " + _environmentDirectory.getAbsolutePath());
            LOGGER.info("Group name " + groupName);
            LOGGER.info("Node name " + nodeName);
            LOGGER.info("Node host port " + hostPort);
            LOGGER.info("Helper host port " + helperHostPort);
            LOGGER.info("Helper node name " + helperNodeName);
            LOGGER.info("Durability " + _defaultDurability);
            LOGGER.info("Designated primary (applicable to 2 node case only) " + designatedPrimary);
            LOGGER.info("Node priority " + priority);
            LOGGER.info("Quorum override " + quorumOverride);
            LOGGER.info("Permitted node list " + _permittedNodes);
        }

        Map<String, String> replicationEnvironmentParameters = new HashMap<>(ReplicatedEnvironmentFacade.REPCONFIG_DEFAULTS);
        replicationEnvironmentParameters.putAll(_configuration.getReplicationParameters());

        ReplicationConfig replicationConfig = new ReplicationConfig(groupName, nodeName, hostPort);
        replicationConfig.setHelperHosts(helperHostPort);
        replicationConfig.setDesignatedPrimary(designatedPrimary);
        replicationConfig.setNodePriority(priority);
        replicationConfig.setElectableGroupSizeOverride(quorumOverride);

        for (Map.Entry<String, String> configItem : replicationEnvironmentParameters.entrySet())
        {
            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Setting ReplicationConfig key " + configItem.getKey() + " to '" + configItem.getValue() + "'");
            }
            replicationConfig.setConfigParam(configItem.getKey(), configItem.getValue());
        }

        Map<String, String> environmentParameters = new HashMap<>(EnvironmentFacade.ENVCONFIG_DEFAULTS);
        environmentParameters.putAll(_configuration.getParameters());

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(true);
        envConfig.setExceptionListener(new LoggingAsyncExceptionListener());
        envConfig.setDurability(_defaultDurability);

        for (Map.Entry<String, String> configItem : environmentParameters.entrySet())
        {
            if (LOGGER.isInfoEnabled())
            {
                LOGGER.info("Setting EnvironmentConfig key " + configItem.getKey() + " to '" + configItem.getValue() + "'");
            }
            envConfig.setConfigParam(configItem.getKey(), configItem.getValue());
        }

        if (createEnvironmentInSeparateThread)
        {
            return createEnvironmentInSeparateThread(_environmentDirectory, envConfig, replicationConfig);
        }
        else
        {
            return createEnvironment(_environmentDirectory, envConfig, replicationConfig);
        }
    }

    private ReplicatedEnvironment createEnvironmentInSeparateThread(final File environmentPathFile, final EnvironmentConfig envConfig,
            final ReplicationConfig replicationConfig)
    {
        Future<ReplicatedEnvironment> environmentFuture = _environmentJobExecutor.submit(new Callable<ReplicatedEnvironment>(){
            @Override
            public ReplicatedEnvironment call() throws Exception
            {
                return createEnvironment(environmentPathFile, envConfig, replicationConfig);
            }});

        final long setUpTimeOutMillis = extractEnvSetupTimeoutMillis(replicationConfig);
        final long initialTimeOutMillis = Math.max(setUpTimeOutMillis / 4, 1000);
        final long remainingTimeOutMillis = setUpTimeOutMillis - initialTimeOutMillis;
        try
        {
            try
            {
                return environmentFuture.get(initialTimeOutMillis, TimeUnit.MILLISECONDS);
            }
            catch (TimeoutException te)
            {
                if (remainingTimeOutMillis > 0)
                {
                    LOGGER.warn("Slow replicated environment creation for " + _prettyGroupNodeName
                                + ". Will continue to wait for further " + remainingTimeOutMillis
                                + "ms. for environment creation to complete.");
                    return environmentFuture.get(remainingTimeOutMillis, TimeUnit.MILLISECONDS);
                }
                else
                {
                    throw te;
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Environment creation was interrupted", e);
        }
        catch (ExecutionException e)
        {
            throw new RuntimeException("Unexpected exception on environment creation", e.getCause());
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException("JE replicated environment creation took too long (permitted time "
                                       + setUpTimeOutMillis + "ms)");
        }
    }

    private ReplicatedEnvironment createEnvironment(File environmentPathFile, EnvironmentConfig envConfig,
            final ReplicationConfig replicationConfig)
    {
        ReplicatedEnvironment environment = null;

        String originalThreadName = Thread.currentThread().getName();
        try
        {
            _envSetupTimeoutMillis = extractEnvSetupTimeoutMillis(replicationConfig);
            environment = new ReplicatedEnvironment(environmentPathFile, replicationConfig, envConfig);
        }
        catch (final InsufficientLogException ile)
        {
            LOGGER.warn("The log files of this node are too old. Network restore will begin now.", ile);
            NetworkRestore restore = new NetworkRestore();
            NetworkRestoreConfig config = new NetworkRestoreConfig();
            config.setRetainLogFiles(false);
            restore.execute(ile, config);
            LOGGER.warn("Network restore complete.");
            environment = new ReplicatedEnvironment(environmentPathFile, replicationConfig, envConfig);
        }
        finally
        {
            Thread.currentThread().setName(originalThreadName);
        }

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Environment is created for node " + _prettyGroupNodeName);
        }
        return environment;
    }

    private long extractEnvSetupTimeoutMillis(ReplicationConfig replicationConfig)
    {
        return (long) PropUtil.parseDuration(replicationConfig.getConfigParam(ReplicationConfig.ENV_SETUP_TIMEOUT));
    }

    public int getNumberOfElectableGroupMembers()
    {
        if (_state.get() != State.OPEN)
        {
            throw new IllegalStateException("Environment facade is not opened");
        }
        return _environment.getGroup().getElectableNodes().size();
    }

    public boolean isMaster()
    {
        return ReplicatedEnvironment.State.MASTER.name().equals(getNodeState());
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

    /**
     * This method should only be invoked from configuration thread on virtual host activation.
     * Otherwise, invocation of this method whilst coalescing committer is committing transactions might result in transaction aborts.
     */
    public void setMessageStoreDurability(SyncPolicy localTransactionSynchronizationPolicy, SyncPolicy remoteTransactionSynchronizationPolicy, ReplicaAckPolicy replicaAcknowledgmentPolicy)
    {
        if (_messageStoreDurability == null || localTransactionSynchronizationPolicy != _messageStoreDurability.getLocalSync()
                || remoteTransactionSynchronizationPolicy != _messageStoreDurability.getReplicaSync()
                || replicaAcknowledgmentPolicy != _messageStoreDurability.getReplicaAck())
        {
            _messageStoreDurability = new Durability(localTransactionSynchronizationPolicy, remoteTransactionSynchronizationPolicy, replicaAcknowledgmentPolicy);

            if (_coalescingCommiter != null)
            {
                _coalescingCommiter.stop();
                _coalescingCommiter = null;
            }

            if (localTransactionSynchronizationPolicy == LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY)
            {
                localTransactionSynchronizationPolicy = SyncPolicy.NO_SYNC;
                _coalescingCommiter = new CoalescingCommiter(_configuration.getGroupName(), this);
                _coalescingCommiter.start();
            }
            _realMessageStoreDurability = new Durability(localTransactionSynchronizationPolicy, remoteTransactionSynchronizationPolicy, replicaAcknowledgmentPolicy);
        }
    }

    public void setPermittedNodes(Collection<String> permittedNodes)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug(_prettyGroupNodeName + " permitted nodes set to " + permittedNodes);
        }

        _permittedNodes.clear();
        if (permittedNodes != null)
        {
            _permittedNodes.addAll(permittedNodes);
            registerAppStateMonitorIfPermittedNodesSpecified();

            ReplicationGroupListener listener = _replicationGroupListener.get();
            int count = 0;
            for(ReplicationNode node: _remoteReplicationNodes.values())
            {
                if (!isNodePermitted(node))
                {
                    onIntruder(listener, node);
                }
                count++;
            }
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug(_prettyGroupNodeName + " checked  " + count + " node(s)");
            }
        }
    }

    Set<String> getPermittedNodes()
    {
        return Collections.unmodifiableSet(_permittedNodes);
    }

    static NodeState getRemoteNodeState(String groupName, ReplicationNode repNode, int dbPingSocketTimeout) throws IOException, ServiceConnectFailedException
    {
        if (repNode == null)
        {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return new DbPing(repNode, groupName, dbPingSocketTimeout).getNodeState();
    }

    public static Set<String> convertApplicationStateBytesToPermittedNodeList(byte[] applicationState)
    {
        if (applicationState == null || applicationState.length == 0)
        {
            return Collections.emptySet();
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try
        {
            Map<String, Object> settings = objectMapper.readValue(applicationState, Map.class);
            return new HashSet<String>((Collection<String>)settings.get(PERMITTED_NODE_LIST));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unexpected exception on de-serializing of application state", e);
        }
    }

    public static Collection<String> connectToHelperNodeAndCheckPermittedHosts(String nodeName, String hostPort, String groupName, String helperNodeName, String helperHostPort, int dbPingSocketTimeout)
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug(String.format("Requesting state of the node '%s' at '%s'", helperNodeName, helperHostPort));
        }

        if (helperNodeName == null || "".equals(helperNodeName))
        {
            throw new IllegalConfigurationException(String.format("A helper node is not specified for node '%s'"
                    + " joining the group '%s'", nodeName, groupName));
        }

        Collection<String> permittedNodes = null;
        try
        {
            ReplicationNodeImpl node = new ReplicationNodeImpl(helperNodeName, helperHostPort);
            NodeState state = getRemoteNodeState(groupName, node, dbPingSocketTimeout);
            byte[] applicationState = state.getAppState();
            permittedNodes = convertApplicationStateBytesToPermittedNodeList(applicationState);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException(String.format("Cannot connect to existing node '%s' at '%s'", helperNodeName, helperHostPort), e);
        }
        catch (ServiceConnectFailedException e)
        {
            throw new IllegalConfigurationException(String.format("Failure to connect to '%s'", helperHostPort), e);
        }
        catch (Exception e)
        {
            throw new RuntimeException(String.format("Cannot retrieve state for node '%s' (%s) from group '%s'",
                    helperNodeName, helperHostPort, groupName), e);
        }

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug(String.format("Attribute 'permittedNodes' on node '%s' is set to '%s'", helperNodeName, String.valueOf(permittedNodes)));
        }

        if (permittedNodes==null || !permittedNodes.contains(hostPort))
        {
            throw new IllegalConfigurationException(String.format("Node from '%s' is not permitted!", hostPort));
        }

        return permittedNodes;
    }

    private void registerAppStateMonitorIfPermittedNodesSpecified()
    {
        if (!_permittedNodes.isEmpty())
        {
            byte[] data = permittedNodeListToBytes(_permittedNodes);
            _environment.registerAppStateMonitor(new EnvironmentStateHolder(data));
        }
    }

    private boolean isNodePermitted(ReplicationNode replicationNode)
    {
        if (_permittedNodes.isEmpty())
        {
            return true;
        }

        String nodeHostPort = getHostPort(replicationNode);
        return _permittedNodes.contains(nodeHostPort);
    }

    private String getHostPort(ReplicationNode replicationNode)
    {
        return replicationNode.getHostName() + ":" + replicationNode.getPort();
    }


    private boolean onIntruder(ReplicationGroupListener replicationGroupListener, ReplicationNode replicationNode)
    {
        if (replicationGroupListener != null)
        {
            return replicationGroupListener.onIntruderNode(replicationNode);
        }
        else
        {
            LOGGER.warn(String.format(
                    "Found an intruder node '%s' from ''%s' . The node is not listed in permitted list: %s",
                    replicationNode.getName(),
                    getHostPort(replicationNode),
                    String.valueOf(_permittedNodes)));
            return true;
        }
    }

    private byte[] permittedNodeListToBytes(Set<String> permittedNodeList)
    {
        HashMap<String, Object> data = new HashMap<String, Object>();
        data.put(PERMITTED_NODE_LIST, permittedNodeList);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        try
        {
            objectMapper.writeValue(baos, data);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unexpected exception on serializing of permitted node list into json", e);
        }
        return baos.toByteArray();
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
               _remoteReplicationNodes.put(replicationNode.getName(), replicationNode);
            }
        }
     }

    private void notifyExistingRemoteReplicationNodes(ReplicationGroupListener listener)
    {
        for (ReplicationNode value : _remoteReplicationNodes.values())
        {
            listener.onReplicationNodeRecovered(value);
        }
    }

    private class RemoteNodeStateLearner implements Callable<Void>
    {
        private Map<String, ReplicatedEnvironment.State> _previousGroupState = Collections.emptyMap();
        private boolean _previousDesignatedPrimary;
        private int _previousElectableGroupOverride;

        @Override
        public Void call()
        {
            boolean continueMonitoring = true;
            try
            {
                if (_state.get() == State.OPEN)
                {
                    try
                    {
                        continueMonitoring = detectGroupChangesAndNotify();
                    }
                    catch(DatabaseException e)
                    {
                        handleDatabaseException("Exception on replication group check", e);
                    }

                    if (continueMonitoring)
                    {
                        boolean currentDesignatedPrimary = isDesignatedPrimary();
                        int currentElectableGroupSizeOverride = getElectableGroupSizeOverride();

                        Map<ReplicationNode, NodeState> nodeStates = discoverNodeStates(_remoteReplicationNodes.values());

                        executeDatabasePingerOnNodeChangesIfMaster(nodeStates, currentDesignatedPrimary, currentElectableGroupSizeOverride);

                       notifyGroupListenerAboutNodeStates(nodeStates);
                    }
                }
            }
            finally
            {
                State state = _state.get();
                if (state != State.CLOSED && state != State.CLOSING && continueMonitoring)
                {
                    _groupChangeExecutor.schedule(this, _remoteNodeMonitorInterval, TimeUnit.MILLISECONDS);
                }
                else
                {
                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Monitoring task is not scheduled:  state " + state + ", continue monitoring flag " + continueMonitoring);
                    }
                }
            }
            return null;
        }

        private boolean detectGroupChangesAndNotify()
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Checking for changes in the group " + _configuration.getGroupName() + " on node " + _configuration.getName());
            }
            boolean shouldContinue = true;
            String groupName = _configuration.getGroupName();
            ReplicatedEnvironment env = _environment;
            ReplicationGroupListener replicationGroupListener = _replicationGroupListener.get();
            if (env != null)
            {
                ReplicationGroup group = env.getGroup();
                Set<ReplicationNode> nodes = new HashSet<ReplicationNode>(group.getNodes());
                String localNodeName = getNodeName();

                Map<String, ReplicationNode> removalMap = new HashMap<String, ReplicationNode>(_remoteReplicationNodes);
                for (ReplicationNode replicationNode : nodes)
                {
                    String discoveredNodeName = replicationNode.getName();
                    if (!discoveredNodeName.equals(localNodeName))
                    {
                        if (!_remoteReplicationNodes.containsKey(discoveredNodeName))
                        {
                            if (LOGGER.isDebugEnabled())
                            {
                                LOGGER.debug("Remote replication node added '" + replicationNode + "' to '" + groupName + "'");
                            }

                            _remoteReplicationNodes.put(discoveredNodeName, replicationNode);

                            if (isNodePermitted(replicationNode))
                            {
                                if (replicationGroupListener != null)
                                {
                                    replicationGroupListener.onReplicationNodeAddedToGroup(replicationNode);
                                }
                            }
                            else
                            {
                                if (!onIntruder(replicationGroupListener, replicationNode))
                                {
                                    shouldContinue = false;
                                }
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
                    for (Map.Entry<String, ReplicationNode> replicationNodeEntry : removalMap.entrySet())
                    {
                        String replicationNodeName = replicationNodeEntry.getKey();
                        if (LOGGER.isDebugEnabled())
                        {
                            LOGGER.debug("Remote replication node removed '" + replicationNodeName + "' from '" + groupName + "'");
                        }
                        _remoteReplicationNodes.remove(replicationNodeName);
                        if (replicationGroupListener != null)
                        {
                            replicationGroupListener.onReplicationNodeRemovedFromGroup(replicationNodeEntry.getValue());
                        }
                    }
                }
            }
            return shouldContinue;
        }

        private Map<ReplicationNode, NodeState> discoverNodeStates(Collection<ReplicationNode> electableNodes)
        {
            final Map<ReplicationNode, NodeState> nodeStates = new HashMap<ReplicationNode, NodeState>();
            Set<Future<Void>> futures = new HashSet<Future<Void>>();

            for (final ReplicationNode node : electableNodes)
            {
                Future<Void> future = _groupChangeExecutor.submit(new Callable<Void>()
                {
                    @Override
                    public Void call()
                    {
                        NodeState nodeStateObject = null;
                        try
                        {
                            nodeStateObject = getRemoteNodeState(_configuration.getGroupName(), node, _dbPingSocketTimeout);
                        }
                        catch (IOException | ServiceConnectFailedException | com.sleepycat.je.rep.utilint.BinaryProtocol.ProtocolException e )
                        {
                            // Cannot discover node states. The node state should be treated as UNKNOWN
                        }

                        nodeStates.put(node, nodeStateObject);
                        return null;
                    }
                });
                futures.add(future);
            }

            for (Future<Void> future : futures)
            {
                try
                {
                    future.get(_remoteNodeMonitorInterval, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
                catch (ExecutionException e)
                {
                    LOGGER.warn("Cannot update node state for group " + _configuration.getGroupName(), e.getCause());
                }
                catch (TimeoutException e)
                {
                    LOGGER.warn("Timeout whilst updating node state for group " + _configuration.getGroupName());
                    future.cancel(true);
                }
            }
            return nodeStates;
        }

        /**
         * If the state of the group changes or the user alters the parameters used to determine if the
         * there  is quorum in the group, execute a single small transaction to discover is quorum is
         * still available.  This allows us to discover if quorum is lost in a timely manner, rather than
         * having to await the next user transaction.
         */
        private void executeDatabasePingerOnNodeChangesIfMaster(final Map<ReplicationNode, NodeState> nodeStates,
                                                                final boolean currentDesignatedPrimary,
                                                                final int currentElectableGroupSizeOverride)
        {
            if (ReplicatedEnvironment.State.MASTER == _environment.getState())
            {
                Map<String, ReplicatedEnvironment.State> currentGroupState = new HashMap<>();
                for (Map.Entry<ReplicationNode, NodeState> entry : nodeStates.entrySet())
                {
                    ReplicationNode node = entry.getKey();
                    NodeState nodeState = entry.getValue();
                    ReplicatedEnvironment.State state = nodeState == null? ReplicatedEnvironment.State.UNKNOWN : nodeState.getNodeState();
                    currentGroupState.put(node.getName(), state);
                }

                ReplicatedEnvironmentFacade.this.isDesignatedPrimary();
                ReplicatedEnvironmentFacade.this.getElectableGroupSizeOverride();

                boolean stateChanged = !_previousGroupState.equals(currentGroupState)
                                || currentDesignatedPrimary != _previousDesignatedPrimary
                                || currentElectableGroupSizeOverride != _previousElectableGroupOverride;

                _previousGroupState = currentGroupState;
                _previousDesignatedPrimary = currentDesignatedPrimary;
                _previousElectableGroupOverride = currentElectableGroupSizeOverride;

                if (stateChanged && State.OPEN == _state.get())
                {
                    new DatabasePinger().pingDb(ReplicatedEnvironmentFacade.this);
                }
            }
        }

        private void notifyGroupListenerAboutNodeStates(final Map<ReplicationNode, NodeState> nodeStates)
        {
            ReplicationGroupListener replicationGroupListener = _replicationGroupListener.get();
            if (replicationGroupListener != null)
            {
                for (Map.Entry<ReplicationNode, NodeState> entry : nodeStates.entrySet())
                {
                    replicationGroupListener.onNodeState(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public static enum State
    {
        OPENING,
        OPEN,
        RESTARTING,
        CLOSING,
        CLOSED
    }

    private static class EnvironmentStateHolder implements AppStateMonitor
    {
        private byte[] _data;

        private EnvironmentStateHolder(byte[] data)
        {
            this._data = data;
        }

        @Override
        public byte[] getAppState()
        {
            return _data;
        }
    }

    public static class ReplicationNodeImpl implements ReplicationNode
    {

        private final InetSocketAddress _address;
        private final String _nodeName;
        private final String _host;
        private final int _port;

        public ReplicationNodeImpl(String nodeName, String hostPort)
        {
            String[] tokens = hostPort.split(":");
            if (tokens.length != 2)
            {
                throw new IllegalArgumentException("Unexpected host port value :" + hostPort);
            }
            _host = tokens[0];
            _port = Integer.parseInt(tokens[1]);
            _nodeName = nodeName;
            _address = new InetSocketAddress(_host, _port);
        }

        @Override
        public String getName()
        {
            return _nodeName;
        }

        @Override
        public NodeType getType()
        {
            return NodeType.ELECTABLE;
        }

        @Override
        public InetSocketAddress getSocketAddress()
        {
            return _address;
        }

        @Override
        public String getHostName()
        {
            return _host;
        }

        @Override
        public int getPort()
        {
            return _port;
        }

        @Override
        public String toString()
        {
            return "ReplicationNodeImpl{" +
                    "_nodeName='" + _nodeName + '\'' +
                    ", _host='" + _host + '\'' +
                    ", _port=" + _port +
                    '}';
        }
    }
}
