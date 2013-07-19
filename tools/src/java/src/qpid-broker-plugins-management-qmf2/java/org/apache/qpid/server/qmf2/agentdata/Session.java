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
import java.util.Collections;
import java.util.Map;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// QMF2 Imports
import org.apache.qpid.qmf2.agent.QmfAgentData;
import org.apache.qpid.qmf2.common.ObjectId;
//import org.apache.qpid.qmf2.common.SchemaEventClass;
//import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
//import org.apache.qpid.qmf2.common.SchemaProperty;

import org.apache.qpid.server.model.Statistics;

/**
 * This class provides a concrete implementation of QmfAgentData for the Session Management Object.
 * In general it's possible to use QmfAgentData without sub-classing as it's really a "bean" style class
 * that retains its properties in a Map, but in the case of the Java Broker Management Agent it's useful
 * to sub-class as we need to map between the properties/statistics as specified in the Java Broker
 * management model and those specified in qpid/spec/management-schema.xml which is what the C++ broker 
 * uses. This class retains a reference to its peer org.apache.qpid.server.model.Consumer and does the
 * necessary mapping when its mapEncode() method is called (which is used to serialise the QmfAgentData).
 *
 * @author Fraser Adams
 */
public class Session extends QmfAgentData
{
    private static final Logger _log = LoggerFactory.getLogger(Session.class);

    /**
     * This static initialiser block initialises the QMF2 Schema information needed by the Agent to find
     * QmfAgentData and QmfEvent Objects of a given type.
     */
    private static final SchemaObjectClass _schema;

    /**
     * Returns the schema for the Session class.
     * @return the SchemaObjectClass for the Session class.
     */
    public static SchemaObjectClass getSchema()
    {
        return _schema;
    }

    static
    {
        // Declare the schema for the QMF2 session class.
        _schema = new SchemaObjectClass("org.apache.qpid.broker", "session");

        // TODO
        //_schema.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));
    }
    // End of static initialiser.

    private final org.apache.qpid.server.model.Session _session;

    /**
     * Constructor.
     * @param session the Session ConfiguredObject from the broker model.
     * @param connectionRef the ObjectId of the Connection Object that is the parent of the Session.
     */
    public Session(final org.apache.qpid.server.model.Session session, final ObjectId connectionRef)
    {
        super(getSchema());
        _session = session; // Will eventually be used in mapEncode() to retrieve statistics.

        setValue("name", session.getAttribute("id")); // Use ID to be consistent with C++ Broker.
        setValue("channelId", session.getName());     // The Java Broker name uses the channelId.
        setRefValue("connectionRef", connectionRef);
    }

    /**
     * This method maps the org.apache.qpid.server.model.Session to QMF2 subscribe properties where possible then
     * serialises into the underlying Map for transmission via AMQP. This method is called by handleQueryRequest()
     * in the org.apache.qpid.qmf2.agent.Agent class implementing the main QMF2 Agent behaviour.
     * 
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        // Statistics

        Statistics stats = _session.getStatistics();
        setValue("unackedMessages", stats.getStatistic("unacknowledgedMessages"));

        update(); // TODO Only Update if statistics have changes.

        return super.mapEncode();
    }
}
