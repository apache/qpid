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
import org.apache.qpid.qmf2.agent.Agent;
import org.apache.qpid.qmf2.agent.QmfAgentData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.Handle;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.SchemaEventClass;
//import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
//import org.apache.qpid.qmf2.common.SchemaProperty;

import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Statistics;

/**
 * This class provides a concrete implementation of QmfAgentData for the Queue Management Object.
 * In general it's possible to use QmfAgentData without sub-classing as it's really a "bean" style class
 * that retains its properties in a Map, but in the case of the Java Broker Management Agent it's useful
 * to sub-class as we need to map between the properties/statistics as specified in the Java Broker
 * management model and those specified in qpid/spec/management-schema.xml which is what the C++ broker 
 * uses. This class retains a reference to its peer org.apache.qpid.server.model.Queue and does the
 * necessary mapping when its mapEncode() method is called (which is used to serialise the QmfAgentData).
 *
 * @author Fraser Adams
 */
public class Queue extends QmfAgentData
{
    private static final Logger _log = LoggerFactory.getLogger(Queue.class);

    /**
     * This static initialiser block initialises the QMF2 Schema information needed by the Agent to find
     * QmfAgentData and QmfEvent Objects of a given type.
     */
    private static final SchemaObjectClass _schema;
    private static final SchemaEventClass _queueDeclareSchema;
    private static final SchemaEventClass _queueDeleteSchema;

    /**
     * Returns the schema for the Queue class.
     * @return the SchemaObjectClass for the Queue class.
     */
    public static SchemaObjectClass getSchema()
    {
        return _schema;
    }

    /**
     * Returns the schema for the Queue Declare Event.
     * @return the SchemaEventClass for the Queue Declare Event.
     */
    public static SchemaEventClass getQueueDeclareSchema()
    {
        return _queueDeclareSchema;
    }

    /**
     * Returns the schema for the Queue Delete Event.
     * @return the SchemaEventClass for the Queue Delete Event.
     */
    public static SchemaEventClass getQueueDeleteSchema()
    {
        return _queueDeleteSchema;
    }

    static
    {
        // Declare the schema for the QMF2 broker class.
        _schema = new SchemaObjectClass("org.apache.qpid.broker", "queue");

        // TODO
        //_schema.addProperty(new SchemaProperty("whatHappened", QmfType.TYPE_STRING));

        // Declare the schema for the QMF2 queueDeclare Event class.
        _queueDeclareSchema = new SchemaEventClass("org.apache.qpid.broker", "queueDeclare");

        // Declare the schema for the QMF2 queueDelete Event class.
        _queueDeleteSchema = new SchemaEventClass("org.apache.qpid.broker", "queueDelete");
    }
    // End of static initialiser.

    private final org.apache.qpid.server.model.Queue _queue;
    private String _vhostName = "";
    private ObjectId _alternateExchange = null;
    private String _alternateExchangeName = "";

    /**
     * Constructor.
     * @param vhost the parent VirtualHost ConfiguredObject from the broker model.
     * @param queue the Queue ConfiguredObject from the broker model.
     */
    public Queue(final org.apache.qpid.server.model.VirtualHost vhost,
                 final org.apache.qpid.server.model.Queue queue)
    {
        super(getSchema());
        _queue = queue;

        String name = _queue.getName();

        if (vhost == null)
        { // Note we include an empty vhost name in the compare key to make sure things get sorted properly.
            setCompareKey("vhost:/" + name);
        }
        else
        {
            _vhostName = "vhost:" + vhost.getName() + "/";
            name = _vhostName + name;
            setCompareKey(name);
        }

        LifetimePolicy lifetimePolicy = (LifetimePolicy)_queue.getAttribute("lifetimePolicy");
        boolean autoDelete = (lifetimePolicy != LifetimePolicy.PERMANENT) ? true : false;

        // TODO vhostRef - currently just use its name to try and get things working with standard command line tools.

        setValue("name", name);
        setValue("durable", _queue.getAttribute("durable"));
        setValue("autoDelete", autoDelete);
        setValue("exclusive", _queue.getAttribute("exclusive"));

        // altExchange needs to be set later, done in mapEncode() for convenience, because it isn't set during
        // Queue construction in the Java Broker.

        // TODO arguments properties.


        // ObjectId needs to be set here in Queue because the QMF2 version of qpid-config uses a hardcoded
        // _object_name as below in the _object_id that it sets in the getQueue() call and in queueRef.
        // It *shouldn't* do this and should really use the _object_id of the queue object returned by
        // getObjects("queue"), but it does. The following line causes the Agent to use the explicit
        // ObjectId below rather than constructing its own, which fixes the qpid-config issue.
        setObjectId(new ObjectId("", "org.apache.qpid.broker:queue:" + name, 0));
    }

    /**
     * TODO
     * 
     */
    public void invokeMethod(Agent agent, Handle handle, String methodName, QmfData inArgs)
    {
        /*if (methodName.equals("purge"))
        {
            //broker.create(inArgs);
        }
        else if (methodName.equals("reroute"))
        {
            //broker.create(inArgs);
        }
        else*/
        {
            agent.raiseException(handle, methodName + " not yet implemented on Queue.");
        }
    }

