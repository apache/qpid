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
package org.apache.qpid.configuration;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Based on code in Apache Ant, this utility class handles property expansion. This
 * is most useful in config files and so on.
 */
public class PropertyUtils
{
    /**
     * Replaces <code>${xxx}</code> style constructions in the given value
     * with the string value of the corresponding data types. Replaces only system
     * properties
     *
     * @param value The string to be scanned for property references.
     *              May be <code>null</code>, in which case this
     *              method returns immediately with no effect.
     * @return the original string with the properties replaced, or
     *         <code>null</code> if the original string is <code>null</code>.
     * @throws PropertyException if the string contains an opening
     *                           <code>${</code> without a closing
     *                           <code>}</code>
     */
    public static String replaceProperties(String value) throws PropertyException
    {
        if (value == null)
        {
            return null;
        }

        ArrayList<String> fragments = new ArrayList<String>();
        ArrayList<String> propertyRefs = new ArrayList<String>();
        parsePropertyString(value, fragments, propertyRefs);

        StringBuffer sb = new StringBuffer();
        Iterator j = propertyRefs.iterator();

        for (String fragment : fragments)
        {
            if (fragment == null)
            {
                String propertyName = (String) j.next();

                // try to get it from the project or keys
                // Backward compatibility
                String replacement = System.getProperty(propertyName);

                if (replacement == null)
                {
                    throw new PropertyException("Property ${" + propertyName +
                                                "} has not been set");
                }
                fragment = replacement;
            }
            sb.append(fragment);
        }

        return sb.toString();
    }

    /**
     * Default parsing method. Parses the supplied value for properties which are specified
     * using ${foo} syntax. $X is left as is, and $$ specifies a single $.
     * @param value the property string to parse
     * @param fragments is populated with the string fragments. A null means "insert a
     * property value here. The number of nulls in the list when populated is equal to the
     * size of the propertyRefs list
     * @param propertyRefs populated with the property names to be added into the final
     * String.
     */
    private static void parsePropertyString(String value, ArrayList<String> fragments,
                                            ArrayList<String> propertyRefs)
            throws PropertyException
    {
        int prev = 0;
        int pos;
        //search for the next instance of $ from the 'prev' position
        while ((pos = value.indexOf("$", prev)) >= 0)
        {

            //if there was any text before this, add it as a fragment
            if (pos > 0)
            {
                fragments.add(value.substring(prev, pos));
            }
            //if we are at the end of the string, we tack on a $
            //then move past it
            if (pos == (value.length() - 1))
            {
                fragments.add("$");
                prev = pos + 1;
            }
            else if (value.charAt(pos + 1) != '{')
            {
                //peek ahead to see if the next char is a property or not
                //not a property: insert the char as a literal
                if (value.charAt(pos + 1) == '$')
                {
                    // two $ map to one $
                    fragments.add("$");
                    prev = pos + 2;
                }
                else
                {
                    // $X maps to $X for all values of X!='$'
                    fragments.add(value.substring(pos, pos + 2));
                    prev = pos + 2;
                }
            }
            else
            {
                // property found, extract its name or bail on a typo
                int endName = value.indexOf('}', pos);
                if (endName < 0)
                {
                    throw new PropertyException("Syntax error in property: " +
                                                value);
                }
                String propertyName = value.substring(pos + 2, endName);
                fragments.add(null);
                propertyRefs.add(propertyName);
                prev = endName + 1;
            }
        }
        //no more $ signs found
        //if there is any tail to the file, append it
        if (prev < value.length())
        {
            fragments.add(value.substring(prev));
        }
    }


}
