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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.TreeMap;

public class DotnetGenerator extends Generator
{
	private class DomainInfo
	{
		public String type;
		public String size;
		public String encodeExpression;
		public String decodeExpression;
		public DomainInfo(String domain, String size, String encodeExpression, String decodeExpression)
		{
			this.type = domain;
			this.size = size;
			this.encodeExpression = encodeExpression;
			this.decodeExpression = decodeExpression;
		}
	}
	
	private static TreeMap<String, DomainInfo> typeMap = new TreeMap<String, DomainInfo>();

	public DotnetGenerator(AmqpVersionSet versionList)
	{
		super(versionList);
		// Load .NET type and size maps.
		// Adjust or add to these lists as new types are added/defined.
		// The char '#' will be replaced by the field variable name (any type).
		// The char '~' will be replaced by the compacted bit array size (type bit only).
		// TODO: I have left a copy of the Java typeMap here - replace with appropriate .NET values.
		typeMap.put("bit", new DomainInfo(
			"boolean",										// .NET code type
			"~",											// size
			"EncodingUtils.writeBooleans(buffer, #)",		// encode expression
			"# = EncodingUtils.readBooleans(buffer)"));		// decode expression
		typeMap.put("content", new DomainInfo(
			"Content",										// .NET code type
			"EncodingUtils.encodedContentLength(#)", 	// size
			"EncodingUtils.writeContentBytes(buffer, #)", // encode expression
			"# = EncodingUtils.readContent(buffer)"));	// decode expression
		typeMap.put("long", new DomainInfo(
			"long",											// .NET code type
			"4",											// size
			"EncodingUtils.writeUnsignedInteger(buffer, #)", // encode expression
			"# = buffer.getUnsignedInt()")); 				// decode expression
		typeMap.put("longlong", new DomainInfo(
			"long",											// .NET code type
			"8",											// size
			"buffer.putLong(#)", 							// encode expression
			"# = buffer.getLong()")); 						// decode expression
		typeMap.put("longstr", new DomainInfo(
			"byte[]",										// .NET code type
			"EncodingUtils.encodedLongstrLength(#)", 		// size
			"EncodingUtils.writeLongStringBytes(buffer, #)", // encode expression
			"# = EncodingUtils.readLongstr(buffer)"));		// decode expression
		typeMap.put("octet", new DomainInfo(
			"short",										// .NET code type
			"1",											// size
			"EncodingUtils.writeUnsignedByte(buffer, #)",	// encode expression
			"# = buffer.getUnsigned()")); 					// decode expression
		typeMap.put("short", new DomainInfo(
			"int",											// .NET code type
			"2",											// size
			"EncodingUtils.writeUnsignedShort(buffer, #)",	// encode expression
			"# = buffer.getUnsignedShort()")); 				// decode expression
		typeMap.put("shortstr", new DomainInfo(
			"AMQShortString",								// .NET code type
			"EncodingUtils.encodedShortStringLength(#)",	// size
			"EncodingUtils.writeShortStringBytes(buffer, #)", // encode expression
			"# = EncodingUtils.readAMQShortString(buffer)"));	// decode expression
		typeMap.put("table", new DomainInfo(
			"FieldTable",									// .NET code type
			"EncodingUtils.encodedFieldTableLength(#)", 	// size
			"EncodingUtils.writeFieldTableBytes(buffer, #)", // encode expression
			"# = EncodingUtils.readFieldTable(buffer)"));	// decode expression
		typeMap.put("timestamp", new DomainInfo(
			"long",											// .NET code type
			"8",											// size
			"EncodingUtils.writeTimestamp(buffer, #)",		// encode expression
			"# = EncodingUtils.readTimestamp(buffer)"));	// decode expression
	}

