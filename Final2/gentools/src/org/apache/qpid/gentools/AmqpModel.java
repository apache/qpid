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

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AmqpModel implements Printable, NodeAware
{
	public LanguageConverter converter;
	public AmqpClassMap classMap;

	public AmqpModel(LanguageConverter converter)
	{
		this.converter = converter;
		this.converter.setModel(this);
		classMap = new AmqpClassMap();
	}

	public boolean addFromNode(Node n, int o, AmqpVersion v)
		throws AmqpParseException, AmqpTypeMappingException
	{
		NodeList nList = n.getChildNodes();
		int eCntr = 0;
		for (int i=0; i<nList.getLength(); i++)
		{
			Node c = nList.item(i);
			if (c.getNodeName().compareTo(Utils.ELEMENT_CLASS) == 0)
			{
				String className = converter.prepareClassName(Utils.getNamedAttribute(c, Utils.ATTRIBUTE_NAME));
				AmqpClass thisClass = classMap.get(className);
				if (thisClass == null)
				{
					thisClass = new AmqpClass(className, converter);
					classMap.put(className, thisClass);
				}
				if (!thisClass.addFromNode(c, eCntr++, v))
				{
					System.out.println("INFO: Generation supression tag found for class " + className + " - removing.");
					thisClass.removeVersion(v);
					classMap.remove(className);
				}
			}
		}
		return true;
	}
	
	public void print(PrintStream out, int marginSize, int tabSize)
	{
		out.println(Utils.createSpaces(marginSize) +
			"[C]=class; [M]=method; [F]=field; [D]=domain; [I]=index; [O]=ordinal" + Utils.lineSeparator);
		out.println(Utils.createSpaces(marginSize) + "Model:");

		for (String thisClassName : classMap.keySet())
		{
			AmqpClass thisClass = classMap.get(thisClassName);
			thisClass.print(out, marginSize + tabSize, tabSize);
		}
	}
}
