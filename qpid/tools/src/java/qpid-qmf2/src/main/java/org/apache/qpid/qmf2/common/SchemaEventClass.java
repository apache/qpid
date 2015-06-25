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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass of SchemaClass
 * <p>
 * Agent applications may dynamically construct instances of these objects by adding properties at run
 * time. However, once the Schema is made public, it must be considered immutable, as the hash value
 * must be constant once the Schema is in use.
 * <p>
 * Note that <a href=https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal>QMF2 API</a> suggests that the
 * properties are represented by an unordered map of SchemaProperty entries indexed by property name, however
 * these are actually represented in the QMF2 protocol as a "List of SCHEMA_PROPERTY elements that describe the
 * schema event's properties.
 * <p>
 * In this implementation getProperties() returns a {@code List<SchemaProperty>} reflecting the reality of the protocol
 * rather than what is suggested by the API documentation.
 *
 * @author Fraser Adams
 */
public final class SchemaEventClass extends SchemaClass
{
    private List<SchemaProperty> _properties = new ArrayList<SchemaProperty>();

    /**
     * The main constructor, taking a java.util.Map as a parameter.
     *
     * @param m the map used to construct the SchemaEventClass
     */
    public SchemaEventClass(final Map m)
    {
       super(m);
        if (m != null)
        {
            List<Map> mapEncodedProperties = this.<List<Map>>getValue("_properties");
            if (mapEncodedProperties != null)
            { // In theory this shouldn't be null as "_properties" property of SchemaClass is not optional but.....
                for (Map property : mapEncodedProperties)
                {
                    addProperty(new SchemaProperty(property));
                }
            }
        }
    }

    /**
     * Construct a SchemaEventClass from a package name and a class name.
     *
     * @param packageName the package name.
     * @param className the class name.
     */
    public SchemaEventClass(final String packageName, final String className)
    {
        setClassId(new SchemaClassId(packageName, className, "_event"));
    }

    /**
     * Construct a SchemaEventClass from a SchemaClassId.
     *
     * @param classId the SchemaClassId identifying this Schema.
     */
    public SchemaEventClass(final SchemaClassId classId)
    {
        setClassId(new SchemaClassId(classId.getPackageName(), classId.getClassName(), "_event"));
    }

    /**
     * Return optional default severity of this Property.
     * @return optional default severity of this Property.
     */
    public long getDefaultSeverity()
    {
        return getLongValue("_default_severity");
    }

    /**
     * Set the default severity of this Schema Event
     *
     * @param severity optional severity of this Schema Event.
     */
    public void setDefaultSeverity(final int severity)
    {
        setValue("_default_severity", severity);
    }

    /**
     * Return optional string description of this Schema Event.
     * @return optional string description of this Schema Event.
     */
    public String getDesc()
    {
        return getStringValue("_desc");
    }

    /**
     * Set the optional string description of this Schema Object.
     *
     * @param description optional string description of this Schema Object.
     */
    public void setDesc(final String description)
    {
        setValue("_desc", description);
    }

    /**
     * Return the count of SchemaProperties in this instance.
     * @return the count of SchemaProperties in this instance.
     */
    public long getPropertyCount()
    {
        return getProperties().size();
    }

    /**
     * Return Schema Object's properties.
     * <p>
     * Note that <a href=https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal>QMF2 API</a> suggests that
     * the properties are represented by an unordered map of SchemaProperty indexed by property name however it
     * is actually represented in the QMF2 protocol as a "List of SCHEMA_PROPERTY elements that describe the
     * schema objects's properties. In this implementation getProperties() returns a {@code List<SchemaProperty>} 
     * reflecting the reality of the protocol rather than what is suggested by the API documentation.
     *
     * @return Schema Object's properties.
     */
    public List<SchemaProperty> getProperties()
    {
        return _properties;
    }

    /**
     * Return the SchemaProperty for the parameter "name".
     * @param name the name of the SchemaProperty to return.
     * @return the SchemaProperty for the parameter "name".
     */
    public SchemaProperty getProperty(final String name)
    {
        for (SchemaProperty p : _properties)
        {
            if (p.getName().equals(name))
            {
                return p;
            }
        }
        return null;
    }

    /**
     * Return the SchemaProperty for the index i.
     * @param i the index of the SchemaProperty to return.
     * @return the SchemaProperty for the index i.
     */
    public SchemaProperty getProperty(final int i)
    {
        return _properties.get(i);
    }

    /**
     * Add a new Property.
     *
     * @param name the name of the SchemaProperty 
     * @param value the SchemaProperty associated with "name"
     */
    public void addProperty(final String name, final SchemaProperty value)
    {
        value.setValue("_name", name);
        _properties.add(value);
    }

    /**
     * Add a new Property.
     *
     * @param value the SchemaProperty associated with "name"
     */
    public void addProperty(final SchemaProperty value)
    {
        _properties.add(value);
    }


    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        System.out.println("SchemaEventClass:");
        getClassId().listValues();

        if (hasValue("_desc")) System.out.println("desc: " + getDesc());
        if (hasValue("_default_severity")) System.out.println("default severity: " + getDefaultSeverity());

        if (getPropertyCount() > 0)
        {
            System.out.println("properties:");
        }
        for (SchemaProperty p : _properties)
        {
            p.listValues();
        }
    }

    /**
     * Return the underlying map. 
     * <p>
     * We need to convert any properties from SchemaProperty to Map.
     *
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        // I think a "_methods" property is mandatory for a SchemaClass even if it's empty
        setValue("_methods", Collections.EMPTY_LIST);
        List<Map> mapEncodedProperties = new ArrayList<Map>();
        for (SchemaProperty p : _properties)
        {
            mapEncodedProperties.add(p.mapEncode());
        }
        setValue("_properties", mapEncodedProperties);
        setValue("_schema_id", getClassId().mapEncode());
        return super.mapEncode();
    }

    /**
     * Generate the partial hash for the schema fields in this class.
     * @param md5 the MessageDigest to be updated.
     */
    @Override
    protected void updateHash(MessageDigest md5)
    {
        super.updateHash(md5);

        for (SchemaProperty p : _properties)
        {
            p.updateHash(md5);
        }
    }
}