    /**
     * Factory method to create a Queue Declare Event Object with timestamp of now.
     * @return the newly created Queue Declare Event Object.
     */
    public QmfEvent createQueueDeclareEvent()
    {
        QmfEvent queueDeclare = new QmfEvent(_queueDeclareSchema);
        queueDeclare.setSeverity("info");
        // TODO the _alternateExchangeName gets set some time after the Constructor - how do I get its value for
        // the queueDeclareEvent???!!!
        queueDeclare.setValue("altEx", _alternateExchangeName);
        queueDeclare.setValue("args", Collections.EMPTY_MAP); // TODO
        queueDeclare.setValue("autoDel", getBooleanValue("autoDelete"));
        queueDeclare.setValue("disp", "created");
        queueDeclare.setValue("durable", getBooleanValue("durable"));
        queueDeclare.setValue("excl", getBooleanValue("durable"));
        queueDeclare.setValue("qName", getStringValue("name"));
        // TODO Not sure of a way to get these for Java Broker Exchange.
        //queueDeclare.setValue("rhost", _connection.getName());
        //queueDeclare.setValue("user", getStringValue("authIdentity"));
        return queueDeclare;
    }

    /**
     * Factory method to create a Queue Delete Event Object with timestamp of now.
     * @return the newly created Queue Delete Event Object.
     */
    public QmfEvent createQueueDeleteEvent()
    {
        QmfEvent queueDelete = new QmfEvent(_queueDeleteSchema);
        queueDelete.setSeverity("info");
        queueDelete.setValue("qName", getStringValue("name"));
        // TODO Not sure of a way to get these for Java Broker Exchange.
        //queueDelete.setValue("rhost", _connection.getName());
        //queueDelete.setValue("user", getStringValue("authIdentity"));
        return queueDelete;
    }

    /**
     * This method maps the org.apache.qpid.server.model.Queue to QMF2 broker properties where possible then
     * serialises into the underlying Map for transmission via AMQP. This method is called by handleQueryRequest()
     * in the org.apache.qpid.qmf2.agent.Agent class implementing the main QMF2 Agent behaviour.
     * 
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        // Set the altExchange reference if an alternateExchange exists and hasn't already been set.
        // Not sure how to set this closer to the Constructor. At the moment the _alternateExchangeName gets set
        // too late to populate the "altEx" property of the queueDeclareEvent.
        if (_alternateExchange == null)
        {
            Exchange altEx = (Exchange)_queue.getAttribute("alternateExchange");
            if (altEx != null)
            {
                _alternateExchangeName = _vhostName + altEx.getName();
                _alternateExchange = new ObjectId("", "org.apache.qpid.broker:exchange:" + _alternateExchangeName, 0);
                setRefValue("altExchange", _alternateExchange);
            }
        }

        // Statistics
        Statistics stats = _queue.getStatistics();
        setValue("msgTotalEnqueues", stats.getStatistic("totalEnqueuedMessages"));
        setValue("msgTotalDequeues", stats.getStatistic("totalDequeuedMessages"));
        // msgTxnEnqueues not implemented in Qpid 0.20
        // msgTxnDequeues not implemented in Qpid 0.20
        setValue("msgPersistEnqueues", stats.getStatistic("persistentEnqueuedMessages"));
        setValue("msgPersistDequeues", stats.getStatistic("persistentDequeuedMessages"));
        setValue("msgDepth", stats.getStatistic("queueDepthMessages"));
        setValue("byteDepth", stats.getStatistic("queueDepthBytes"));
        setValue("byteTotalEnqueues", stats.getStatistic("totalEnqueuedBytes"));
        setValue("byteTotalDequeues", stats.getStatistic("totalDequeuedBytes"));
        // byteTxnEnqueues not implemented in Qpid 0.20
        // byteTxnDequeues not implemented in Qpid 0.20
        setValue("bytePersistEnqueues", stats.getStatistic("persistentEnqueuedBytes"));
        setValue("bytePersistDequeues", stats.getStatistic("persistentDequeuedBytes"));

        // Flow-to-disk Statistics not implemented in Qpid 0.20
        // releases & acquires not implemented in Qpid 0.20
        // discardsTtl (discardsTtlMessages) not implemented in Qpid 0.20
        // discardsRing not implemented in Qpid 0.20
        // discardsLvq not implemented in Qpid 0.20
        // discardsOverflow not implemented in Qpid 0.20
        // discardsSubscriber not implemented in Qpid 0.20
        // discardsPurge not implemented in Qpid 0.20
        // reroutes not implemented in Qpid 0.20

        setValue("consumerCount", stats.getStatistic("consumerCount"));
        setValue("bindingCount", stats.getStatistic("bindingCount"));
        setValue("unackedMessages", stats.getStatistic("unacknowledgedMessages"));

        setValue("messageLatency", "Not yet implemented");
        // flowStopped not implemented in Qpid 0.20
        // flowStoppedCount not implemented in Qpid 0.20

        update(); // TODO only update if statistics have actually changed.
        return super.mapEncode();
    }
}
