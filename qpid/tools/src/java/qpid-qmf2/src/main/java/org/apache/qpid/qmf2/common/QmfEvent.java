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
package org.apache.qpid.qmf2.common;

// Misc Imports
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * QMF supports event management functionality. An event is a notification that is sent by an Agent to alert Console(s)
 * of a change in some aspect of the system under management. Like QMF Data, Events may be formally defined by a Schema
 * or not. Unlike QMF Data, Events are not manageable entities - they have no lifecycle. Events simply indicate a point
 * in time where some interesting action occurred.
 * <p>
 * An AMQP timestamp value is associated with each QmfEvent instance. It indicates the moment in time the event occurred.
 * This timestamp is mandatory.
 * <p>
 * A severity level may be associated with each QmfEvent instance. The following severity levels are supported:
 * <pre>
 *    * "emerg"   - system is unusable
 *    * "alert"   - action must be taken immediately
 *    * "crit"    - the system is in a critical condition
 *    * "err"     - there is an error condition
 *    * "warning" - there is a warning condition
 *    * "notice"  - a normal but significant condition
 *    * "info"    - a purely informational message
 *    * "debug"   - messages generated to debug the application
 * </pre>
 * The default severity is "notice".
 *
 * @author Fraser Adams
 */
public final class QmfEvent extends QmfDescribed
{
    private static final String[] severities = {"emerg", "alert", "crit", "err", "warning", "notice", "info", "debug"};

    private long _timestamp;
    private int  _severity;

    /**
     * The main constructor, taking a java.util.Map as a parameter. In essence it "deserialises" its state from the Map.
     *
     * @param m the map used to construct the QmfEvent
     */
    public QmfEvent(final Map m)
    {
        super(m);
        _timestamp = m.containsKey("_timestamp") ? getLong(m.get("_timestamp")) : System.currentTimeMillis()*1000000l;
        _severity = m.containsKey("_severity") ? (int)getLong(m.get("_severity")) : 5;
    }

    /**
     * This constructor taking a SchemaEventClass is the main constructor used by Agents when creating Events
     * 
     * @param schema the SchemaEventClass describing the Event
     */
    public QmfEvent(final SchemaEventClass schema)
    {
        _timestamp = System.currentTimeMillis()*1000000l;
        setSchemaClassId(schema.getClassId());
    }

    /**
     * Return the timestamp. Timestamps are recorded in nanoseconds since the epoch.
     * <p>
     * An AMQP timestamp value is associated with each QmfEvent instance. It indicates the moment in time the event
     * occurred. This timestamp is mandatory.
     *
     * @return the AMQP timestamp value in nanoseconds since the epoch.
     */
    public long getTimestamp()
    {
        return _timestamp;
    }

    /**
     * Return the severity.
     * <p>
     * A severity level may be associated with each QmfEvent instance. The following severity levels are supported:
     * <pre>
     *        * "emerg"   - system is unusable
     *        * "alert"   - action must be taken immediately
     *        * "crit"    - the system is in a critical condition
     *        * "err"     - there is an error condition
     *        * "warning" - there is a warning condition
     *        * "notice"  - a normal but significant condition
     *        * "info"    - a purely informational message
     *        * "debug"   - messages generated to debug the application
     * </pre>
     * The default severity is "notice"
     *
     * @return the severity value as a String as described above
     */
    public String getSeverity()
    {
        return severities[_severity];
    }

    /**
     * Set the severity level of the Event
     * @param severity the severity level of the Event as an int
     */
    public void setSeverity(final int severity)
    {
        if (severity < 0 || severity > 7)
        {
            // If supplied value is out of range we set to the default severity
            _severity = 5;
            return;
        }
        _severity = severity;
    }

    /**
     * Set the severity level of the Event
     * @param severity the severity level of the Event as a String.
     * <p>
     * The following severity levels are supported:
     * <pre>
     *        * "emerg"   - system is unusable
     *        * "alert"   - action must be taken immediately
     *        * "crit"    - the system is in a critical condition
     *        * "err"     - there is an error condition
     *        * "warning" - there is a warning condition
     *        * "notice"  - a normal but significant condition
     *        * "info"    - a purely informational message
     *        * "debug"   - messages generated to debug the application
     * </pre>
     */
    public void setSeverity(final String severity)
    {
        for (int i = 0; i < severities.length; i++)
        {
            if (severity.equals(severities[i]))
            {
                _severity = i;
                return;
            }
        }
        // If we can't match the values we set to the default severity
        _severity = 5;
    }

    /**
     * Return the underlying Map representation of this QmfEvent
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("_values", super.mapEncode());
        if (_subtypes != null)
        {
            map.put("_subtypes", _subtypes);
        }
        map.put("_schema_id", getSchemaClassId().mapEncode());
        map.put("_timestamp", _timestamp);
        map.put("_severity", _severity);
        return map;
    }

    /**
     * Helper/debug method to list the object properties and their type.
     */
    @Override
    public void listValues()
    {
        System.out.println("QmfEvent:");
        System.out.println(this);
    }

    /**
     * Returns a String representation of the QmfEvent.
     * <p>
     * The String representation attempts to mirror the python class Event __repr__ method as far as possible.
     * @return a String representation of the QmfEvent.
     */
    @Override
    public String toString()
    {
        if (getSchemaClassId() == null)
        {
            return "<uninterpretable>";
        }

        String out = new Date(getTimestamp()/1000000l).toString();
        out += " " + getSeverity() + " " + getSchemaClassId().getPackageName() + ":" + getSchemaClassId().getClassName();
        
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String, Object> entry : super.mapEncode().entrySet())
        {
            String disp = getString(entry.getValue());
            if (disp.contains(" "))
            {
                disp = "\"" + disp + "\"";
            }

            buf.append(" " + entry.getKey() + "=" + disp);
        }

        return out + buf.toString();
    }
}

