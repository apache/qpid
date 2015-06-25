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
import java.util.HashMap;
import java.util.Map;

/**
 * QMF defines the QmfData class to represent an atomic unit of managment data.
 * <p>
 * The QmfData class defines a collection of named data values. Optionally, a string tag, or "sub-type" may be
 * associated with each data item. This tag may be used by an application to annotate the data item.
 * (Note the tag implementation is TBD).
 * <p>
 * In QMFv2, message bodies are AMQP maps and are therefore easily extended without causing backward
 * compatibility problems. Another benefit of the map-message format is that all messages can be fully
 * parsed when received without the need for external schema data
 * <p>
 * This class is the base class for all QMF2 Data Types in this implementation. It is intended to provide a
 * useful wrapper for the underlying Map, supplying accessor and mutator methods that give an element of type
 * safety (for example strings appear to be encoded as a mixture of byte[] and String depending of the agent, so
 * this class checks for this in its getString())
 * <p>
 * The following diagram represents the QmfData class hierarchy.
 * <p>
 * <img alt="" src="doc-files/QmfData.png">
 *
 * @author Fraser Adams
 */
public class QmfData
{
    protected Map<String, Object> _values = null;
    protected Map<String, String> _subtypes = null;

    /**
     * The default constructor, initialises the QmfData with an empty Map.
     */
    public QmfData()
    {
        _values = new HashMap<String, Object>();
    }

    /**
     * The main constructor, taking a java.util.Map as a parameter. In essence it "deserialises" its state from the Map.
     *
     * @param m the Map used to construct the QmfData.
     */
    @SuppressWarnings("unchecked")
    public QmfData(final Map m)
    {
        if (m == null)
        { // This branch is just a safety precaution and shouldn't generally occur in normal usage scenarios.
            // Initialise with a new HashMap. Should then behave the same as the default Constructor.
            _values = new HashMap<String, Object>();
        }
        else
        {
            Map<String, Object> values = (Map<String, Object>)m.get("_values");
            _values = (values == null) ? m : values;

            Map<String, String> subtypes = (Map<String, String>)m.get("_subtypes");
             _subtypes = subtypes;
        }   
    }

    /**
     * Get the state of the _subtypes Map, (generally used when serialising method request/response arguments.
     *
     * @return the value of the _subtypes Map.
     */
    public Map<String, String> getSubtypes()
    {
        return _subtypes;
    }


    /**
     * Set the state of the _subtypes Map, (generally used when deserialising method request/response arguments.
     *
     * @param subtypes the new value of the _subtypes Map.
     */
    @SuppressWarnings("unchecked")
    public void setSubtypes(Map subtypes)
    {
        _subtypes = subtypes;
    }

    /**
     * Helper method to return the <i>best</i> String representation of the given Object.
     * <p>
     * There seem to be some inconsistencies where string properties are sometimes returned as byte[] and
     * sometimes as Strings. It seems to vary depending on the Agent and is due to strings being encoded as a mix of
     * binary strings and UTF-8 strings by C++ Agent classes. Getting it wrong results in ClassCastExceptions, which
     * is clearly unfortunate.
     * <p>
     * This is basically a helper method to check the type of a property and return the most "appropriate"
     * String representation for it.
     *
     * @param p a property in Object form
     * @return the most appropriate String representation of the property
     */
    public static final String getString(final Object p)
    {
        if (p == null)
        {
            return "";
        }
        else if (p instanceof String)
        {
            return (String)p;
        }
        else if (p instanceof byte[])
        {
            return new String((byte[])p);
        }
        else return p.toString();
    }

    /**
     * Helper method to return the <i>best</i> long representation of the given Object.
     * <p>
     * There seem to be some inconsistencies where properties are sometimes returned as Integer and
     * sometimes as Long. It seems to vary depending on the Agent and getting it wrong results in
     * ClassCastExceptions, which is clearly unfortunate.
     * <p>
     * This is basically a helper method to check the type of a property and return a long representation for it.
     *
     * @param p a property in Object form
     * @return the long representation for it.
     */
    public static final long getLong(final Object p)
    {
        if (p == null)
        {
            return 0;
        }
        else if (p instanceof Long)
        {
            return ((Long)p).longValue();
        }
        else if (p instanceof Integer)
        {
            return ((Integer)p).intValue();
        }
        else if (p instanceof Short)
        {
            return ((Short)p).shortValue();
        }
        else return 0;
    }

    /**
     * Helper method to return the <i>best</i> boolean representation of the given Object.
     * <p>
     * There seem to be some inconsistencies where boolean properties are sometimes returned as Boolean and
     * sometimes as Long/Integer/Short. It seems to vary depending on the Agent and getting it wrong results in
     * ClassCastExceptions, which is clearly unfortunate.
     * <p>
     * This is basically a helper method to check the type of a property and return a boolean representation for it.
     *
     * @param p a property in Object form
     * @return the boolean representation for it.
     */
    public static final boolean getBoolean(final Object p)
    {
        if (p == null)
        {
            return false;
        }
        else if (p instanceof Boolean)
        {
            return ((Boolean)p).booleanValue();
        }
        else if (p instanceof Long)
        {
            return ((Long)p).longValue() > 0;
        }
        else if (p instanceof Integer)
        {
            return ((Integer)p).intValue() > 0;
        }
        else if (p instanceof Short)
        {
            return ((Short)p).shortValue() > 0;
        }
        else if (p instanceof String)
        {
            return Boolean.parseBoolean((String)p);
        }
        else return false;
    }

