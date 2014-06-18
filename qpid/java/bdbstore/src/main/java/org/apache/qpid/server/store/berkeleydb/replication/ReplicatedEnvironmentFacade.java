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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceConfig;

import org.apache.log4j.Logger;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.berkeleydb.CoalescingCommiter;
import org.apache.qpid.server.store.berkeleydb.EnvironmentFacade;
import org.apache.qpid.server.store.berkeleydb.LoggingAsyncExceptionListener;
import org.apache.qpid.server.util.DaemonThreadFactory;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Durability.ReplicaAckPolicy;
import com.sleepycat.je.Durability.SyncPolicy;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.rep.InsufficientLogException;
import com.sleepycat.je.rep.InsufficientReplicasException;
import com.sleepycat.je.rep.NetworkRestore;
import com.sleepycat.je.rep.NetworkRestoreConfig;
import com.sleepycat.je.rep.NodeState;
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

public class ReplicatedEnvironmentFacade implements EnvironmentFacade, StateChangeListener
{
    public static final String MASTER_TRANSFER_TIMEOUT_PROPERTY_NAME = "qpid.bdb.ha.master_transfer_interval";
    public static final String DB_PING_SOCKET_TIMEOUT_PROPERTY_NAME = "qpid.bdb.ha.db_ping_socket_timeout";
    public static final String REMOTE_NODE_MONITOR_INTERVAL_PROPERTY_NAME = "qpid.bdb.ha.remote_node_monitor_interval";

    private static final Logger LOGGER = Logger.getLogger(ReplicatedEnvironmentFacade.class);

    private static final int DEFAULT_MASTER_TRANSFER_TIMEOUT = 1000 * 60;
    private static final int DEFAULT_DB_PING_SOCKET_TIMEOUT = 1000;
    private static final int DEFAULT_REMOTE_NODE_MONITOR_INTERVAL = 1000;

    private static final int MASTER_TRANSFER_TIMEOUT = Integer.getInteger(MASTER_TRANSFER_TIMEOUT_PROPERTY_NAME, DEFAULT_MASTER_TRANSFER_TIMEOUT);
    private static final int DB_PING_SOCKET_TIMEOUT = Integer.getInteger(DB_PING_SOCKET_TIMEOUT_PROPERTY_NAME, DEFAULT_DB_PING_SOCKET_TIMEOUT);
    private static final int REMOTE_NODE_MONITOR_INTERVAL = Integer.getInteger(REMOTE_NODE_MONITOR_INTERVAL_PROPERTY_NAME, DEFAULT_REMOTE_NODE_MONITOR_INTERVAL);
    private static final int RESTART_TRY_LIMIT = 3;

    static final SyncPolicy LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY = SyncPolicy.SYNC;
    static final SyncPolicy REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY = SyncPolicy.NO_SYNC;
    static final ReplicaAckPolicy REPLICA_REPLICA_ACKNOWLEDGMENT_POLICY = ReplicaAckPolicy.SIMPLE_MAJORITY;

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
        put(ReplicationConfig.ENV_SETUP_TIMEOUT, "15 min");
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

    public static final String TYPE = "BDB-HA";

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
    private final CoalescingCommiter _coalescingCommiter;

    private volatile ReplicatedEnvironment _environment;
    private volatile long _joinTime;
    private volatile ReplicatedEnvironment.State _lastKnownEnvironmentState;
    private volatile SyncPolicy _messageStoreLocalTransactionSyncronizationPolicy = LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY;
    private volatile SyncPolicy _messageStoreRemoteTransactionSyncronizationPolicy = REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY;

    private final ConcurrentHashMap<String, Database> _cachedDatabases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DatabaseEntry, Sequence> _cachedSequences = new ConcurrentHashMap<>();

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
        _defaultDurability = new Durability(LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY, REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY, REPLICA_REPLICA_ACKNOWLEDGMENT_POLICY);
        _prettyGroupNodeName = _configuration.getGroupName() + ":" + _configuration.getName();

