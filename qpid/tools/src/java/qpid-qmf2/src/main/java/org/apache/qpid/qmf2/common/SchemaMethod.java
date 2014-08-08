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
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The SchemaMethod class describes a method call's parameter list.
 * <p>
 * Note that <a href=https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal>QMF2 API</a> suggests that
 * the parameter list is represented by an unordered map of SchemaProperty entries indexed by parameter name,
 * however is is actually represented in the QMF2 protocol as a "List of SCHEMA_PROPERTY elements that describe
 * the method's arguments". 
 * <p>
 * In this implementation getArguments() returns a {@code List<SchemaProperty>} reflecting the reality of the protocol
 * rather than what is suggested by the API documentation.
 *
 * @author Fraser Adams
 */
public final class SchemaMethod extends QmfData
{
    private List<SchemaProperty> _arguments = new ArrayList<SchemaProperty>();

    /**
     * The main constructor, taking a java.util.Map as a parameter.
     *
     * @param m the map used to construct the SchemaMethod.
     */
    public SchemaMethod(final Map m)
    {
        super(m);
        if (m != null)
        {
            List<Map> mapEncodedArguments = this.<List<Map>>getValue("_arguments");
            if (mapEncodedArguments != null)
            { // In theory this shouldn't be null as "_arguments" property of SchemaMethod is not optional but.....
                for (Map argument : mapEncodedArguments)
                {
                    addArgument(new SchemaProperty(argument));
                }
            }
        }
    }

    /**
     * Construct a SchemaMethod from its name.
     *
     * @param name the name of the SchemaMethod.
     */
    public SchemaMethod(final String name)
    {
        this(name, null);
    }

    /**
     * Construct a SchemaMethod from its name and description.
     *
     * @param name the name of the SchemaMethod.
     * @param description a description of the SchemaMethod.
     */
    public SchemaMethod(final String name, final String description)
    {
        setValue("_name", name);

        if (description != null)
        {
            setValue("_desc", description);
        }
    }

    /**
     * Construct a SchemaMethod from a map of "name":{@code <SchemaProperty>} entries and description.
     *
     * Note this Constructor is the one given in the QMF2 API specification at
     * <a href=https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal>QMF2 API</a>Note too that this method does not
     * set a name so setName() needs to be called explicitly by clients after construction.
     *
     * @param args a Map of "name":{@code <SchemaProperty>} entries.
     * @param description a description of the SchemaMethod.
     */
    public SchemaMethod(final Map<String, SchemaProperty> args, final String description)
    {
        if (description != null)
        {
            setValue("_desc", description);
        }

        for (Map.Entry<String, SchemaProperty> entry : args.entrySet())
        {
            addArgument(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Return the method's name.
     * @return the method's name.
     */
    public String getName()
    {
        return getStringValue("_name");
    }

    /**
     * Sets the method's name.
     * @param name the method's name.
     */
    public void setName(final String name)
    {
        setValue("_name", name);
    }

    /**
     * Return a description of the method.
     * @return a description of the method.
     */
    public String getDesc()
    {
        return getStringValue("_desc");
    }

    /**
     * Return the number of arguments for this method.
     * @return the number of arguments for this method.
     */
    public int getArgumentCount()
    {
        return getArguments().size();
    }

    /**
     * Return the Method's arguments.
     *<p>
     * <a href=https://cwiki.apache.org/confluence/display/qpid/QMFv2+API+Proposal>QMF2 API</a> suggests that
     * the parameter list is represented by an unordered map of SchemaProperty entries indexed by parameter name,
     * however is is actually represented in the QMF2 protocol as a "List of SCHEMA_PROPERTY elements that describe
     * the method's arguments". In this implementation getArguments() returns a {@code List<SchemaProperty>} reflecting the
     * reality of the protocol rather than what is suggested by the API documentation.
     *
     * @return the Method's arguments.
     */
    public List<SchemaProperty> getArguments()
    {
        return _arguments;
    }

    /**
     * Return the argument with the name "name" as a SchemaProperty.
     * @param name the name of the SchemaProperty to return.
     * @return the argument with the name "name" as a SchemaProperty.
     */
    public SchemaProperty getArgument(final String name)
    {
        for (SchemaProperty p : _arguments)
        {
            if (p.getName().equals(name))
            {
                return p;
            }
        }
        return null;
    }

    /**
     * Return the argument for the index i as a SchemaProperty.
     * @param i the index of the SchemaProperty to return.
     * @return the argument for the index i as a SchemaProperty.
     */
    public SchemaProperty getArgument(final int i)
    {
        return _arguments.get(i);
    }

    /**
     * Add a new method argument.
     *
     * @param name the name of the SchemaProperty.
     * @param value the SchemaProperty to add.
     */
    public void addArgument(final String name, final SchemaProperty value)
    {
        value.setValue("_name", name);
        _arguments.add(value);
    }

    /**
     * Add a new method argument.
     *
     * @param value the SchemaProperty to add.
     */
    public void addArgument(final SchemaProperty value)
    {
        _arguments.add(value);
    }

    /**
     * Return the underlying map. 
     *
     * @return the underlying map. 
     */
    @Override
    public Map<String, Object> mapEncode()
    {
        List<Map> args = new ArrayList<Map>();
        for (SchemaProperty p : _arguments)
        {
            args.add(p.mapEncode());
        }
        setValue("_arguments", args);

        return super.mapEncode();
    }

    /**
     * Generate the partial hash for the schema fields in this class.
     * @param md5 the MessageDigest to be updated.
     */
    protected void updateHash(MessageDigest md5)
    {
        try
        {
            md5.update(getName().getBytes("UTF-8"));
            md5.update(getDesc().toString().getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException uee)
        {
        }
        for (SchemaProperty p : _arguments)
        {
            p.updateHash(md5);
        }
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        System.out.println("SchemaMethod:");
        System.out.println("_name: " + getName());
        if (hasValue("_desc")) System.out.println("_desc: " + getDesc());
        if (getArgumentCount() > 0)
        {
            System.out.println("_arguments:");
        }
        for (SchemaProperty p : _arguments)
        {
            p.listValues();
        }
    }
}

