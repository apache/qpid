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
import org.apache.qpid.qmf2.common.SchemaEventClass;
//import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
//import org.apache.qpid.qmf2.common.SchemaProperty;

import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Statistics;

/**
 * This class provides a concrete implementation of QmfAgentData for the Exchange Management Object.
 * In general it's possible to use QmfAgentData without sub-classing as it's really a "bean" style class
 * that retains its properties in a Map, but in the case of the Java Broker Management Agent it's useful
 * to sub-class as we need to map between the properties/statistics as specified in the Java Broker
 * management model and those specified in qpid/spec/management-schema.xml which is what the C++ broker 
 * uses. This class retains a reference to its peer org.apache.qpid.server.model.Exchange and does the
 * necessary mapping when its mapEncode() method is called (which is used to serialise the QmfAgentData).
 *
 * @author Fraser Adams
 */
public class Exchange extends QmfAgentData
{
    private static final Logger _log = LoggerFactory.getLogger(Exchange.class);

    /**
     * This static initialiser block initialises the QMF2 Schema information needed by the Agent to find
     * QmfAgentData and QmfEvent Objects of a given type.
     */
    private static final SchemaObjectClass _schema;
    private static final SchemaEventClass _exchangeDeclareSchema;
    private static final SchemaEventClass _exchangeDeleteSchema;

    /**
     * Returns the schema for the Exchange class.
     * @return the SchemaObjectClass for the Exchange class.
     */
    public static SchemaObjectClass getSchema()
    {
        return _schema;
    }

    /**
     * Returns the schema for the Exchange Declare Event.
     * @return the SchemaEventClass for the Exchange Declare Event.
     */
    public static SchemaEventClass getExchangeDeclareSchema()
    {
        return _exchangeDeclareSchema;
    }

    /**
     * Returns the schema for the Exchange Delete Event.
     * @return the SchemaEventClass for the Exchange Delete Event.
     */
    public static SchemaEventClass getExchangeDeleteSchema()
    {
        return _exchangeDeleteSchema;
    }

    static
    {
        // Declare the schema for the QMF2 broker class.
        _schema = new SchemaObjectClass("org.apache.qpid.broker", "exchange");

        // TODO
        //_schema.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));

        // Declare the schema for the QMF2 exchangeDeclare Event class.
        _exchangeDeclareSchema = new SchemaEventClass("org.apache.qpid.broker", "exchangeDeclare");

        // Declare the schema for the QMF2 exchangeDelete Event class.
        _exchangeDeleteSchema = new SchemaEventClass("org.apache.qpid.broker", "exchangeDelete");
    }
    // End of static initialiser.

    private final org.apache.qpid.server.model.Exchange _exchange;
    private String _name;

    /**
     * Constructor.
     * @param vhost the parent VirtualHost ConfiguredObject from the broker model.
     * @param exchange the Exchange ConfiguredObject from the broker model.
     */
    public Exchange(final org.apache.qpid.server.model.VirtualHost vhost,
                    final org.apache.qpid.server.model.Exchange exchange)
    {
        super(getSchema());
        _exchange = exchange;

        _name = _exchange.getName();
        _name = (_name.equals("<<default>>")) ? "" : _name;

        if (vhost == null)
        { // Note we include an empty vhost name in the compare key to make sure things get sorted properly.
            setCompareKey("vhost:/" + _name);
        }
        else
        {
            _name = "vhost:" + vhost.getName() + "/" + _name;
            setCompareKey(_name);
        }

        // In the Java Broker LifetimePolicy may be PERMANENT, DELETE_ON_CONNECTION_CLOSE,
        // DELETE_ON_SESSION_END, DELETE_ON_NO_OUTBOUND_LINKS, DELETE_ON_NO_LINKS, IN_USE
        // We map these to a boolean value to be consistent with the C++ Broker QMF value.
        // TODO The C++ and Java Brokers should really return consistent information.
        LifetimePolicy lifetimePolicy = (LifetimePolicy)_exchange.getAttribute("lifetimePolicy");
        boolean autoDelete = (lifetimePolicy != LifetimePolicy.PERMANENT) ? true : false;

        // TODO vhostRef - currently just use its name to try and get things working with standard command line tools.

        setValue("name", _name);
        setValue("type", _exchange.getAttribute("type"));
        setValue("durable", _exchange.getAttribute("durable"));
        setValue("autoDelete", autoDelete);

        // TODO altExchange and arguments properties.

        // ObjectId needs to be set here in Exchange because the QMF2 version of qpid-config uses a hardcoded
        // _object_name as below in the _object_id that it sets in the getExchange() call and in exchangeRef.
        // It *shouldn't* do this and should really use the _object_id of the exchange object returned by
        // getObjects("exchange"), but it does. The following line causes the Agent to use the explicit
        // ObjectId below rather than constructing its own, which fixes the qpid-config issue.
        setObjectId(new ObjectId("", "org.apache.qpid.broker:exchange:" + _name, 0));
    }

