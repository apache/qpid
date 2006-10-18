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

import java.util.TreeMap;

public class CppGenerator extends Generator
{
	private class DomainInfo
	{
		public String type;
		public String size;
		public DomainInfo(String domain, String size)
		{
			this.type = domain;
			this.size = size;
		}
	}
	
	private static TreeMap<String, DomainInfo> typeMap = new TreeMap<String, DomainInfo>();

	public CppGenerator(AmqpVersionSet versionList)
	{
		super(versionList);
		// Load C++ type and size maps.
		// Adjust or add to these lists as new types are added/defined.
		// The char '#' will be replaced by the field variable name.
		typeMap.put("bit", new DomainInfo(
				"bool",					// domain
				"1")); 					// size
		typeMap.put("long", new DomainInfo(
				"u_int32_t",			// domain
				"4")); 					// size
		typeMap.put("longlong", new DomainInfo(
				"u_int64_t",			// domain
				"8")); 					// size
		typeMap.put("longstr", new DomainInfo(
				"string",				// domain
				"4 + #.length()")); 	// size
		typeMap.put("octet", new DomainInfo(
				"u_int8_t",				// domain
				"1")); 					// size
		typeMap.put("short", new DomainInfo(
				"u_int16_t",			// domain
				"2")); 					// size
		typeMap.put("shortstr", new DomainInfo(
				"string",				// domain
				"1 + #.length()"));		// size
		typeMap.put("table", new DomainInfo(
				"FieldTable",			// domain
				"#.size()"));			// size
		typeMap.put("timestamp", new DomainInfo(
				"u_int64_t",			// domain
				"8")); 					// decode expression
	}
	
	// === Start of methods for Interface LanguageConverter ===
	
	public String prepareClassName(String className)
	{
		return camelCaseName(className, true);
	}
	
	public String prepareMethodName(String methodName)
	{
		return camelCaseName(methodName, false);		
	}
	
	public String prepareDomainName(String domainName)
	{
		return camelCaseName(domainName, false);		
	}
	
	public String getDomainType(String domainType, AmqpVersion version)
		throws AmqpTypeMappingException
	{
		String domain = globalDomainMap.getDomainType(domainType, version);
		String type = typeMap.get(domain).type;
		if (type == null)
			throw new AmqpTypeMappingException("Domain type \"" + domainType + "\" not found in Java typemap.");
		return type;
	}
	
	public String getGeneratedType(String domainName, AmqpVersion version)
		throws AmqpTypeMappingException
	{
		String domainType = getDomainType(domainName, version);
		return typeMap.get(domainType).type;
	}

	// === Abstract methods from class Generator - C++-specific implementation ===

	@Override
	protected String prepareFilename(String filenameTemplate, AmqpClass thisClass, AmqpMethod method,
			AmqpField field)
	{
		StringBuffer sb = new StringBuffer(filenameTemplate);
		if (thisClass != null)
			replaceToken(sb, "${CLASS}", thisClass.name);
		if (method != null)
			replaceToken(sb, "${METHOD}", method.name);
		if (field != null)
			replaceToken(sb, "${FIELD}", field.name);
		return sb.toString();
	}

	@Override
	protected String processToken(String snipitKey, AmqpClass thisClass, AmqpMethod method, AmqpField field)
	    throws AmqpTemplateException
	{
		if (snipitKey.compareTo("${property_flags_initializer}") == 0)
		{
			StringBuffer sb = new StringBuffer();
			// TODO
			return sb.toString();
		}
		throw new AmqpTemplateException("Template token " + snipitKey + " unknown.");	
	}
	
	@Override
	protected void processClassList(StringBuffer sb, int tokStart, int tokEnd, AmqpModel model)
        throws AmqpTemplateException
	{
// 		TODO
	}
	
	@Override
	protected void processMethodList(StringBuffer sb, int tokStart, int tokEnd, AmqpClass thisClass)
        throws AmqpTemplateException
	{
//		TODO
	}
	
	@Override
	protected void processFieldList(StringBuffer sb, int listMarkerStartIndex, int listMarkerEndIndex,
		AmqpFieldMap fieldMap)
        throws AmqpTypeMappingException, AmqpTemplateException
	{
//		TODO
	}

//	@Override
//	protected String generateFieldDeclaration(AmqpFieldMap fieldMap, int indentSize)
//	    throws AmqpTypeMappingException
//	{
//		String indent = Utils.createSpaces(indentSize);
//		StringBuffer sb = new StringBuffer(indent + "// [FieldDeclaration]" + Utils.lineSeparator);
//		// TODO
//		return sb.toString();
//	}
//
//	@Override
//	protected String generateFieldGetMethod(AmqpFieldMap fieldMap, int indentSize, int tabSize)
//	    throws AmqpTypeMappingException
//	{
//		String indent = Utils.createSpaces(indentSize);
////		String tab = Utils.createSpaces(tabSize);
//		StringBuffer sb = new StringBuffer(indent + "// [FieldGetMethod]" + Utils.lineSeparator);
//		// TODO
//		return sb.toString();
//	}
//
//	@Override
//	protected String generateContentHeaderGetSetMethod(AmqpFieldMap fieldMap, int indentSize,
//		int tabSize)
//	    throws AmqpTypeMappingException
//	{
//		String indent = Utils.createSpaces(indentSize);
////		String tab = Utils.createSpaces(tabSize);
//		StringBuffer sb = new StringBuffer(indent + "// Property get/set methods" + Utils.lineSeparator);
//		// TODO
//		return sb.toString();
//	}
//
//	@Override
//	protected String generateCodeSnippet(String token, AmqpFieldMap fieldMap, int indentSize,
//		int tabSize)
//	    throws AmqpTypeMappingException
//	{
//		String indent = Utils.createSpaces(indentSize);
////		String tab = Utils.createSpaces(tabSize);
//		StringBuffer sb = new StringBuffer(indent + "// [Code snippet " + token + "]" + Utils.lineSeparator);
//		// TODO
//		return sb.toString();
//	}
	
	// Private helper functions unique to C++
		
	private String camelCaseName(String name, boolean upperFirstFlag)
	{
		StringBuffer ccn = new StringBuffer();
		String[] toks = name.split("[-_.\\ ]");
		for (int i=0; i<toks.length; i++)
		{
			StringBuffer b = new StringBuffer(toks[i]);
			if (upperFirstFlag || i>0)
				b.setCharAt(0, Character.toUpperCase(toks[i].charAt(0)));
			ccn.append(b);
		}
		return ccn.toString();
	}
}