        // we relay on this executor being single-threaded as we need to restart and mutate the environment in one thread
        _environmentJobExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("Environment-" + _prettyGroupNodeName));
        _groupChangeExecutor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 1, new DaemonThreadFactory("Group-Change-Learner:" + _prettyGroupNodeName));

        _coalescingCommiter  = new CoalescingCommiter(_configuration.getGroupName(), this);

        // create environment in a separate thread to avoid renaming of the current thread by JE
        _environment = createEnvironment(true);
        populateExistingRemoteReplicationNodes();
        _groupChangeExecutor.submit(new RemoteNodeStateLearner());
    }

    @Override
    public Transaction beginTransaction()
    {
        try
        {
            TransactionConfig transactionConfig = new TransactionConfig();
            transactionConfig.setDurability(getMessageStoreTransactionDurability());
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
            tx.commit();
        }
        catch (DatabaseException de)
        {
            throw handleDatabaseException("Got DatabaseException on commit, closing environment", de);
        }
        return _coalescingCommiter.commit(tx, syncCommit);
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

                shutdownAndAwaitExecutorService(_environmentJobExecutor);
                shutdownAndAwaitExecutorService(_groupChangeExecutor);

                try
                {
                    _coalescingCommiter.close();
                    closeSequences();
                    closeDatabases();
                }
                finally
                {
                    closeEnvironment();
                }
            }
            finally
            {
                _state.compareAndSet(State.CLOSING, State.CLOSED);
            }
        }
    }

    private void shutdownAndAwaitExecutorService(ExecutorService executorService)
    {
        executorService.shutdown();
        try
        {
            boolean wasShutdown = executorService.awaitTermination(5000, TimeUnit.MILLISECONDS);
            if (!wasShutdown)
            {
                LOGGER.warn("Executor service " + executorService + " did not shutdown within allowed time period, ignoring");
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
        boolean restart = (dbe instanceof InsufficientReplicasException || dbe instanceof InsufficientReplicasException || dbe instanceof RestartRequiredException);
        if (restart)
        {
            tryToRestartEnvironment(dbe);
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
                    for (int i = 0; i < RESTART_TRY_LIMIT; i++)
                    {
                        try
                        {
                            restartEnvironment();
                            break;
                        }
                        catch(EnvironmentFailureException e)
                        {
                            // log exception and try again
                            LOGGER.warn("Unexpected failure on environment restart. Restart iteration: " + i, e);
                        }
                        catch (Exception e)
                        {
                            LOGGER.error("Exception on environment restart", e);
                            break;
                        }
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
        LOGGER.debug("openDatabase " + name + " for " + _prettyGroupNodeName);
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
                LOGGER.debug("openDatabase " + name + " new handle");

                cachedHandle = handle;
            }
            else
            {
                LOGGER.debug("openDatabase " + name + " existing handle");
                cachedHandle = existingHandle;
                handle.close();
            }
        }
        return cachedHandle;
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
                LOGGER.debug("Ignoring the state environment change event as the environment facade for node '" + _prettyGroupNodeName
                        + "' is in state " + _state.get());
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

    Durability getMessageStoreTransactionDurability()
    {
        SyncPolicy localSync = getMessageStoreLocalTransactionSyncronizationPolicy();
        if ( localSync == LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY && _coalescingCommiter.isStarted())
        {
            localSync = SyncPolicy.NO_SYNC;
        }
        SyncPolicy replicaSync = getMessageStoreRemoteTransactionSyncronizationPolicy();
        return new Durability(localSync, replicaSync, getReplicaAcknowledgmentPolicy());
    }

    public Durability getDurability()
    {
        return new Durability(getMessageStoreLocalTransactionSyncronizationPolicy(),
                getMessageStoreRemoteTransactionSyncronizationPolicy(), getReplicaAcknowledgmentPolicy());
    }

    public boolean isCoalescingSync()
    {
        return _coalescingCommiter.isStarted();
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
                    String newMaster = admin.transferMaster(Collections.singleton(nodeName), MASTER_TRANSFER_TIMEOUT, TimeUnit.MILLISECONDS, true);
                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("The mastership has been transfered to " + newMaster);
                    }
                }
                catch (DatabaseException e)
                {
                    LOGGER.warn("Exception on transfering the mastership to " + _prettyGroupNodeName
                            + " Master transfer timeout : " + MASTER_TRANSFER_TIMEOUT, e);
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

    private void restartEnvironment()
    {
        LOGGER.info("Restarting environment");

        closeEnvironmentOnRestart();

        _environment = createEnvironment(false);

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
                if (environment.isValid())
                {
                    environment.setStateChangeListener(new StateChangeListener()
                    {
                        @Override
                        public void stateChange(StateChangeEvent stateChangeEvent) throws RuntimeException
                        {
                            if (LOGGER.isDebugEnabled())
                            {
                                LOGGER.debug(
                                        "When restarting a state change event is received on NOOP listener for state:"
                                        + stateChangeEvent.getState());
                            }
                        }
                    });
                }

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

        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Creating environment");
            LOGGER.info("Environment path " + _environmentDirectory.getAbsolutePath());
            LOGGER.info("Group name " + groupName);
            LOGGER.info("Node name " + _configuration.getName());
            LOGGER.info("Node host port " + hostPort);
            LOGGER.info("Helper host port " + helperHostPort);
            LOGGER.info("Durability " + _defaultDurability);
            LOGGER.info("Designated primary (applicable to 2 node case only) " + designatedPrimary);
            LOGGER.info("Node priority " + priority);
            LOGGER.info("Quorum override " + quorumOverride);
        }

        Map<String, String> replicationEnvironmentParameters = new HashMap<>(ReplicatedEnvironmentFacade.REPCONFIG_DEFAULTS);
        replicationEnvironmentParameters.putAll(_configuration.getReplicationParameters());

        ReplicationConfig replicationConfig = new ReplicationConfig(groupName, _configuration.getName(), hostPort);
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
                String originalThreadName = Thread.currentThread().getName();
                try
                {
                    return createEnvironment(environmentPathFile, envConfig, replicationConfig);
                }
                finally
                {
                    Thread.currentThread().setName(originalThreadName);
                }
            }});

        long setUpTimeOutMillis = PropUtil.parseDuration(replicationConfig.getConfigParam(ReplicationConfig.ENV_SETUP_TIMEOUT));
        try
        {
            return environmentFuture.get(setUpTimeOutMillis, TimeUnit.MILLISECONDS);
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
            throw new RuntimeException("JE environment has not been created in due time");
        }
    }

    private ReplicatedEnvironment createEnvironment(File environmentPathFile, EnvironmentConfig envConfig,
            final ReplicationConfig replicationConfig)
    {
        ReplicatedEnvironment environment = null;
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
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Environment is created for node " + _prettyGroupNodeName);
        }
        return environment;
    }

    NodeState getRemoteNodeState(ReplicationNode repNode) throws IOException, ServiceConnectFailedException
    {
        if (repNode == null)
        {
            throw new IllegalArgumentException("Node cannot be null");
        }
        return new DbPing(repNode, (String)_configuration.getGroupName(), DB_PING_SOCKET_TIMEOUT).getNodeState();
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

    public SyncPolicy getMessageStoreLocalTransactionSyncronizationPolicy()
    {
        return _messageStoreLocalTransactionSyncronizationPolicy;
    }

    public SyncPolicy getMessageStoreRemoteTransactionSyncronizationPolicy()
    {
        return _messageStoreRemoteTransactionSyncronizationPolicy;
    }

    public ReplicaAckPolicy getReplicaAcknowledgmentPolicy()
    {
        return REPLICA_REPLICA_ACKNOWLEDGMENT_POLICY;
    }

    public void setMessageStoreLocalTransactionSynchronizationPolicy(SyncPolicy localTransactionSyncronizationPolicy)
    {
        _messageStoreLocalTransactionSyncronizationPolicy = localTransactionSyncronizationPolicy;
        if (localTransactionSyncronizationPolicy == LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY)
        {
            _coalescingCommiter.start();
        }
        else
        {
            _coalescingCommiter.stop();
        }
    }

    public void setMessageStoreRemoteTransactionSyncrhonizationPolicy(SyncPolicy remoteTransactionSyncronizationPolicy)
    {
        _messageStoreRemoteTransactionSyncronizationPolicy = remoteTransactionSyncronizationPolicy;
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

        @Override
        public Void call()
        {
            try
            {
                if (_state.get() == State.OPEN)
                {
                    try
                    {
                        detectGroupChangesAndNotify();
                    }
                    catch(DatabaseException e)
                    {
                        handleDatabaseException("Exception on replication group check", e);
                    }

                    Map<ReplicationNode, NodeState> nodeStates = discoverNodeStates(_remoteReplicationNodes.values());

                    executeDabasePingerOnNodeChangesIfMaster(nodeStates);

                    notifyGroupListenerAboutNodeStates(nodeStates);
                }

            }
            finally
            {
                State state = _state.get();
                if (state != State.CLOSED && state != State.CLOSING)
                {
                    _groupChangeExecutor.schedule(this, REMOTE_NODE_MONITOR_INTERVAL, TimeUnit.MILLISECONDS);
                }
            }
            return null;
        }

        private void detectGroupChangesAndNotify()
        {
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Checking for changes in the group " + _configuration.getGroupName() + " on node " + _configuration.getName());
            }

            String groupName = _configuration.getGroupName();
            ReplicatedEnvironment env = _environment;
            ReplicationGroupListener replicationGroupListener = _replicationGroupListener.get();
            if (env != null)
            {
                ReplicationGroup group = env.getGroup();
                Set<ReplicationNode> nodes = new HashSet<ReplicationNode>(group.getElectableNodes());
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

                            if (replicationGroupListener != null)
                            {
                                replicationGroupListener.onReplicationNodeAddedToGroup(replicationNode);
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
                            nodeStateObject = getRemoteNodeState(node);
                        }
                        catch (IOException | ServiceConnectFailedException e )
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
                    future.get(REMOTE_NODE_MONITOR_INTERVAL, TimeUnit.MILLISECONDS);
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

        private void executeDabasePingerOnNodeChangesIfMaster(final Map<ReplicationNode, NodeState> nodeStates)
        {
            if (ReplicatedEnvironment.State.MASTER == _environment.getState())
            {
                Map<String, ReplicatedEnvironment.State> currentGroupState = new HashMap<String, ReplicatedEnvironment.State>();
                for (Map.Entry<ReplicationNode, NodeState> entry : nodeStates.entrySet())
                {
                    ReplicationNode node = entry.getKey();
                    NodeState nodeState = entry.getValue();
                    ReplicatedEnvironment.State state = nodeState == null? ReplicatedEnvironment.State.UNKNOWN : nodeState.getNodeState();
                    currentGroupState.put(node.getName(), state);
                }
                boolean stateChanged = !_previousGroupState.equals(currentGroupState);
                _previousGroupState = currentGroupState;
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
}
