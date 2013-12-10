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
import java.util.Map;

// Reuse this class as it provides a handy mechanism to parse an options String into a Map
import org.apache.qpid.messaging.util.AddressParser;

/**
 * The SchemaProperty class describes a single data item in a QmfData object. A SchemaProperty is a list of
 * named attributes and their values. QMF defines a set of primitive attributes. An application can extend
 * this set of attributes with application-specific attributes.
 * <p>
 * QMF reserved attribute names all start with the underscore character ("_"). Do not create an application-specific
 * attribute with a name starting with an underscore.
 * <p>
 * Once instantiated, the SchemaProperty is immutable.
 * <p>
 * Note that there appear to be some differences between the fields mentioned in
 * <a href=https://cwiki.apache.org/qpid/qmfv2-api-proposal.html>QMF2 API propsal</a> and
 * <a href=https://cwiki.apache.org/qpid/qmf-map-message-protocol.html>QMF2 Map Message protocol</a>.
 * I've gone with what's stated in the protocol documentation as this seems more accurate, at least for Qpid 0.10
 *
 * @author Fraser Adams
 */
public final class SchemaProperty extends QmfData
{
    /**
     * Construct a SchemaProperty from its Map representation.
     */
    public SchemaProperty(final Map m)
    {
       super(m);
    }

    /**
     * Construct a SchemaProperty from its name and type.
     *
     * @param name the name of the SchemaProperty.
     * @param type the QmfType of the SchemaProperty.
     */
    public SchemaProperty(final String name, final QmfType type) throws QmfException
    {
        this(name, type, null);
    }

    /**
     * Construct a SchemaProperty from its name, type and options.
     *
     * @param name the name of the SchemaProperty.
     * @param type the QmfType of the SchemaProperty.
     * @param options a String containing the SchemaProperty options in the form:
     * <pre>
     * "{&lt;option1&gt;: &lt;value1&gt;, &lt;option2&gt;: &lt;value2&gt;}".
     * </pre>
     * Example:
     * <pre>
     * "{dir:IN, unit:metre, min:1, max:5, desc: 'ladder extension range'}"
     * </pre>
     */
    public SchemaProperty(final String name, final QmfType type, final String options) throws QmfException
    {
        setValue("_name", name);
        setValue("_type", type.toString());

        if (options != null && options.length() != 0)
        {
            Map optMap = new AddressParser(options).map();

            if (optMap.containsKey("index"))
            {
                String value = optMap.get("index").toString();
                setValue("_index", Boolean.valueOf(value));
            }

            if (optMap.containsKey("access"))
            {
                String value = (String)optMap.get("access");
                if (value.equals("RC") || value.equals("RO") || value.equals("RW"))
                {
                    setValue("_access", value);
                }
                else
                {
                    throw new QmfException("Invalid value for 'access' option. Expected RC, RO, or RW");
                }
            }

            if (optMap.containsKey("unit"))
            {
                String value = (String)optMap.get("unit");
                setValue("_unit", value);
            }

            if (optMap.containsKey("min"))
            { // Slightly odd way of parsing, but AddressParser stores integers as Integer, which seems OK but it limits
              // things like queue length to 2GB. I think this should change so this code deliberately avoids assuming
              // integers are encoded as Integer by using the String representation instead.
                long value = Long.parseLong(optMap.get("min").toString());
                setValue("_min", value);
            }

            if (optMap.containsKey("max"))
            { // Slightly odd way of parsing, but AddressParser stores integers as Integer. which seems OK but it limits
              // things like queue length to 2GB. I think this should change so this code deliberately avoids assuming
              // integers are encoded as Integer by using the String representation instead.
                long value = Long.parseLong(optMap.get("max").toString());
                setValue("_max", value);
            }

            if (optMap.containsKey("maxlen"))
            { // Slightly odd way of parsing, but AddressParser stores integers as Integer, which seems OK but it limits
              // things like queue length to 2GB. I think this should change so this code deliberately avoids assuming
              // integers are encoded as Integer by using the String representation instead.
                long value = Long.parseLong(optMap.get("maxlen").toString());
                setValue("_maxlen", value);
            }

            if (optMap.containsKey("desc"))
            {
                String value = (String)optMap.get("desc");
                setValue("_desc", value);
            }

            if (optMap.containsKey("dir"))
            {
                String value = (String)optMap.get("dir");
                if (value.equals("IN"))
                {
                    setValue("_dir", "I");
                }
                else if (value.equals("OUT"))
                {
                    setValue("_dir", "O");
                }
                else if (value.equals("INOUT"))
                {
                    setValue("_dir", "IO");
                }
                else
                {
                    throw new QmfException("Invalid value for 'dir' option. Expected IN, OUT, or INOUT");
                }
            }

            if (optMap.containsKey("subtype"))
            {
                String value = (String)optMap.get("subtype");
                setValue("_subtype", value);
            }
        }
    }

