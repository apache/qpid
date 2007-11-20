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
import java.util.ArrayList;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AmqpField implements Printable, NodeAware, VersionConsistencyCheck
{
	public LanguageConverter converter;
	public AmqpVersionSet versionSet;
	public AmqpDomainVersionMap domainMap;
	public AmqpOrdinalVersionMap ordinalMap;
	public String name;
	
	public AmqpField(String name, LanguageConverter converter)
	{
		this.name = name;
		this.converter = converter;
		versionSet = new AmqpVersionSet();
		domainMap = new AmqpDomainVersionMap();
		ordinalMap = new AmqpOrdinalVersionMap();
	}

	public boolean addFromNode(Node fieldNode, int ordinal, AmqpVersion version)
		throws AmqpParseException, AmqpTypeMappingException
	{
		versionSet.add(version);
		String domainType;
		// Early versions of the spec (8.0) used the "type" attribute instead of "domain" for some fields.
		try
		{
			domainType = converter.prepareDomainName(Utils.getNamedAttribute(fieldNode, Utils.ATTRIBUTE_DOMAIN));
		}
		catch (AmqpParseException e)
		{
			domainType = converter.prepareDomainName(Utils.getNamedAttribute(fieldNode, Utils.ATTRIBUTE_TYPE));
		}
		AmqpVersionSet thisVersionList = domainMap.get(domainType);
		if (thisVersionList == null) // First time, create new entry
		{
			thisVersionList = new AmqpVersionSet();
			domainMap.put(domainType, thisVersionList);
		}
		thisVersionList.add(version);
		thisVersionList = ordinalMap.get(ordinal);
		if (thisVersionList == null) // First time, create new entry
		{
			thisVersionList = new AmqpVersionSet();
			ordinalMap.put(ordinal, thisVersionList);
		}
		thisVersionList.add(version);
		NodeList nList = fieldNode.getChildNodes();
		for (int i=0; i<nList.getLength(); i++)
		{
			Node child = nList.item(i);
			if (child.getNodeName().compareTo(Utils.ELEMENT_CODEGEN) == 0)
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
		domainMap.removeVersion(version);
		ordinalMap.removeVersion(version);
		versionSet.remove(version);
	}
	
	public boolean isCodeTypeConsistent(LanguageConverter converter)
	    throws AmqpTypeMappingException
	{
		if (domainMap.size() == 1)
			return true; // By definition
		ArrayList<String> codeTypeList = new ArrayList<String>();
		for (String thisDomainName : domainMap.keySet())
		{
			AmqpVersionSet versionSet = domainMap.get(thisDomainName);
			String codeType = converter.getGeneratedType(thisDomainName, versionSet.first());
			if (!codeTypeList.contains(codeType))
				codeTypeList.add(codeType);
		}
		return codeTypeList.size() == 1;
	}
	
	public boolean isConsistent(Generator generator)
        throws AmqpTypeMappingException
	{
		if (!isCodeTypeConsistent(generator))
			return false;
		if (ordinalMap.size() != 1)
			return false;
		// Since the various doamin names map to the same code type, add the version occurrences
		// across all domains to see we have all possible versions covered
		int vCntr = 0;
		for (String thisDomainName : domainMap.keySet())
		{
			vCntr += domainMap.get(thisDomainName).size();
		}
		return vCntr == generator.globalVersionSet.size();
	}
	
	public void print(PrintStream out, int marginSize, int tabSize)
	{
		String margin = Utils.createSpaces(marginSize);
		out.println(margin + "[F] " + name + ": " + versionSet);

		for (Integer thisOrdinal : ordinalMap.keySet())
		{
			AmqpVersionSet versionList = ordinalMap.get(thisOrdinal);
			out.println(margin + "  [O] " + thisOrdinal + " : " + versionList.toString());
		}

		for (String thisDomainName : domainMap.keySet())
		{
			AmqpVersionSet versionList = domainMap.get(thisDomainName);
			out.println(margin + "  [D] " + thisDomainName + " : " + versionList.toString());
		}
	}
	
	public boolean isVersionConsistent(AmqpVersionSet globalVersionSet)
	{
		if (!versionSet.equals(globalVersionSet))
			return false;
		if (!domainMap.isVersionConsistent(globalVersionSet))
			return false;
		if (!ordinalMap.isVersionConsistent(globalVersionSet))
			return false;
		return true;
	}
}
