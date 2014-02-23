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
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.SchemaEventClass;
//import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
//import org.apache.qpid.qmf2.common.SchemaProperty;

import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.Statistics;

/**
 * This class provides a concrete implementation of QmfAgentData for the Subscription Management Object.
 * In general it's possible to use QmfAgentData without sub-classing as it's really a "bean" style class
 * that retains its properties in a Map, but in the case of the Java Broker Management Agent it's useful
 * to sub-class as we need to map between the properties/statistics as specified in the Java Broker
 * management model and those specified in qpid/spec/management-schema.xml which is what the C++ broker 
 * uses. This class retains a reference to its peer org.apache.qpid.server.model.Consumer and does the
 * necessary mapping when its mapEncode() method is called (which is used to serialise the QmfAgentData).
 *
 * @author Fraser Adams
 */
public class Subscription extends QmfAgentData
{
    private static final Logger _log = LoggerFactory.getLogger(Subscription.class);

    /**
     * This static initialiser block initialises the QMF2 Schema information needed by the Agent to find
     * QmfAgentData and QmfEvent Objects of a given type.
     */
    private static final SchemaObjectClass _schema;
    private static final SchemaEventClass _subscribeSchema;
    private static final SchemaEventClass _unsubscribeSchema;

    /**
     * Returns the schema for the Subscription class.
     * @return the SchemaObjectClass for the Subscription class.
     */
    public static SchemaObjectClass getSchema()
    {
        return _schema;
    }

    /**
     * Returns the schema for the Subscribe Event.
     * @return the SchemaEventClass for the Subscribe Event.
     */
    public static SchemaEventClass getSubscribeSchema()
    {
        return _subscribeSchema;
    }

    /**
     * Returns the schema for the Unsubscribe Event.
     * @return the SchemaEventClass for the Unsubscribe Event.
     */
    public static SchemaEventClass getUnsubscribeSchema()
    {
        return _unsubscribeSchema;
    }

    static
    {
        // Declare the schema for the QMF2 subscription class.
        _schema = new SchemaObjectClass("org.apache.qpid.broker", "subscription");

        // TODO
        //_schema.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));

        // Declare the schema for the QMF2 subscribe Event class.
        _subscribeSchema = new SchemaEventClass("org.apache.qpid.broker", "subscribe");

        // Declare the schema for the QMF2 unsubscribe Event class.
        _unsubscribeSchema = new SchemaEventClass("org.apache.qpid.broker", "unsubscribe");
    }
    // End of static initialiser.

    private final org.apache.qpid.server.model.Consumer _subscription;

    private boolean _exclusive = false;
    private String _qName = "";

    /**
     * Constructor.
     * @param subscription the Consumer ConfiguredObject from the broker model.
     */
    public Subscription(final org.apache.qpid.server.model.Consumer subscription)
    {
        super(getSchema());
        _subscription = subscription; // Will eventually be used in mapEncode() to retrieve statistics.

        setValue("name", subscription.getName());
        setValue("browsing", false); // TODO not supported in Qpid 0.20.
        setValue("acknowledged", true); // TODO not supported in Qpid 0.20.
        setValue("creditMode", "WINDOW"); // TODO not supported in Qpid 0.20.
    }

    /**
     * Set the sessionRef property.
     * @param sessionRef the sessionRef ObjectId.
     */
    public void setSessionRef(final ObjectId sessionRef)
    {
        setRefValue("sessionRef", sessionRef);
    }

    /**
     * Set the queueRef property.
     * @param queueRef the queueRef ObjectId.
     */
    public void setQueueRef(final ObjectId queueRef, final Queue queue)
    {
        setRefValue("queueRef", queueRef);

        // Unfortunately the org.apache.qpid.server.model.Consumer doesn't yet allow access to its associated Queue
        // so we pass a reference ourselves when we do setQueueRef. This is because some Subscription properties
        // are *actually" related to the associated Queue.
        _qName = queue.getName();

        // In the Java Broker exclusivity may be NONE, SESSION, CONNECTION, CONTAINER, PRINCIPAL, LINK
        // We map these to a boolean value to be consistent with the C++ Broker QMF values.
        // TODO The C++ and Java Brokers should really return consistent information.
        ExclusivityPolicy exclusivityPolicy = (ExclusivityPolicy)queue.getAttribute("exclusive");
        _exclusive = (exclusivityPolicy != ExclusivityPolicy.NONE) ? true : false;
    }

    /**
     * Factory method to create a Subscribe Event Object with timestamp of now.
     * @return the newly created Subscribe Event Object.
     */
    public QmfEvent createSubscribeEvent()
    {
        QmfEvent subscribe = new QmfEvent(_subscribeSchema);
        subscribe.setSeverity("info");
        subscribe.setValue("args", Collections.EMPTY_MAP);
        subscribe.setValue("dest", getStringValue("name"));
        subscribe.setValue("excl", _exclusive);
        subscribe.setValue("qName", _qName);
        // TODO Not sure of a way to get these for Java Broker Subscription.
        //subscribe.setValue("rhost", _connection.getName());
        //subscribe.setValue("user", getStringValue("authIdentity"));
        return subscribe;
    }

    /**
     * Factory method to create an Unsubscribe Event Object with timestamp of now.
     * @return the newly created Unsubscribe Event Object.
     */
    public QmfEvent createUnsubscribeEvent()
    {
        QmfEvent unsubscribe = new QmfEvent(_unsubscribeSchema);
        unsubscribe.setSeverity("info");
        unsubscribe.setValue("dest", getStringValue("name"));
        // TODO Not sure of a way to get these for Java Broker Subscription.
        //unsubscribe.setValue("rhost", _connection.getName());
        //unsubscribe.setValue("user", getStringValue("authIdentity"));
        return unsubscribe;
    }

    /**
     * This method maps the org.apache.qpid.server.model.Consumer to QMF2 subscribe properties where possible then
     * serialises into the underlying Map for transmission via AMQP. This method is called by handleQueryRequest()
     * in the org.apache.qpid.qmf2.agent.Agent class implementing the main QMF2 Agent behaviour.
     * 
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        // Statistics 
        Statistics stats = _subscription.getStatistics();
        setValue("delivered", stats.getStatistic("messagesOut"));

        setValue("exclusive", _exclusive);

        update(); // TODO Only Update if statistics have changes.
        return super.mapEncode();
    }
}
