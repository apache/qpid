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
package org.apache.qpid.server.federation;

import org.apache.qpid.AMQStoreException;
import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.ConnectionConfig;
import org.apache.qpid.server.configuration.ConnectionConfigType;
import org.apache.qpid.server.configuration.LinkConfig;
import org.apache.qpid.server.configuration.LinkConfigType;
import org.apache.qpid.server.transport.ServerSession;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.transport.*;
import org.apache.qpid.util.Strings;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class BrokerLink implements LinkConfig, ConnectionListener
{

    private static final int CORE_POOL_SIZE = 4;

    private static final ScheduledThreadPoolExecutor _threadPool =
            new ScheduledThreadPoolExecutor(CORE_POOL_SIZE);
    private static final String TRANSPORT   = "transport";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String REMOTE_VHOST = "remoteVhost";
    private static final String DURABLE = "durable";
    private static final String AUTH_MECHANISM = "authMechanism";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";


    private final String _transport;
    private final String _host;
    private final int _port;
    private final String _remoteVhost;
    private final boolean _durable;
    private final String _authMechanism;
    private final String _username;
    private final String _password;
    private final VirtualHost _virtualHost;
    private UUID _id;
    private AtomicBoolean _closing = new AtomicBoolean();
    private final long _createTime;
    private Connection _qpidConnection;
    private AtomicReference<Thread> _executor = new AtomicReference<Thread>();
    private AtomicInteger _bridgeId = new AtomicInteger();

    private final ConcurrentHashMap<Bridge,Bridge> _bridges = new ConcurrentHashMap<Bridge,Bridge>();
    private final ConcurrentHashMap<Bridge,Bridge> _activeBridges = new ConcurrentHashMap<Bridge,Bridge>();
    private final ConcurrentLinkedQueue<Bridge> _pendingBridges = new ConcurrentLinkedQueue<Bridge>();
    private String _remoteFederationTag;

    private ConnectionConfig _connectionConfig;
    private ConnectionException _exception;
    private String _lastErrorMessage;
    private int _retryDelay = 1;
    private final Runnable _makeConnectionTask = new Runnable()
        {
            public void run()
            {
                doMakeConnection();
            }
        };




    public static enum State
    {
        OPERATIONAL,
        DOWN,
        ESTABLISHING,
        DELETED
    }


    private volatile State _state = State.DOWN;

    private static final AtomicReferenceFieldUpdater<BrokerLink, State> _stateUpdater =
            AtomicReferenceFieldUpdater.newUpdater(BrokerLink.class, State.class, "_state");

    private class ConnectionConfigAdapter implements ConnectionConfig
    {
        private long _adapterCreateTime = System.currentTimeMillis();
        private UUID _id = BrokerLink.this.getConfigStore().createId();

        public VirtualHost getVirtualHost()
        {
            return BrokerLink.this.getVirtualHost();
        }

        public String getAddress()
        {
            return _host+":"+_port;
        }

        public Boolean isIncoming()
        {
            return false;
        }

        public Boolean isSystemConnection()
        {
            return true;
        }

        public Boolean isFederationLink()
        {
            return true;
        }

        public String getAuthId()
        {
            return _username;
        }

        public String getRemoteProcessName()
        {
            return null;
        }

        public Integer getRemotePID()
        {
            return null;
        }

        public Integer getRemoteParentPID()
        {
            return null;
        }

        public ConfigStore getConfigStore()
        {
            return getVirtualHost().getConfigStore();
        }

        public UUID getId()
        {
            return _id;
        }

        public ConnectionConfigType getConfigType()
        {
            return ConnectionConfigType.getInstance();
        }

        public ConfiguredObject getParent()
        {
            return getVirtualHost();
        }

        public boolean isDurable()
        {
            return false;
        }
        
        public long getCreateTime()
        {
            return _adapterCreateTime;
        }

        public Boolean isShadow()
        {
            return false;
        }

        public void mgmtClose()
        {
            _connectionConfig.mgmtClose();
        }
    }

    private class SessionFactory implements Connection.SessionFactory
    {

        public Session newSession(final Connection conn, final Binary name, final long expiry)
        {
            return new ServerSession(conn, new SessionDelegate(), name, expiry, _connectionConfig);
        }
    };

    public BrokerLink(final VirtualHost virtualHost, UUID id, long createTime, Map<String, String> arguments)
    {
        _virtualHost = virtualHost;
        _id = id;
        virtualHost.getConfigStore().persistentIdInUse(id);
        _createTime = createTime;
        _transport = arguments.get(TRANSPORT);

        _host = arguments.get(HOST);
        _port = Integer.parseInt(arguments.get(PORT));
        _remoteVhost = arguments.get(REMOTE_VHOST);
        _durable = Boolean.parseBoolean(arguments.get(DURABLE));
        _authMechanism = arguments.get("authMechanism");
        _username = arguments.get("username");
        _password = arguments.get("password");

        if(_durable)
        {
            try
            {
                _virtualHost.getDurableConfigurationStore().createBrokerLink(this);
            }
            catch (AMQStoreException e)
            {
                throw new RuntimeException(e);
            }
        }


        _qpidConnection = new Connection();
        _connectionConfig = new ConnectionConfigAdapter();
        _qpidConnection.addConnectionListener(this);


        makeConnection();
        
    }


    public BrokerLink(final VirtualHost virtualHost,
                      final String transport,
                      final String host,
                      final int port,
                      final String remoteVhost,
                      final boolean durable,
                      final String authMechanism,
                      final String username,
                      final String password)
    {
        _virtualHost = virtualHost;
        _transport = transport;
        _createTime = System.currentTimeMillis();
        _host = host;
        _port = port;
        _remoteVhost = remoteVhost;
        _durable = durable;
        _authMechanism = authMechanism;
        _username = username;
        _password = password;
        _id = durable ? virtualHost.getConfigStore().createPersistentId() : virtualHost.getConfigStore().createId();

        if(durable)
        {
            try
            {
                _virtualHost.getDurableConfigurationStore().createBrokerLink(this);
            }
            catch (AMQStoreException e)
            {
                throw new RuntimeException(e);
            }
        }
        _qpidConnection = new Connection();
        _connectionConfig = new ConnectionConfigAdapter();
        _qpidConnection.addConnectionListener(this);

        makeConnection();
    }

    public Map<String,String> getArguments()
    {
        Map<String,String> arguments = new HashMap<String, String>();
        
        arguments.put(TRANSPORT, _transport);
        arguments.put(HOST, _host);
        arguments.put(PORT, String.valueOf(_port));
        arguments.put(REMOTE_VHOST, _remoteVhost);
        arguments.put(DURABLE, String.valueOf(_durable));
        arguments.put(AUTH_MECHANISM, _authMechanism);
        arguments.put(USERNAME, _username);
        arguments.put(PASSWORD, _password);

        return Collections.unmodifiableMap(arguments);
    }
    
    private final boolean updateState(State expected, State newState)
    {
        return _stateUpdater.compareAndSet(this,expected,newState);
    }

    private void makeConnection()
    {
        _threadPool.execute(_makeConnectionTask);
    }



    private void doMakeConnection()
    {
        if(updateState(State.DOWN, State.ESTABLISHING))
        {
            try
            {
                _qpidConnection.setConnectionDelegate(new ClientDelegate(new ConnectionSettings())
                {
                    protected SaslClient createSaslClient(List<Object> brokerMechs) throws ConnectionException,
                                                                                           SaslException
                    {
                        Map<String,Object> saslProps = new HashMap<String,Object>();


                        CallbackHandler cbh = new CallbackHandler()
                        {
                            public void handle(final Callback[] callbacks)
                                    throws IOException, UnsupportedCallbackException
                            {
                                for (int i = 0; i < callbacks.length; i++)
                                {
                                    Callback cb = callbacks[i];
                                    if (cb instanceof NameCallback)
                                    {
                                        ((NameCallback)cb).setName(_username);
                                    }
                                    else if (cb instanceof PasswordCallback)
                                    {
                                        ((PasswordCallback)cb).setPassword(_password.toCharArray());
                                    }
                                    else
                                    {
                                        throw new UnsupportedCallbackException(cb);
                                    }
                            }

                            }
                        };
                        final SaslClient sc = Sasl.createSaslClient(new String[] {"PLAIN"}, null,
                                                                    _conSettings.getSaslProtocol(),
                                                                    _conSettings.getSaslServerName(),
                                                                    saslProps, cbh);

                        return sc;
                }});

                _qpidConnection.connect(_host, _port, _remoteVhost, _username, _password, "ssl".equals(_transport), _authMechanism);

                final Map<String,Object> serverProps = _qpidConnection.getServerProperties();

                _remoteFederationTag = (String) serverProps.get(ServerPropertyNames.FEDERATION_TAG);
                if(_remoteFederationTag == null)
                {
                    _remoteFederationTag = UUID.fromString(_transport+":"+_host+":"+_port).toString();
                }
                _qpidConnection.setSessionFactory(new SessionFactory());

                updateState(State.ESTABLISHING, State.OPERATIONAL);

                _retryDelay = 1;

                for(Bridge bridge : _bridges.values())
                {
                    if(_state != State.OPERATIONAL)
                    {
                        break;
                    }
                    addBridge(bridge);
                }


             }
            catch (TransportException e)
            {
                _lastErrorMessage = e.getMessage();
                if(_retryDelay < 60)
                {
                    _retryDelay <<= 1;
                }

                updateState(State.ESTABLISHING, State.DOWN);
                _activeBridges.clear();
                scheduleConnectionRetry();
            }
        }
    }

    private void scheduleConnectionRetry()
    {
        if(_state != State.DELETED)
        {
            _threadPool.schedule(_makeConnectionTask, _retryDelay, TimeUnit.SECONDS);
        }
    }

    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public String getTransport()
    {
        return _transport;
    }

    public String getHost()
    {
        return _host;
    }

    public int getPort()
    {
        return _port;
    }

    public String getRemoteVhost()
    {
        return _remoteVhost;
    }

    public UUID getId()
    {
        return _id;
    }

    public LinkConfigType getConfigType()
    {
        return LinkConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return getVirtualHost();
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public String getAuthMechanism()
    {
        return _authMechanism;
    }

    public String getUsername()
    {
        return _username;
    }

    public String getPassword()
    {
        return _password;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final BrokerLink that = (BrokerLink) o;

        if (_port != that._port)
        {
            return false;
        }
        if (_host != null ? !_host.equals(that._host) : that._host != null)
        {
            return false;
        }
        if (_remoteVhost != null ? !_remoteVhost.equals(that._remoteVhost) : that._remoteVhost != null)
        {
            return false;
        }
        if (_transport != null ? !_transport.equals(that._transport) : that._transport != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _transport != null ? _transport.hashCode() : 0;
        result = 31 * result + (_host != null ? _host.hashCode() : 0);
        result = 31 * result + _port;
        result = 31 * result + (_remoteVhost != null ? _remoteVhost.hashCode() : 0);
        return result;
    }

    public void close()
    {
        if(_closing.compareAndSet(false,true))
        {
            // TODO - close connection
            for(Bridge bridge : _bridges.values())
            {
                bridge.close();
            }
            _bridges.clear();

            _virtualHost.removeBrokerConnection(this);
        }
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public void createBridge(final boolean durable,
                             final boolean dynamic,
                             final boolean srcIsQueue,
                             final boolean srcIsLocal,
                             final String src,
                             final String dest,
                             final String key,
                             final String tag,
                             final String excludes)
    {
        if(!_closing.get())
        {
            Bridge bridge = new Bridge(this, _bridgeId.incrementAndGet(), durable,dynamic,srcIsQueue,srcIsLocal,src,dest,key,tag,excludes);
            if(_bridges.putIfAbsent(bridge, bridge) == null)
            {

                addBridge(bridge);
            }
        }


    }

    public void createBridge(final UUID id, final long createTime, final Map<String, String> arguments)
    {
        if(!_closing.get())
        {
            Bridge bridge = new Bridge(this, _bridgeId.incrementAndGet(), id, createTime, arguments);
            if(_bridges.putIfAbsent(bridge, bridge) == null)
            {

                addBridge(bridge);
            }
        }
    }


    private void addBridge(final Bridge bridge)
    {
        getConfigStore().addConfiguredObject(bridge);

        if(_state == State.OPERATIONAL && (_activeBridges.putIfAbsent(bridge,bridge) == null))
        {


            Session session = _qpidConnection.createSession("Bridge("
                                                            + (bridge.isDurable() ? "durable" : "transient")
                                                            + "," + (bridge.isDynamic() ? "dynamic" : "static")
                                                            + "," + (bridge.isQueueBridge() ? "queue" : "exchange")
                                                            + "," + (bridge.isLocalSource() ? "local-src" : "remote-src")
                                                            + ",[Source: '" + bridge.getSource() + "']"
                                                            + ",[Destination: '" + bridge.getDestination() + "']"
                                                            + ",[Key: '" + bridge.getKey() + "']"
                                                            + ",[Tag: '" + bridge.getTag() + "']"
                                                            + ".[Excludes: '" + bridge.getExcludes() + "'])");
            bridge.setSession(session);


            if(_closing.get())
            {
                bridge.close();
            }
        }

    }

    public void opened(final Connection connection)
    {
        // this method not called
    }

    public void exception(final Connection connection, final ConnectionException exception)
    {
        _exception = exception;
        _lastErrorMessage = exception.getMessage();

    }

    public void closed(final Connection connection)
    {
        State currentState = _state;
        if(currentState != State.DOWN && currentState != State.DELETED && updateState(currentState, State.DOWN))
        {
            scheduleConnectionRetry();
        }
    }

    public ConfigStore getConfigStore()
    {
        return getVirtualHost().getConfigStore();
    }

    public String getFederationTag()
    {
        return getVirtualHost().getFederationTag();
    }

    public String getRemoteFederationTag()
    {
        return _remoteFederationTag;
    }

    public String getState()
    {
        return _state.name();
    }

    public String getLastError()
    {
        return _lastErrorMessage;
    }

    @Override
    public String toString()
    {
        return "BrokerLink{" +
               " _id=" + _id +
               ", _transport='" + _transport + '\'' +
               ", _host='" + _host + '\'' +
               ", _port=" + _port +
               ", _remoteVhost='" + _remoteVhost + '\'' +
               ", _durable=" + _durable +
               ", _authMechanism='" + _authMechanism + '\'' +
               ", _username='" + _username + '\'' +
               ", _password='" + _password + '\'' +
               ", _virtualHost=" + _virtualHost +
               ", _createTime=" + _createTime +
               ", _remoteFederationTag='" + _remoteFederationTag + '\'' +
               ", _state=" + _state +
               '}';
    }
}
