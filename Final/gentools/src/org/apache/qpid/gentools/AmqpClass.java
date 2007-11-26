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

public class AmqpClass implements Printable, NodeAware 
{
	public LanguageConverter converter;
	public AmqpVersionSet versionSet;
	public AmqpFieldMap fieldMap;
	public AmqpMethodMap methodMap;
	public String name;
	public AmqpOrdinalVersionMap indexMap;
	
	public AmqpClass(String name, LanguageConverter converter)
	{
		this.name = name;
		this.converter = converter;
		versionSet = new AmqpVersionSet();
		fieldMap = new AmqpFieldMap();
		methodMap = new AmqpMethodMap();
		indexMap = new AmqpOrdinalVersionMap();
	}

	public boolean addFromNode(Node classNode, int ordinal, AmqpVersion version)
		throws AmqpParseException, AmqpTypeMappingException
	{
		versionSet.add(version);
		int index = Utils.getNamedIntegerAttribute(classNode, "index");
		AmqpVersionSet indexVersionSet = indexMap.get(index);
		if (indexVersionSet != null)
			indexVersionSet.add(version);
		else
		{
			indexVersionSet = new AmqpVersionSet();
			indexVersionSet.add(version);
			indexMap.put(index, indexVersionSet);
		}
		NodeList nList = classNode.getChildNodes();
		int fieldCntr = fieldMap.size();
		for (int i=0; i<nList.getLength(); i++)
		{
			Node child = nList.item(i);
			if (child.getNodeName().compareTo(Utils.ELEMENT_FIELD) == 0)
			{
				String fieldName = converter.prepareDomainName(Utils.getNamedAttribute(child,
						Utils.ATTRIBUTE_NAME));
				AmqpField thisField = fieldMap.get(fieldName);
				if (thisField == null)
				{
					thisField = new AmqpField(fieldName, converter);
					fieldMap.put(fieldName, thisField);
				}
				if (!thisField.addFromNode(child, fieldCntr++, version))
				{
					String className = converter.prepareClassName(Utils.getNamedAttribute(classNode,
							Utils.ATTRIBUTE_NAME));
					System.out.println("INFO: Generation supression tag found for field " +
							className + "." + fieldName + " - removing.");
					thisField.removeVersion(version);
					fieldMap.remove(fieldName);
				}
			}
			else if (child.getNodeName().compareTo(Utils.ELEMENT_METHOD) == 0)
			{
				String methodName = converter.prepareMethodName(Utils.getNamedAttribute(child,
						Utils.ATTRIBUTE_NAME));
				AmqpMethod thisMethod = methodMap.get(methodName);
				if (thisMethod == null)
				{
					thisMethod = new AmqpMethod(methodName, converter);
					methodMap.put(methodName, thisMethod);
				}			
				if (!thisMethod.addFromNode(child, 0, version))
				{
					String className = converter.prepareClassName(Utils.getNamedAttribute(classNode,
							Utils.ATTRIBUTE_NAME));
					System.out.println("INFO: Generation supression tag found for method " +
							className + "." + methodName + " - removing.");
					thisMethod.removeVersion(version);
					methodMap.remove(methodName);
				}
			}
			else if (child.getNodeName().compareTo(Utils.ELEMENT_CODEGEN) == 0)
			{
				String value = Utils.getNamedAttribute(child, Utils.ATTRIBUTE_VALUE);
				if (value.compareTo("no-gen") == 0)
					return false;
			}
		}
		return true;
	}
	
	public void removeVersion(AmqpVersion version)
	{
		indexMap.removeVersion(version);
		fieldMap.removeVersion(version);
		methodMap.removeVersion(version);
		versionSet.remove(version);
	}
	
	public void print(PrintStream out, int marginSize, int tabSize)
	{
		String margin = Utils.createSpaces(marginSize);
		String tab = Utils.createSpaces(tabSize);
		out.println(margin + "[C] " + name + ": " + versionSet);
		
		for (Integer thisIndex : indexMap.keySet())
		{
			AmqpVersionSet indexVersionSet = indexMap.get(thisIndex);
			out.println(margin + tab + "[I] " + thisIndex + indexVersionSet);
		}
		
		for (String thisFieldName : fieldMap.keySet())
		{
			AmqpField thisField = fieldMap.get(thisFieldName);
			thisField.print(out, marginSize + tabSize, tabSize);
		}
		
		for (String thisMethodName : methodMap.keySet())
		{
			AmqpMethod thisMethod = methodMap.get(thisMethodName);
			thisMethod.print(out, marginSize + tabSize, tabSize);
		}
	}
}