	@Override
	protected String prepareFilename(String filenameTemplate,
			AmqpClass thisClass, AmqpMethod method, AmqpField field)
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
	protected void processClassList(StringBuffer sb, int listMarkerStartIndex,
			int listMarkerEndIndex, AmqpModel model)
			throws AmqpTemplateException, AmqpTypeMappingException
	{
		String codeSnippet;
		int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
		String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
		int tokStart = tline.indexOf('$');
		String token = tline.substring(tokStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		// TODO: Add in tokens and calls to their corresponding generator methods here...
		if (token.compareTo("${??????????}") == 0)
		{
			codeSnippet = token; // This is a stub to get the compile working - remove when gen method is present.
//			codeSnippet = generateRegistry(model, 8, 4); 
		}
		
		else // Oops!
		{
			throw new AmqpTemplateException("Template token " + token + " unknown.");
		}
		sb.insert(listMarkerStartIndex, codeSnippet);
	}

	@Override
	protected void processConstantList(StringBuffer sb,
			int listMarkerStartIndex, int listMarkerEndIndex,
			AmqpConstantSet constantSet) throws AmqpTemplateException,
			AmqpTypeMappingException
	{
        String codeSnippet;
        int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
        String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
        int tokStart = tline.indexOf('$');
        String token = tline.substring(tokStart).trim();
        sb.delete(listMarkerStartIndex, lend);

		// TODO: Add in tokens and calls to their corresponding generator methods here...
        if (token.compareTo("${??????????}") == 0)
        {
			codeSnippet = token; // This is a stub to get the compile working - remove when gen method is present.
//            codeSnippet = generateConstantGetMethods(constantSet, 4, 4); 
        }
       
        else // Oops!
        {
            throw new AmqpTemplateException("Template token " + token + " unknown.");
        }
        sb.insert(listMarkerStartIndex, codeSnippet);
	}

	@Override
	protected void processFieldList(StringBuffer sb, int listMarkerStartIndex,
			int listMarkerEndIndex, AmqpFieldMap fieldMap, AmqpVersion version)
			throws AmqpTypeMappingException, AmqpTemplateException,
			IllegalAccessException, InvocationTargetException
	{
		String codeSnippet;
		int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
		String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
		int tokStart = tline.indexOf('$');
		String token = tline.substring(tokStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		// TODO: Add in tokens and calls to their corresponding generator methods here...
		if (token.compareTo("${??????????}") == 0)
		{
			codeSnippet = token; // This is a stub to get the compile working - remove when gen method is present.
//			codeSnippet = fieldMap.parseFieldMap(declarationGenerateMethod,
//				mangledDeclarationGenerateMethod, 4, 4, this);
		}
		
		else // Oops!
		{
			throw new AmqpTemplateException("Template token " + token + " unknown.");
		}
		sb.insert(listMarkerStartIndex, codeSnippet);
	}

	@Override
	protected void processMethodList(StringBuffer sb, int listMarkerStartIndex,
			int listMarkerEndIndex, AmqpClass thisClass)
			throws AmqpTemplateException, AmqpTypeMappingException
	{
		String codeSnippet;
		int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
		String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
		int tokStart = tline.indexOf('$');
		String token = tline.substring(tokStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		// TODO: Add in tokens and calls to their corresponding generator methods here...
		if (token.compareTo("${??????????}") == 0)
		{
			codeSnippet = token; // This is a stub to get the compile working - remove when gen method is present.
		}
		
		else // Oops!
		{
			throw new AmqpTemplateException("Template token " + token + " unknown.");
		}
		sb.insert(listMarkerStartIndex, codeSnippet);
	}

	@Override
	protected void processTemplateA(String[] template) throws IOException,
			AmqpTemplateException, AmqpTypeMappingException,
			IllegalAccessException, InvocationTargetException
	{
		// I've put in the Java model here - this can be changed if a different pattern is required.
		processTemplateD(template, null, null, null);
	}

	@Override
	protected void processTemplateB(String[] template, AmqpClass thisClass)
			throws IOException, AmqpTemplateException,
			AmqpTypeMappingException, IllegalAccessException,
			InvocationTargetException
	{
		// I've put in the Java model here - this can be changed if a different pattern is required.
		processTemplateD(template, thisClass, null, null);
	}

	@Override
	protected void processTemplateC(String[] template, AmqpClass thisClass,
			AmqpMethod method) throws IOException, AmqpTemplateException,
			AmqpTypeMappingException, IllegalAccessException,
			InvocationTargetException
	{
		// I've put in the Java model here - this can be changed if a different pattern is required.
		processTemplateD(template, thisClass, method, null);
	}

	@Override
	protected void processTemplateD(String[] template, AmqpClass thisClass,
			AmqpMethod method, AmqpField field) throws IOException,
			AmqpTemplateException, AmqpTypeMappingException,
			IllegalAccessException, InvocationTargetException
	{
		// I've put in the Java model here - this can be changed if a different pattern is required.
		StringBuffer sb = new StringBuffer(template[1]);
		String filename = prepareFilename(getTemplateFileName(sb), thisClass, method, field);
		try { processAllLists(sb, thisClass, method, null); }
		catch (AmqpTemplateException e)
		{
			System.out.println("WARNING: " + template[templateFileNameIndex] + ": " + e.getMessage());
		}
		try { processAllTokens(sb, thisClass, method, field, null); }
		catch (AmqpTemplateException e)
		{
			System.out.println("WARNING: " + template[templateFileNameIndex] + ": " + e.getMessage());
		}
		writeTargetFile(sb, new File(genDir + Utils.fileSeparator + filename));
		generatedFileCounter ++;
	}

	@Override
	protected String processToken(String token, AmqpClass thisClass,
			AmqpMethod method, AmqpField field, AmqpVersion version)
			throws AmqpTemplateException, AmqpTypeMappingException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public String getDomainType(String domainName, AmqpVersion version)
			throws AmqpTypeMappingException
	{
		return globalDomainMap.getDomainType(domainName, version);
	}

	public String getGeneratedType(String domainName, AmqpVersion version)
			throws AmqpTypeMappingException
	{
		String domainType = globalDomainMap.getDomainType(domainName, version);
		if (domainType == null)
        {
			throw new AmqpTypeMappingException("Domain type \"" + domainName +
                "\" not found in Java typemap.");
        }
        DomainInfo info = typeMap.get(domainType);
        if (info == null)
        {
            throw new AmqpTypeMappingException("Unknown domain: \"" + domainType + "\"");
        }
		return info.type;
	}

	public String prepareClassName(String className)
	{
		return camelCaseName(className, true);
	}

	public String prepareDomainName(String domainName)
	{
		return camelCaseName(domainName, false);
	}

	public String prepareMethodName(String methodName)
	{
		return camelCaseName(methodName, false);
	}

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