    /**
     * Get the peer org.apache.qpid.server.model.Exchange instance. This is mainly used when creating an Alternate
     * Exchange on a Queue as the underlying method requires an org.apache.qpid.server.model.Exchange.
     */
    public org.apache.qpid.server.model.Exchange getExchange()
    {
        return _exchange;
    }

    /**
     * Factory method to create an Exchange Declare Event Object with timestamp of now.
     * @return the newly created Exchange Declare Event Object.
     */
    public QmfEvent createExchangeDeclareEvent()
    {
        QmfEvent exchangeDeclare = new QmfEvent(_exchangeDeclareSchema);
        exchangeDeclare.setSeverity("info");
        exchangeDeclare.setValue("altEx", ""); // Java Broker can't set Alternate Exchange on Exchange
        exchangeDeclare.setValue("args", Collections.EMPTY_MAP);
        exchangeDeclare.setValue("autoDel", getBooleanValue("autoDelete"));
        exchangeDeclare.setValue("disp", "created");
        exchangeDeclare.setValue("durable", getBooleanValue("durable"));
        exchangeDeclare.setValue("exName", _name);
        exchangeDeclare.setValue("exType", getStringValue("type"));
        // TODO Not sure of a way to get these for Java Broker Exchange.
        //exchangeDeclare.setValue("rhost", _connection.getName());
        //exchangeDeclare.setValue("user", getStringValue("authIdentity"));
        return exchangeDeclare;
    }

    /**
     * Factory method to create an Exchange Delete Event Object with timestamp of now.
     * @return the newly created Exchange Delete Event Object.
     */
    public QmfEvent createExchangeDeleteEvent()
    {
        QmfEvent exchangeDelete = new QmfEvent(_exchangeDeleteSchema);
        exchangeDelete.setSeverity("info");
        exchangeDelete.setValue("exName", _name);
        // TODO Not sure of a way to get these for Java Broker Exchange.
        //exchangeDelete.setValue("rhost", _connection.getName());
        //exchangeDelete.setValue("user", getStringValue("authIdentity"));
        return exchangeDelete;
    }

    /**
     * This method maps the org.apache.qpid.server.model.Exchange to QMF2 broker properties where possible then
     * serialises into the underlying Map for transmission via AMQP. This method is called by handleQueryRequest()
     * in the org.apache.qpid.qmf2.agent.Agent class implementing the main QMF2 Agent behaviour.
     * 
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        // Statistics
        Statistics stats = _exchange.getStatistics();
        long msgReceives = ((Long)stats.getStatistic("messagesIn")).longValue();
        long msgDrops = ((Long)stats.getStatistic("messagesDropped")).longValue();
        long msgRoutes = msgReceives - msgDrops;

        long byteReceives = ((Long)stats.getStatistic("bytesIn")).longValue();
        long byteDrops = ((Long)stats.getStatistic("bytesDropped")).longValue();
        long byteRoutes = byteReceives - byteDrops;

        setValue("producerCount", "Not yet implemented"); // In Qpid 0.20 producerCount statistic returns null.

        // We have to modify the value of bindingCount for Exchange because the QmfManagementAgent "hides" the
        // QMF Objects that relate to its own AMQP Connection/Queues/Bindings so the bindingCount for default direct
        // qmf.default.direct and qmf.default.topic is different to the actual number of QMF bindings.
        long bindingCount = ((Long)stats.getStatistic("bindingCount")).longValue();
        if (_name.equals(""))
        {
            bindingCount -= 3;
        }
        else if (_name.equals("qmf.default.direct"))
        {
            bindingCount -= 2;
        }
        else if (_name.equals("qmf.default.topic"))
        {
            bindingCount -= 1;
        }
        setValue("bindingCount", bindingCount);

        setValue("msgReceives", msgReceives);
        setValue("msgDrops", msgDrops);
        setValue("msgRoutes", msgRoutes);
        setValue("byteReceives", byteReceives);
        setValue("byteDrops", byteDrops);
        setValue("byteRoutes", byteRoutes);

        update(); // TODO only set update if a statistic has actually changed value.
        return super.mapEncode();
    }
}
