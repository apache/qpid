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
package org.apache.qpid.gentools;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.TreeSet;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author kpvdr
 * This class implements a set collection for {@link #AmqpConstant AmqpConstant} objects, being the collection
 * of constants accumulated from various AMQP specification files processed. Each name occurs once only in the set.
 * The {@link #AmqpConstant AmqpConstant} objects (derived from {@link java.util#TreeMap TreeMap}) keep track of
 * the value and version(s) assigned to this name.
 */
@SuppressWarnings("serial")
public class AmqpConstantSet extends TreeSet<AmqpConstant> implements Printable, NodeAware, Comparable<AmqpConstantSet>
{
    public LanguageConverter converter;

    public AmqpConstantSet(LanguageConverter converter)
    {
        this.converter = converter;
        this.converter.setConstantSet(this);
    }
    
   /* (non-Javadoc)
     * @see org.apache.qpid.gentools.NodeAware#addFromNode(org.w3c.dom.Node, int, org.apache.qpid.gentools.AmqpVersion)
     */
    public boolean addFromNode(Node node, int ordinal, AmqpVersion version)
        throws AmqpParseException, AmqpTypeMappingException
    {
        NodeList nodeList = node.getChildNodes();
        for (int i=0; i<nodeList.getLength(); i++)
        {
            Node childNode = nodeList.item(i);
            if (childNode.getNodeName().compareTo(Utils.ELEMENT_CONSTANT) == 0)
            {
                String name = converter.prepareDomainName(Utils.getNamedAttribute(childNode, Utils.ATTRIBUTE_NAME));
                String value = Utils.getNamedAttribute(childNode, Utils.ATTRIBUTE_VALUE);
                // Find this name in the existing set of objects
                boolean foundName = false;
                Iterator<AmqpConstant> cItr = iterator();
                while (cItr.hasNext() && !foundName)
                {
                    AmqpConstant thisConstant = cItr.next();
                    if (name.compareTo(thisConstant.name) == 0)
                    {
                        foundName = true;
                        thisConstant.versionSet.add(version);
                        // Now, find the value in the map
                        boolean foundValue = false;
                        for (String thisValue : thisConstant.keySet())
                        {
                            if (value.compareTo(thisValue) == 0)
                            {
                                foundValue = true;
                               // Add this version to existing version set.
                                AmqpVersionSet versionSet = thisConstant.get(thisValue);
                                versionSet.add(version);
                            }
                        }
                        // Check that the value was found - if not, add it
                        if (!foundValue)
                        {
                            thisConstant.put(value, new AmqpVersionSet(version));
                        }              
                    }
                }
                // Check that the name was found - if not, add it
                if (!foundName)
                {
                    add(new AmqpConstant(name, value, version));
                }
           }
        }
        return true;
    }
    
    /* (non-Javadoc)
     * @see org.apache.qpid.gentools.Printable#print(java.io.PrintStream, int, int)
     */
    public void print(PrintStream out, int marginSize, int tabSize)
    {
        out.println(Utils.createSpaces(marginSize) + "Constants: ");
        for (AmqpConstant thisAmqpConstant : this)
        {
        	thisAmqpConstant.print(out, marginSize, tabSize);
        }
    }
    
    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(AmqpConstantSet other)
    {
        int res = size() - other.size();
        if (res != 0)
            return res;
        Iterator<AmqpConstant> cItr = iterator();
        Iterator<AmqpConstant> oItr = other.iterator();
        while (cItr.hasNext() && oItr.hasNext())
        {
            AmqpConstant constant = cItr.next();
            AmqpConstant oConstant = oItr.next();
            res = constant.compareTo(oConstant);
            if (res != 0)
                return res;
        }
        return 0;
    }
}
