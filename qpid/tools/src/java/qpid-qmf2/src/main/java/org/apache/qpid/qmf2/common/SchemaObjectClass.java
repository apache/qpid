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
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Subclass of SchemaClass.
 * <p>
 * The structure of QmfData objects is formally defined by the class SchemaObjectClass.
 * <p>
 * Agent applications may dynamically construct instances of these objects by adding properties and methods
 * at run time. However, once the Schema is made public, it must be considered immutable, as the hash value
 * must be constant once the Schema is in use.
 * <p>
 * Note that <a href=https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal>QMF2 API</a> suggests that
 * the properties and methods are represented by an unordered map of SchemaProperty or SchemaMethod entries indexed by
 * property or method name, however these are actually represented in the QMF2 protocol as a "List of SCHEMA_PROPERTY
 * and "List of SCHEMA_METHOD" elements that describe the schema objects's properties and methods". In this
 * implementation getProperties() returns a {@code List<SchemaProperty>} and getMethods() returns a {@code List<SchemaMethod>}
 * reflecting the reality of the protocol rather than what is suggested by the API documentation.
 *
 * @author Fraser Adams
 */
public final class SchemaObjectClass extends SchemaClass
{
    private List<SchemaMethod>   _methods = new ArrayList<SchemaMethod>();
    private List<SchemaProperty> _properties = new ArrayList<SchemaProperty>();
    private String[]             _idNames = {};

    /**
     * The main constructor, taking a java.util.Map as a parameter.
     *
     * @param m the map used to construct the SchemaObjectClass.
     */
    public SchemaObjectClass(final Map m)
    {
       super(m);
        if (m != null)
        {
            List<Map> mapEncodedMethods = this.<List<Map>>getValue("_methods");
            if (mapEncodedMethods != null)
            { // In theory this shouldn't be null as "_methods" property of SchemaClass is not optional but.....
                for (Map method : mapEncodedMethods)
                {
                    addMethod(new SchemaMethod(method));
                }
            }

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
     * Construct a SchemaObjectClass from a package name and a class name.
     *
     * @param packageName the package name.
     * @param className the class name.
     */
    public SchemaObjectClass(final String packageName, final String className)
    {
        setClassId(new SchemaClassId(packageName, className, "_data"));
    }

    /**
     * Construct a SchemaObjectClass from a SchemaClassId.
     *
     * @param classId the SchemaClassId identifying this SchemaObjectClass.
     */
    public SchemaObjectClass(final SchemaClassId classId)
    {
        setClassId(new SchemaClassId(classId.getPackageName(), classId.getClassName(), "_data"));
    }

    /**
     * Return optional string description of this Schema Object.
     * @return optional string description of this Schema Object.
     */
    public String getDesc()
    {
        return getStringValue("_desc");
    }

    /**
     * Set the Schema Object's description.
     *
     * @param description optional string description of this Schema Object.
     */
    public void setDesc(final String description)
    {
        setValue("_desc", description);
    }

    /**
     * Return the list of property names to use when constructing the object identifier.
     * <p>
     * Get the value of the list of property names to use when constructing the object identifier.    
     * When a QmfAgentData object is created the values of the properties specified here are used to
     * create the associated ObjectId object name.
     * @return the list of property names to use when constructing the object identifier.
     */
    public String[] getIdNames()
    {
        // As it's not performance critical copy _idNames, because "return _idNames;" causes findBugs to moan.
        return Arrays.copyOf(_idNames, _idNames.length);
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
     * the properties are represented by an unordered map of SchemaProperty indexed by property name, however it
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
     * Return the count of SchemaMethod's in this instance.
     * @return the count of SchemaMethod's in this instance.
     */
    public long getMethodCount()
    {
        return getMethods().size();
    }

    /**
     * Return Schema Object's methods.
     * <p>
     * Note that <a href=https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal>QMF2 API</a> suggests that
     * the methods are represented by an unordered map of SchemaMethod indexed by method name, however it
     * is actually represented in the QMF2 protocol as a "List of SCHEMA_METHOD elements that describe the
     * schema objects's methods. In this implementation getMethods() returns a {@code List<SchemaMethod>} 
     * reflecting the reality of the protocol rather than what is suggested by the API documentation.
     *
     * @return Schema Object's methods.
     */
    public List<SchemaMethod> getMethods()
    {
        return _methods;
    }

    /**
     * Return the SchemaMethod for the parameter "name".
     * @param name the name of the SchemaMethod to return.
     * @return the SchemaMethod for the parameter "name".
     */
    public SchemaMethod getMethod(final String name)
    {
        for (SchemaMethod m : _methods)
        {
            if (m.getName().equals(name))
            {
                return m;
            }
        }
        return null;
    }

    /**
     * Return the SchemaMethod for the index i.
     * @param i the index of the SchemaMethod to return.
     * @return the SchemaMethod for the index i.
     */
    public SchemaMethod getMethod(final int i)
    {
        return _methods.get(i);
    }

    /**
     * Add a new Property.
     *
     * @param name the name of the SchemaProperty.
     * @param value the SchemaProperty associated with "name".
     */
    public void addProperty(final String name, final SchemaProperty value)
    {
        value.setValue("_name", name);
        _properties.add(value);
    }

    /**
     * Add a new Property.
     *
     * @param value the SchemaProperty associated with "name".
     */
    public void addProperty(final SchemaProperty value)
    {
        _properties.add(value);
    }

    /**
     * Add a new Method.
     *
     * @param name the name of the SchemaMethod.
     * @param value the SchemaMethod associated with "name".
     */
    public void addMethod(final String name, final SchemaMethod value)
    {
        value.setValue("_name", name);
        _methods.add(value);
    }

    /**
     * Add a new Method.
     *
     * @param value the SchemaMethod associated with "name".
     */
    public void addMethod(final SchemaMethod value)
    {
        _methods.add(value);
    }

    /**
     * Set the value of the list of property names to use when constructing the object identifier. 
     * <p>   
     * When a QmfAgentData object is created the values of the properties specified here are used to
     * create the associated ObjectId object name.
     * @param idNames the list of property names to use when constructing the object identifier.
     */
    public void setIdNames(final String... idNames)
    {
        _idNames = idNames;
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        System.out.println("SchemaObjectClass:");
        getClassId().listValues();

        if (hasValue("_desc")) System.out.println("desc: " + getDesc());

        if (getMethodCount() > 0)
        {
            System.out.println("methods:");
        }
        for (SchemaMethod m : _methods)
        {
            m.listValues();
        }

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
     * We need to convert any methods from SchemaMethod to Map and any properties from SchemaProperty to Map
     *
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        List<Map> mapEncodedMethods = new ArrayList<Map>();
        for (SchemaMethod m : _methods)
        {
            mapEncodedMethods.add(m.mapEncode());
        }
        setValue("_methods", mapEncodedMethods);

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

        for (SchemaMethod m : _methods)
        {
            m.updateHash(md5);
        }

        for (SchemaProperty p : _properties)
        {
            p.updateHash(md5);
        }
    }
}

