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
import java.util.TreeMap;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@SuppressWarnings("serial")
public class AmqpDomainMap extends TreeMap<String, AmqpDomain> implements Printable, NodeAware
{
	public LanguageConverter converter;

	public AmqpDomainMap(LanguageConverter converter)
	{
		this.converter = converter;
		this.converter.setDomainMap(this);
	}
	
	public void addFromNode(Node n, int o, AmqpVersion v)
		throws AmqpParseException, AmqpTypeMappingException
	{
		NodeList nl = n.getChildNodes();
		for (int i=0; i<nl.getLength(); i++)
		{
			Node c = nl.item(i);
			// All versions 0.9 and greater use <domain> for all domains
			if (c.getNodeName().compareTo(Utils.ELEMENT_DOMAIN) == 0)
			{
				String domainName = converter.prepareDomainName(Utils.getNamedAttribute(c, Utils.ATTRIBUTE_NAME));
				String type = Utils.getNamedAttribute(c, Utils.ATTRIBUTE_TYPE);
				AmqpDomain thisDomain = get(domainName);
				if (thisDomain == null)
				{
					thisDomain = new AmqpDomain(domainName);
					put(domainName, thisDomain);
				}
				thisDomain.addDomain(type, v);
			}
			// Version(s) 0.8 and earlier use <domain> for all complex domains and use
			// attribute <field type=""...> for simple types. Add these simple types to
			// domain list - but beware of duplicates!
			else if (c.getNodeName().compareTo(Utils.ELEMENT_FIELD) == 0)
			{
				try
				{
					String type = converter.prepareDomainName(Utils.getNamedAttribute(c, Utils.ATTRIBUTE_TYPE));
					AmqpDomain thisDomain = get(type);
					if (thisDomain == null)
					{
						thisDomain = new AmqpDomain(type);
						put(type, thisDomain);
					}
					if (!thisDomain.hasVersion(type, v))
						thisDomain.addDomain(type, v);
				}
				catch (AmqpParseException e) {} // Ignore fields without type attribute
			}
			else if (c.getNodeName().compareTo(Utils.ELEMENT_CLASS) == 0 ||
					 c.getNodeName().compareTo(Utils.ELEMENT_METHOD) == 0)
			{
				addFromNode(c, 0, v);
			}
		}	
	}

	public String getDomainType(String domainName, AmqpVersion version)
	    throws AmqpTypeMappingException
	{
		AmqpDomain domainType = get(domainName);
		// For AMQP 8.0, primitive types were not described as domains, so
		// return itself as the type.
		if (domainType == null)
		{
//			return converter.getDomainType(domainName, version);
System.out.println("@DEBUG Unable to find domain " + domainName);
			return domainName;
		}
		try
		{
			return domainType.getDomainType(version);
		}
		catch (AmqpTypeMappingException e)
		{
			throw new AmqpTypeMappingException("Unable to find domain type for domain \"" + domainName +
				"\" version " + version + ".");
		}
	}
	
	
	public void print(PrintStream out, int marginSize, int tabSize)
	{
		Iterator<String> i = keySet().iterator();
		out.println(Utils.createSpaces(marginSize) + "Domain Map:");
		while (i.hasNext())
		{
			String domainName = i.next();
			AmqpDomain domain = get(domainName);
			domain.print(out, marginSize + tabSize, tabSize);
		}
	}
}
