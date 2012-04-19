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
package org.apache.qpid.server.configuration;

import java.util.UUID;

public class MockConnectionConfig implements ConnectionConfig
{

    public MockConnectionConfig(UUID _id, ConnectionConfigType _configType,
                    ConfiguredObject<ConnectionConfigType, ConnectionConfig> _parent, boolean _durable,
                    long _createTime, VirtualHostConfig _virtualHost, String _address, Boolean _incoming,
                    Boolean _systemConnection, Boolean _federationLink, String _authId, String _remoteProcessName,
                    Integer _remotePID, Integer _remoteParentPID, ConfigStore _configStore, Boolean _shadow)
    {
        super();
        this._id = _id;
        this._configType = _configType;
        this._parent = _parent;
        this._durable = _durable;
        this._createTime = _createTime;
        this._virtualHost = _virtualHost;
        this._address = _address;
        this._incoming = _incoming;
        this._systemConnection = _systemConnection;
        this._federationLink = _federationLink;
        this._authId = _authId;
        this._remoteProcessName = _remoteProcessName;
        this._remotePID = _remotePID;
        this._remoteParentPID = _remoteParentPID;
        this._configStore = _configStore;
        this._shadow = _shadow;
    }

    private UUID _id;
    private ConnectionConfigType _configType;
    private ConfiguredObject<ConnectionConfigType, ConnectionConfig> _parent;
    private boolean _durable;
    private long _createTime;
    private VirtualHostConfig _virtualHost;
    private String _address;
    private Boolean _incoming;
    private Boolean _systemConnection;
    private Boolean _federationLink;
    private String _authId;
    private String _remoteProcessName;
    private Integer _remotePID;
    private Integer _remoteParentPID;
    private ConfigStore _configStore;
    private Boolean _shadow;

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public ConnectionConfigType getConfigType()
    {
        return _configType;
    }

    @Override
    public ConfiguredObject<ConnectionConfigType, ConnectionConfig> getParent()
    {
        return _parent;
    }

    @Override
    public boolean isDurable()
    {
        return _durable;
    }

    @Override
    public long getCreateTime()
    {
        return _createTime;
    }

    @Override
    public VirtualHostConfig getVirtualHost()
    {
        return _virtualHost;
    }

    @Override
    public String getAddress()
    {
        return _address;
    }

    @Override
    public Boolean isIncoming()
    {
        return _incoming;
    }

    @Override
    public Boolean isSystemConnection()
    {
        return _systemConnection;
    }

    @Override
    public Boolean isFederationLink()
    {
        return _federationLink;
    }

    @Override
    public String getAuthId()
    {
        return _authId;
    }

    @Override
    public String getRemoteProcessName()
    {
        return _remoteProcessName;
    }

    @Override
    public Integer getRemotePID()
    {
        return _remotePID;
    }

    @Override
    public Integer getRemoteParentPID()
    {
        return _remoteParentPID;
    }

    @Override
    public ConfigStore getConfigStore()
    {
        return _configStore;
    }

    @Override
    public Boolean isShadow()
    {
        return _shadow;
    }

    @Override
    public void mgmtClose()
    {
    }

}
