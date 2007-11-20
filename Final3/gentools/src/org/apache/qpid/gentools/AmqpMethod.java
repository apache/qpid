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

public class AmqpMethod implements Printable, NodeAware, VersionConsistencyCheck
{
	public LanguageConverter converter;
	public AmqpVersionSet versionSet;
	public AmqpFieldMap fieldMap;
	public String name;
	public AmqpOrdinalVersionMap indexMap;
	public AmqpFlagMap clientMethodFlagMap; // Method called on client (<chassis name="server"> in XML)
	public AmqpFlagMap serverMethodFlagMap; // Method called on server (<chassis name="client"> in XML)
	
	public AmqpMethod(String name, LanguageConverter converter)
	{
		this.name = name;
		this.converter = converter;
		versionSet = new AmqpVersionSet();
		fieldMap = new AmqpFieldMap();
		indexMap = new AmqpOrdinalVersionMap();
		clientMethodFlagMap = new AmqpFlagMap();
		serverMethodFlagMap = new AmqpFlagMap();
	}

	public boolean addFromNode(Node methodNode, int ordinal, AmqpVersion version)
		throws AmqpParseException, AmqpTypeMappingException
	{
		versionSet.add(version);
		boolean serverChassisFlag = false;
		boolean clientChassisFlag = false;
		int index = Utils.getNamedIntegerAttribute(methodNode, "index");
		AmqpVersionSet indexVersionSet = indexMap.get(index);
		if (indexVersionSet != null)
			indexVersionSet.add(version);
		else
		{
			indexVersionSet = new AmqpVersionSet();
			indexVersionSet.add(version);
			indexMap.put(index, indexVersionSet);
		}
		NodeList nList = methodNode.getChildNodes();
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
					String className = converter.prepareClassName(Utils.getNamedAttribute(methodNode.getParentNode(),
							Utils.ATTRIBUTE_NAME));
					String methodName = converter.prepareMethodName(Utils.getNamedAttribute(methodNode,
							Utils.ATTRIBUTE_NAME));
					System.out.println("INFO: Generation supression tag found for field " +
							className + "." + methodName + "." + fieldName + " - removing.");
					thisField.removeVersion(version);
					fieldMap.remove(fieldName);
				}
			}
			else if (child.getNodeName().compareTo(Utils.ELEMENT_CHASSIS) == 0)
			{
				String chassisName = Utils.getNamedAttribute(child, Utils.ATTRIBUTE_NAME);
				if (chassisName.compareTo("server") == 0)
					serverChassisFlag = true;
				else if (chassisName.compareTo("client") == 0)
					clientChassisFlag = true;
			}
			else if (child.getNodeName().compareTo(Utils.ELEMENT_CODEGEN) == 0)
			{
				String value = Utils.getNamedAttribute(child, Utils.ATTRIBUTE_VALUE);
				if (value.compareTo("no-gen") == 0)
					return false;
			}
		}
		processChassisFlags(serverChassisFlag, clientChassisFlag, version);
		return true;
	}
	
	public void removeVersion(AmqpVersion version)
	{
		clientMethodFlagMap.removeVersion(version);
		serverMethodFlagMap.removeVersion(version);
		indexMap.removeVersion(version);
		fieldMap.removeVersion(version);
		versionSet.remove(version);
	}
	
	public void print(PrintStream out, int marginSize, int tabSize)
	{
		String margin = Utils.createSpaces(marginSize);
		String tab = Utils.createSpaces(tabSize);
		out.println(margin + "[M] " + name + " {" + (serverMethodFlagMap.isSet() ? "S " +
			serverMethodFlagMap + (clientMethodFlagMap.isSet() ? ", " : "") : "") +
			(clientMethodFlagMap.isSet() ? "C " + clientMethodFlagMap : "") + "}" + ": " +
			versionSet);
		
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
	}
	
	protected void processChassisFlags(boolean serverFlag, boolean clientFlag, AmqpVersion version)
	{
		AmqpVersionSet versionSet = serverMethodFlagMap.get(serverFlag);
		if (versionSet != null)
			versionSet.add(version);
		else
		{
			versionSet = new AmqpVersionSet();
			versionSet.add(version);
			serverMethodFlagMap.put(serverFlag, versionSet);
		}
		
		versionSet = clientMethodFlagMap.get(clientFlag);
		if (versionSet != null)
			versionSet.add(version);
		else
		{
			versionSet = new AmqpVersionSet();
			versionSet.add(version);
			clientMethodFlagMap.put(clientFlag, versionSet);
		}		
	}
	
	public AmqpOverloadedParameterMap getOverloadedParameterLists(AmqpVersionSet globalVersionSet,
		Generator generator)
		throws AmqpTypeMappingException
	{
		AmqpOverloadedParameterMap parameterVersionMap = new AmqpOverloadedParameterMap();
		for (AmqpVersion thisVersion : globalVersionSet)
		{
			AmqpOrdinalFieldMap ordinalFieldMap = fieldMap.getMapForVersion(thisVersion, true, generator);
			AmqpVersionSet methodVersionSet = parameterVersionMap.get(ordinalFieldMap);
			if (methodVersionSet == null)
			{
				methodVersionSet = new AmqpVersionSet();
				methodVersionSet.add(thisVersion);
				parameterVersionMap.put(ordinalFieldMap, methodVersionSet);
			}
			else
			{
				methodVersionSet.add(thisVersion);
			}
		}
		return parameterVersionMap;
	}
		
	public boolean isVersionConsistent(AmqpVersionSet globalVersionSet)
	{
		if (!versionSet.equals(globalVersionSet))
			return false;
		if (!clientMethodFlagMap.isVersionConsistent(globalVersionSet))
			return false;
		if (!serverMethodFlagMap.isVersionConsistent(globalVersionSet))
			return false;
		if (!indexMap.isVersionConsistent(globalVersionSet))
			return false;
		if (!fieldMap.isVersionConsistent(globalVersionSet))
			return false;
		return true;
	}
}
