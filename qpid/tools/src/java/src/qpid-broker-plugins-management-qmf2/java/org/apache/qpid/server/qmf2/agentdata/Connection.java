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

package org.apache.qpid.server.qmf2.agentdata;

// Misc Imports
import java.util.Map;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// QMF2 Imports
import org.apache.qpid.qmf2.agent.QmfAgentData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.SchemaEventClass;
//import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
//import org.apache.qpid.qmf2.common.SchemaProperty;

import org.apache.qpid.server.model.Statistics;

/**
 * This class provides a concrete implementation of QmfAgentData for the Connection Management Object.
 * In general it's possible to use QmfAgentData without sub-classing as it's really a "bean" style class
 * that retains its properties in a Map, but in the case of the Java Broker Management Agent it's useful
 * to sub-class as we need to map between the properties/statistics as specified in the Java Broker
 * management model and those specified in qpid/spec/management-schema.xml which is what the C++ broker 
 * uses. This class retains a reference to its peer org.apache.qpid.server.model.Connection and does the
 * necessary mapping when its mapEncode() method is called (which is used to serialise the QmfAgentData).
 *
 * @author Fraser Adams
 */
public class Connection extends QmfAgentData
{
    private static final Logger _log = LoggerFactory.getLogger(Connection.class);

    /**
     * This static initialiser block initialises the QMF2 Schema information needed by the Agent to find
     * QmfAgentData and QmfEvent Objects of a given type.
     */
    private static final SchemaObjectClass _schema;
    private static final SchemaEventClass _clientConnectSchema;
    private static final SchemaEventClass _clientDisconnectSchema;

    /**
     * Returns the schema for the Connection class.
     * @return the SchemaObjectClass for the Connection class.
     */
    public static SchemaObjectClass getSchema()
    {
        return _schema;
    }

    /**
     * Returns the schema for the Client Connect Event.
     * @return the SchemaEventClass for the Client Connect Event.
     */
    public static SchemaEventClass getClientConnectSchema()
    {
        return _clientConnectSchema;
    }

    /**
     * Returns the schema for the Client Disconnect Event.
     * @return the SchemaEventClass for the Client Disconnect Event.
     */
    public static SchemaEventClass getClientDisconnectSchema()
    {
        return _clientDisconnectSchema;
    }

    static
    {
        // Declare the schema for the QMF2 connection class.
        _schema = new SchemaObjectClass("org.apache.qpid.broker", "connection");

        // TODO
        //_schema.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));

        // Declare the schema for the QMF2 clientConnect Event class.
        _clientConnectSchema = new SchemaEventClass("org.apache.qpid.broker", "clientConnect");

        // Declare the schema for the QMF2 clientDisconnect Event class.
        _clientDisconnectSchema = new SchemaEventClass("org.apache.qpid.broker", "clientDisconnect");
    }
    // End of static initialiser.

    private final org.apache.qpid.server.model.Connection _connection;

    /**
     * Constructor.
     * @param vhost the parent VirtualHost ConfiguredObject from the broker model.
     * @param connection the Connection ConfiguredObject from the broker model.
     */
    public Connection(final org.apache.qpid.server.model.VirtualHost vhost,
                      final org.apache.qpid.server.model.Connection connection)
    {
        super(getSchema());
        _connection = connection; // Will eventually be used to retrieve statistics (when useful ones get populated).
        String vhostName = (vhost == null) ? "" : "vhost:" + vhost.getName() + "/";
        String address = vhostName + _connection.getName();

        // TODO vhostRef - currently just use its name to try and get things working with standard command line tools.

        setValue("address", address);
        setValue("incoming", true); // incoming not supported in Qpid 0.20

        // Although not implemented in Qpid 0.20 it's reasonable for them to be false
        setValue("SystemConnection", false); // Is the S in System really a capital? not implemented in Qpid 0.20
        setValue("userProxyAuth", false); // Not implemented in Qpid 0.20
        setValue("federationLink", false); // Not implemented in Qpid 0.20

        setValue("authIdentity", connection.getAttribute("principal"));
        setValue("remoteProcessName", "unknown"); // remoteProcessName not supported in Qpid 0.20
        setValue("remotePid", "unknown"); // remoteProcessName not supported in Qpid 0.20
        setValue("remoteParentPid", "unknown"); // remoteProcessName not supported in Qpid 0.20

        // shadow Not implemented in Qpid 0.20
        // saslMechanism Not implemented in Qpid 0.20
        // saslSsf Not implemented in Qpid 0.20
        // protocol Not implemented in Qpid 0.20
    }

    /**
     * Factory method to create a Client Connect Event Object with timestamp of now.
     * @return the newly created Client Connect Event Object.
     */
    public QmfEvent createClientConnectEvent()
    {
        QmfEvent clientConnect = new QmfEvent(_clientConnectSchema);
        clientConnect.setSeverity("info");
        // TODO Set properties Map - can't really get much info from the org.apache.qpid.server.model.Connection yet.
        clientConnect.setValue("rhost", _connection.getName());
        clientConnect.setValue("user", getStringValue("authIdentity"));
        return clientConnect;
    }

    /**
     * Factory method to create a Client Disconnect Event Object with timestamp of now.
     * @return the newly created Client Disconnect Event Object.
     */
    public QmfEvent createClientDisconnectEvent()
    {
        QmfEvent clientDisconnect = new QmfEvent(_clientDisconnectSchema);
        clientDisconnect.setSeverity("info");
        // TODO Set properties Map - can't really get much info from the org.apache.qpid.server.model.Connection yet.
        clientDisconnect.setValue("rhost", _connection.getName());
        clientDisconnect.setValue("user", getStringValue("authIdentity"));
        return clientDisconnect;
    }

    /**
     * This method maps the org.apache.qpid.server.model.Connection to QMF2 connection properties where possible then
     * serialises into the underlying Map for transmission via AMQP. This method is called by handleQueryRequest()
     * in the org.apache.qpid.qmf2.agent.Agent class implementing the main QMF2 Agent behaviour.
     * 
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        // Statistics
        Statistics stats = _connection.getStatistics();
        // closing Not implemented in Qpid 0.20
        setValue("framesFromClient", 0); // framesFromClient Not implemented in Qpid 0.20
        setValue("framesToClient", 0); // framesToClient Not implemented in Qpid 0.20
        setValue("bytesFromClient", stats.getStatistic("bytesIn"));
        setValue("bytesToClient", stats.getStatistic("bytesOut")); 
        setValue("msgsFromClient", stats.getStatistic("messagesIn"));
        setValue("msgsToClient", stats.getStatistic("messagesOut"));

        update(); // TODO set update if statistics change.
        return super.mapEncode();
    }
}