    /**
     * Helper method to return the <i>best</i> double representation of the given Object.
     * <p>
     * There seem to be some inconsistencies where properties are sometimes returned as Float and
     * sometimes as Double. It seems to vary depending on the Agent and getting it wrong results in
     * ClassCastExceptions, which is clearly unfortunate.
     * <p>
     * This is basically a helper method to check the type of a property and return a Double representation for it.
     *
     * @param p a property in Object form
     * @return the Double representation for it.
     */
    public static final double getDouble(final Object p)
    {
        if (p == null)
        {
            return 0.0d;
        }
        else if (p instanceof Float)
        {
            return ((Float)p).floatValue();
        }
        else if (p instanceof Double)
        {
            return ((Double)p).doubleValue();
        }
        else return 0.0d;
    }

    /**
     * Determines if the named property exists.
     *
     * @param name of the property to check.
     * @return true if the property exists otherwise false.
     */
    public final boolean hasValue(final String name)
    {
        return _values.containsKey(name);
    }

    /**
     * Accessor method to return a named property as an Object.
     *
     * @param name of the property to return as an Object.
     * @return value of property as an Object.
     */
    @SuppressWarnings("unchecked")
    public final <T> T getValue(final String name)
    {
        return (T)_values.get(name);
    }

    /**
     * Mutator method to set a named Object property.
     *
     * @param name the name of the property to set.
     * @param value the value of the property to set.
     */
    public final void setValue(final String name, final Object value)
    {
        _values.put(name, value);
    }

    /**
     * Mutator method to set a named Object property.
     *
     * @param name the name of the property to set.
     * @param value the value of the property to set.
     * @param subtype the subtype of the property.
     */
    public final void setValue(final String name, final Object value, final String subtype)
    {
        setValue(name, value);
        setSubtype(name, subtype);
    }

    /**
     * Mutator to set or modify the subtype associated with name.
     *
     * @param name the name of the property to set the subtype for.
     * @param subtype the subtype of the property.
     */
    public final void setSubtype(final String name, final String subtype)
    {
        if (_subtypes == null)
        {
            _subtypes = new HashMap<String, String>();
        }
        _subtypes.put(name, subtype);
    }

    /**
     * Accessor to return the subtype associated with named property.
     *
     * @param name the name of the property to get the subtype for.
     * @return the subtype of the named property or null if not present.
     */
    public final String getSubtype(final String name)
    {
        if (_subtypes == null)
        {
            return null;
        }
        return _subtypes.get(name);
    }

    /**
     * Accessor method to return a named property as a boolean.
     *
     * @param name of the property to return as a boolean.
     * @return value of property as a boolean.
     */
    public final boolean getBooleanValue(final String name)
    {
        return getBoolean(getValue(name));
    }

    /**
     * Accessor method to return a named property as a long.
     *
     * @param name of the property to return as a long.
     * @return value of property as a long.
     */
    public final long getLongValue(final String name)
    {
        return getLong(getValue(name));
    }

    /**
     * Accessor method to return a named property as a double.
     *
     * @param name of the property to return as a double.
     * @return value of property as a double.
     */
    public final double getDoubleValue(final String name)
    {
        return getDouble(getValue(name));
    }

    /**
     * Accessor method to return a named property as a String.
     *
     * @param name of the property to return as a String.
     * @return value of property as a String.
     */
    public final String getStringValue(final String name)
    {
        return getString(getValue(name));
    }

    /**
     * Accessor method to return a reference property.
     * <p>
     * Many QMF Objects contain reference properties, e.g. references to other QMF Objects.
     * This method allows these to be obtained as ObjectId objects to enable much easier
     * comparison and rendering.
     * @return the retrieved value as an ObjectId instance.
     */
    public final ObjectId getRefValue(final String name)
    {
        return new ObjectId((Map)getValue(name));
    }

    /**
     * Mutator method to set a named reference property.
     * <p>
     * Many QMF Objects contain reference properties, e.g. references to other QMF Objects.
     * This method allows these to be set as ObjectId objects.
     *
     * @param name the name of the property to set.
     * @param value the value of the property to set.
     */
    public final void setRefValue(final String name, final ObjectId value)
    {
        setValue(name, value.mapEncode());
    }

    /**
     * Mutator method to set a named reference property.
     * <p>
     * Many QMF Objects contain reference properties, e.g. references to other QMF Objects.
     * This method allows these to be set as ObjectId objects.
     *
     * @param name the name of the property to set.
     * @param value the value of the property to set.
     * @param subtype the subtype of the property.
     */
    public final void setRefValue(final String name, final ObjectId value, final String subtype)
    {
        setRefValue(name, value);
        setSubtype(name, subtype);
    }

    /**
     * Return the underlying Map representation of this QmfData.
     * @return the underlying Map. 
     */
    public Map<String, Object> mapEncode()
    {
        return _values;
    }

    /**
     * Helper/debug method to list the properties and their type.
     */
    public void listValues()
    {
        for (Map.Entry<String, Object> entry : _values.entrySet())
        {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map)
            { // Check if the value part is an ObjectId and display appropriately
                Map map = (Map)value;
                if (map.containsKey("_object_name"))
                {
                    System.out.println(key + ": " + new ObjectId(map));
                }
                else
                {
                    System.out.println(key + ": " + getString(value));
                }
            }
            else
            {
                System.out.println(key + ": " + getString(value));
            }
        }
    }
}

