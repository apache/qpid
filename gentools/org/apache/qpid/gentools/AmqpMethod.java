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

	public void addFromNode(Node methodNode, int ordinal, AmqpVersion version)
		throws AmqpParseException, AmqpTypeMappingException
	{
		boolean serverChassisFlag = false;
		boolean clientChassisFlag = false;
		versionSet.add(version);
		int index = Utils.getNamedIntegerAttribute(methodNode, "index");
		AmqpVersionSet versionSet = indexMap.get(index);
		if (versionSet != null)
			versionSet.add(version);
		else
		{
			versionSet = new AmqpVersionSet();
			versionSet.add(version);
			indexMap.put(index, versionSet);
		}
		NodeList nList = methodNode.getChildNodes();
		int fieldCntr = 0;
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
				thisField.addFromNode(child, fieldCntr++, version);				
			}
			if (child.getNodeName().compareTo(Utils.ELEMENT_CHASSIS) == 0)
			{
				String chassisName = Utils.getNamedAttribute(child, Utils.ATTRIBUTE_NAME);
				if (chassisName.compareTo("server") == 0)
					serverChassisFlag = true;
				else if (chassisName.compareTo("client") == 0)
					clientChassisFlag = true;
			}
		}
		processChassisFlags(serverChassisFlag, clientChassisFlag, version);
	}
	
	public void print(PrintStream out, int marginSize, int tabSize)
	{
		String margin = Utils.createSpaces(marginSize);
		String tab = Utils.createSpaces(tabSize);
		out.println(margin + "[M] " + name + " {" + (serverMethodFlagMap.isSet() ? "S " +
			serverMethodFlagMap + (clientMethodFlagMap.isSet() ? ", " : "") : "") +
			(clientMethodFlagMap.isSet() ? "C " + clientMethodFlagMap : "") + "}" + ": " + versionSet);
		
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
