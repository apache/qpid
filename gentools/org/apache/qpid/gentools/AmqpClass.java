/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.gentools;

import java.io.PrintStream;
import java.util.Iterator;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AmqpClass implements Printable, NodeAware
{
	public LanguageConverter converter;
	public AmqpVersionSet versionList;
	public AmqpFieldMap fieldMap;
	public AmqpMethodMap methodMap;
	public String name;
	public AmqpOrdinalMap indexMap;
	
	public AmqpClass(String name, LanguageConverter converter)
	{
		this.name = name;
		this.converter = converter;
		versionList = new AmqpVersionSet();
		fieldMap = new AmqpFieldMap();
		methodMap = new AmqpMethodMap();
		indexMap = new AmqpOrdinalMap();
	}

	public void addFromNode(Node classNode, int ordinal, AmqpVersion version)
		throws AmqpParseException, AmqpTypeMappingException
	{
		versionList.add(version);
		int index = Utils.getNamedIntegerAttribute(classNode, "index");
		AmqpVersionSet versionSet = indexMap.get(index);
		if (versionSet != null)
			versionSet.add(version);
		else
		{
			versionSet = new AmqpVersionSet();
			versionSet.add(version);
			indexMap.put(index, versionSet);
		}
		NodeList nList = classNode.getChildNodes();
		int fieldCntr = 0;
		int methodCntr = 0;
		for (int i=0; i<nList.getLength(); i++)
		{
			Node child = nList.item(i);
			if (child.getNodeName().compareTo(Utils.ELEMENT_FIELD) == 0)
			{
				String fieldName = converter.prepareDomainName(Utils.getNamedAttribute(child, Utils.ATTRIBUTE_NAME));
				AmqpField thisField = fieldMap.get(fieldName);
				if (thisField == null)
				{
					thisField = new AmqpField(fieldName, converter);
					fieldMap.put(fieldName, thisField);
				}
				thisField.addFromNode(child, fieldCntr, version);
				fieldCntr++;
			}
			else if (child.getNodeName().compareTo(Utils.ELEMENT_METHOD) == 0)
			{
				String methodName = converter.prepareMethodName(Utils.getNamedAttribute(child, Utils.ATTRIBUTE_NAME));
				AmqpMethod thisMethod = methodMap.get(methodName);
				if (thisMethod == null)
				{
					thisMethod = new AmqpMethod(methodName, converter);
					methodMap.put(methodName, thisMethod);
				}			
				thisMethod.addFromNode(child, methodCntr++, version);				
			}
		}
	}
	
	public void print(PrintStream out, int marginSize, int tabSize)
	{
		String margin = Utils.createSpaces(marginSize);
		String tab = Utils.createSpaces(tabSize);
		out.println(margin + "[C] " + name + ": " + versionList);
		
		Iterator<Integer> iItr = indexMap.keySet().iterator();
		while (iItr.hasNext())
		{
			int index = iItr.next();
			AmqpVersionSet indexVersionSet = indexMap.get(index);
			out.println(margin + tab + "[I] " + index + indexVersionSet);
		}
		
		Iterator<String> sItr = fieldMap.keySet().iterator();
		while (sItr.hasNext())
		{
			AmqpField thisField = fieldMap.get(sItr.next());
			thisField.print(out, marginSize + tabSize, tabSize);
		}
		
		sItr = methodMap.keySet().iterator();
		while (sItr.hasNext())
		{
			AmqpMethod thisMethod = methodMap.get(sItr.next());
			thisMethod.print(out, marginSize + tabSize, tabSize);
		}
	}
}