    /**
     * Return the property's name.
     * @return the property's name.
     */
    public String getName()
    {
        return getStringValue("_name");
    }

    /**
     * Return the property's QmfType.
     * @return the property's QmfType.
     */
    public QmfType getType()
    {
        return QmfType.valueOf(getStringValue("_type"));
    }

    /**
     * Return true iff this property is an index of an object.
     * @return true iff this property is an index of an object. Default is false.
     */
    public boolean isIndex()
    {
        return hasValue("_index") ? getBooleanValue("_index") : false;
    }

    /**
     * Return true iff this property is optional.
     * @return true iff this property is optional. Default is false.
     */
    public boolean isOptional()
    {
        return hasValue("_optional") ? getBooleanValue("_optional") : false;
    }

    /**
     * Return the property's remote access rules.
     * @return the property's remote access rules "RC"=read/create, "RW"=read/write, "RO"=read only (default).
     */
    public String getAccess()
    {
        return getStringValue("_access");
    }

    /**
     * Return an annotation string describing units of measure for numeric values (optional).
     * @return an annotation string describing units of measure for numeric values (optional).
     */
    public String getUnit()
    {
        return getStringValue("_unit");
    }

    /**
     * Return minimum value (optional).
     * @return minimum value (optional).
     */
    public long getMin()
    {
        return getLongValue("_min");
    }

    /**
     * Return maximum value (optional).
     * @return maximum value (optional).
     */
    public long getMax()
    {
        return getLongValue("_max");
    }

    /**
     * Return maximum length for string values (optional).
     * @return maximum length for string values (optional).
     */
    public long getMaxLen()
    {
        return getLongValue("_maxlen");
    }

    /**
     * Return optional string description of this Property.
     * @return optional string description of this Property.
     */
    public String getDesc()
    {
        return getStringValue("_desc");
    }

    /**
     * Return the direction of information travel.
     * @return "I"=input, "O"=output, or "IO"=input/output (required for method arguments, otherwise optional).
     */
    public String getDirection()
    {
        return getStringValue("_dir");
    }

    /**
     * Return string indicating the formal application type.
     * @return string indicating the formal application type for the data, example: "URL", "Telephone number", etc.
     */
    public String getSubtype()
    {
        return getStringValue("_subtype");
    }

    /**
     * Return a SchemaClassId. If the type is a reference to another managed object.
     * @return a SchemaClassId. If the type is a reference to another managed object, this field may be used.
               to specify the required class for that object 
     */
    public SchemaClassId getReference()
    {
        return new SchemaClassId((Map)getValue("_references"));
    }

    /**
     * Return the value of the attribute named "name".
     * @return the value of the attribute named "name".
     * <p>
     * This method can be used to retrieve application-specific attributes. "name" should start with the prefix "x-".
     */
    public String getAttribute(final String name)
    {
        return getStringValue(name);
    }

    /**
     * Generate the partial hash for the schema fields in this class.
     * @param md5 the MessageDigest to be updated
     */
    protected void updateHash(MessageDigest md5)
    {
        try
        {
            md5.update(getName().getBytes("UTF-8"));
            md5.update(getType().toString().getBytes("UTF-8"));
            md5.update(getSubtype().getBytes("UTF-8"));
            md5.update(getAccess().getBytes("UTF-8"));
            if (isIndex())
            {
                md5.update((byte)1);
            }
            else
            {
                md5.update((byte)0);
            }
            if (isOptional())
            {
                md5.update((byte)1);
            }
            else
            {
                md5.update((byte)0);
            }
            md5.update(getUnit().getBytes("UTF-8"));
            md5.update(getDesc().getBytes("UTF-8"));
            md5.update(getDirection().getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException uee)
        {
        }
    }

    /**
     * Helper/debug method to list the QMF Object properties and their type.
     */
    @Override
    public void listValues()
    {
        System.out.println("SchemaProperty:");
        System.out.println("_name: " + getName());
        System.out.println("_type: " + getType());
        if (hasValue("_index")) System.out.println("is index: " + isIndex());
        if (hasValue("_optional")) System.out.println("is optional: " + isOptional());
        if (hasValue("_access")) System.out.println("access: " + getAccess());
        if (hasValue("_unit")) System.out.println("unit: " + getUnit());
        if (hasValue("_min")) System.out.println("min: " + getMin());
        if (hasValue("_max")) System.out.println("max: " + getMax());
        if (hasValue("_max_len")) System.out.println("maxlen: " + getMaxLen());
        if (hasValue("_desc")) System.out.println("desc: " + getDesc());
        if (hasValue("_dir")) System.out.println("dir: " + getDirection());
        if (hasValue("_subtype")) System.out.println("subtype: " + getSubtype());
        if (hasValue("_references")) System.out.println("reference: " + getReference());
    }
}

