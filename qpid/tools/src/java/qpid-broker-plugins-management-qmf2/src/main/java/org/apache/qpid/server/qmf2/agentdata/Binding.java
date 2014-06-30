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
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.SchemaEventClass;
//import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
//import org.apache.qpid.qmf2.common.SchemaProperty;

/**
 * This class provides a concrete implementation of QmfAgentData for the Binding Management Object.
 * In general it's possible to use QmfAgentData without sub-classing as it's really a "bean" style class
 * that retains its properties in a Map, but in the case of the Java Broker Management Agent it's useful
 * to sub-class as we need to map between the properties/statistics as specified in the Java Broker
 * management model and those specified in qpid/spec/management-schema.xml which is what the C++ broker 
 * uses. This class retains a reference to its peer org.apache.qpid.server.model.Binding and does the
 * necessary mapping when its mapEncode() method is called (which is used to serialise the QmfAgentData).
 *
 * @author Fraser Adams
 */
public class Binding extends QmfAgentData
{
    private static final Logger _log = LoggerFactory.getLogger(Binding.class);

    /**
     * This static initialiser block initialises the QMF2 Schema information needed by the Agent to find
     * QmfAgentData and QmfEvent Objects of a given type.
     */
    private static final SchemaObjectClass _schema;
    private static final SchemaEventClass _bindSchema;
    private static final SchemaEventClass _unbindSchema;

    /**
     * Returns the schema for the Binding class.
     * @return the SchemaObjectClass for the Binding class.
     */
    public static SchemaObjectClass getSchema()
    {
        return _schema;
    }

    /**
     * Returns the schema for the Bind Event.
     * @return the SchemaEventClass for the Bind Event.
     */
    public static SchemaEventClass getBindSchema()
    {
        return _bindSchema;
    }

    /**
     * Returns the schema for the Unbind Event.
     * @return the SchemaEventClass for the Unbind Event.
     */
    public static SchemaEventClass getUnbindSchema()
    {
        return _unbindSchema;
    }

    static
    {
        // Declare the schema for the QMF2 broker class.
        _schema = new SchemaObjectClass("org.apache.qpid.broker", "binding");

        // TODO
        //_schema.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));

        // Declare the schema for the QMF2 bind Event class.
        _bindSchema = new SchemaEventClass("org.apache.qpid.broker", "bind");

        // Declare the schema for the QMF2 unbind Event class.
        _unbindSchema = new SchemaEventClass("org.apache.qpid.broker", "unbind");
    }
    // End of static initialiser.

    private final org.apache.qpid.server.model.Binding _binding;

    /**
     * Constructor.
     * @param binding the Binding ConfiguredObject from the broker model.
     */
    @SuppressWarnings("unchecked")
    public Binding(final org.apache.qpid.server.model.Binding binding)
    {
        super(getSchema());
        _binding = binding; // Will eventually be used in mapEncode() to retrieve statistics.
        setValue("bindingKey", binding.getName());

        Map<String, Object> arguments = binding.getArguments();
        // Only add arguments property if the bindings have arguments
        if (arguments != null && arguments.size() > 0)
        {
            setValue("arguments", arguments);
        }
        // origin not implemented in Java Broker - not really sure what the origin property means anyway???
    }

    /**
     * Set the exchangeRef property.
     * @param exchangeRef the exchangeRef ObjectId.
     */
    public void setExchangeRef(final ObjectId exchangeRef)
    {
        setRefValue("exchangeRef", exchangeRef);
    }

    /**
     * Set the queueRef property.
     * @param queueRef the queueRef ObjectId.
     */
    public void setQueueRef(final ObjectId queueRef)
    {
        setRefValue("queueRef", queueRef);
    }

    /**
     * Factory method to create a Bind Event Object with timestamp of now.
     * @return the newly created Bind Event Object.
     */
    public QmfEvent createBindEvent()
    {
        QmfEvent bind = new QmfEvent(_bindSchema);
        bind.setSeverity("info");
        bind.setValue("args", _binding.getArguments());
        bind.setValue("exName", _binding.getExchange().getName());
        bind.setValue("key", _binding.getName());
        bind.setValue("qName", _binding.getQueue().getName());
        // TODO Not sure of a way to get these for Java Broker Exchange.
        //bind.setValue("rhost", _connection.getName());
        //bind.setValue("user", getStringValue("authIdentity"));
        return bind;
    }

    /**
     * Factory method to create an Unbind Event Object with timestamp of now.
     * @return the newly created Unbind Event Object.
     */
    public QmfEvent createUnbindEvent()
    {
        QmfEvent unbind = new QmfEvent(_unbindSchema);
        unbind.setSeverity("info");
        unbind.setValue("exName", _binding.getExchange().getName());
        unbind.setValue("key", _binding.getName());
        unbind.setValue("qName", _binding.getQueue().getName());
        // TODO Not sure of a way to get these for Java Broker Exchange.
        //unbind.setValue("rhost", _connection.getName());
        //unbind.setValue("user", getStringValue("authIdentity"));
        return unbind;
    }

    /**
     * This method maps the org.apache.qpid.server.model.Binding to QMF2 broker properties where possible then
     * serialises into the underlying Map for transmission via AMQP. This method is called by handleQueryRequest()
     * in the org.apache.qpid.qmf2.agent.Agent class implementing the main QMF2 Agent behaviour.
     * 
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        // Statistics 
        setValue("msgMatched", _binding.getMatches());

        update(); // TODO only set update if a statistic has actually changed value.
        return super.mapEncode();
    }
}
