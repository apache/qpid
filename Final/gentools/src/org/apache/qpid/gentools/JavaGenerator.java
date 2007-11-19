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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

public class JavaGenerator extends Generator
{
	// TODO: Move this to parent class
	protected static final int FIELD_NAME = 0;
	protected static final int FIELD_CODE_TYPE = 1;
	
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

	// Methods used for generation of code snippets called from the field map parsers
	
	// Common methods
	static private Method declarationGenerateMethod;
	static private Method mangledDeclarationGenerateMethod;
	
	// Methods for MessageBody classes
	static private Method mbGetGenerateMethod;
	static private Method mbMangledGetGenerateMethod;
	static private Method mbParamListGenerateMethod;
	static private Method mbPassedParamListGenerateMethod;
	static private Method mbMangledParamListGenerateMethod;
	static private Method mbMangledPassedParamListGenerateMethod;
	static private Method mbBodyInitGenerateMethod;
	static private Method mbMangledBodyInitGenerateMethod;
	static private Method mbSizeGenerateMethod;
	static private Method mbBitSizeGenerateMethod;
	static private Method mbEncodeGenerateMethod;
	static private Method mbBitEncodeGenerateMethod;
	static private Method mbDecodeGenerateMethod;
	static private Method mbBitDecodeGenerateMethod;
	static private Method mbToStringGenerateMethod;
	static private Method mbBitToStringGenerateMethod;
	
	// Methods for PropertyContentHeader classes
	static private Method pchClearGenerateMethod;
	static private Method pchMangledClearGenerateMethod;
	static private Method pchGetGenerateMethod;
	static private Method pchMangledGetGenerateMethod;
	static private Method pchSetGenerateMethod;
	static private Method pchMangledSetGenerateMethod;
	static private Method pchSizeGenerateMethod;
	static private Method pchBitSizeGenerateMethod;
	static private Method pchEncodeGenerateMethod;
	static private Method pchBitEncodeGenerateMethod;
	static private Method pchDecodeGenerateMethod;
	static private Method pchBitDecodeGenerateMethod;
	static private Method pchGetPropertyFlagsGenerateMethod;
	static private Method pchBitGetPropertyFlagsGenerateMethod;
	static private Method pchSetPropertyFlagsGenerateMethod;
	static private Method pchBitSetPropertyFlagsGenerateMethod;
	
	static 
	{
		// **************
		// Common methods
		// **************
		
		// Methods for AmqpFieldMap.parseFieldMap()
		
		try { declarationGenerateMethod = JavaGenerator.class.getDeclaredMethod(
		    "generateFieldDeclaration", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mangledDeclarationGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMangledFieldDeclaration", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		
		// *******************************
		// Methods for MessageBody classes
		// *******************************
		
		// Methods for AmqpFieldMap.parseFieldMap()
		
		try { mbGetGenerateMethod = JavaGenerator.class.getDeclaredMethod(
		    "generateMbGetMethod", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbMangledGetGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbMangledGetMethod", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }

		try { mbParamListGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbParamList", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		
		try { mbPassedParamListGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbPassedParamList", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbMangledParamListGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbMangledParamList", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbMangledPassedParamListGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbMangledPassedParamList", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbBodyInitGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbBodyInit", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbMangledBodyInitGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbMangledBodyInit", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		// Methods for AmqpFieldMap.parseFieldMapOrdinally()
		
		try { mbSizeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbFieldSize", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbBitSizeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbBitArrayFieldSize", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbEncodeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbFieldEncode", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbBitEncodeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbBitFieldEncode", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbDecodeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbFieldDecode", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbBitDecodeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbBitFieldDecode", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbToStringGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbFieldToString", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbBitToStringGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generateMbBitFieldToString", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		// *****************************************
		// Methods for PropertyContentHeader classes
		// *****************************************
		
		// Methods for AmqpFieldMap.parseFieldMap()

		try { pchClearGenerateMethod = JavaGenerator.class.getDeclaredMethod(
		    "generatePchClearMethod", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchMangledClearGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchMangledClearMethod", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }

		try { pchSetGenerateMethod = JavaGenerator.class.getDeclaredMethod(
		    "generatePchSetMethod", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchMangledSetGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchMangledSetMethod", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }

		try { pchGetGenerateMethod = JavaGenerator.class.getDeclaredMethod(
		    "generatePchGetMethod", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchMangledGetGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchMangledGetMethod", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		// Methods for AmqpFieldMap.parseFieldMapOrdinally()
			
		try { pchSizeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchFieldSize", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchBitSizeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchBitArrayFieldSize", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchEncodeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchFieldEncode", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchBitEncodeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchBitFieldEncode", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchDecodeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchFieldDecode", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchBitDecodeGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchBitFieldDecode", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchGetPropertyFlagsGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchGetPropertyFlags", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchBitGetPropertyFlagsGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchBitGetPropertyFlags", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchSetPropertyFlagsGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchSetPropertyFlags", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { pchBitSetPropertyFlagsGenerateMethod = JavaGenerator.class.getDeclaredMethod(
			"generatePchBitSetPropertyFlags", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
	}
	
	public JavaGenerator(AmqpVersionSet versionList)
	{
		super(versionList);
		// Load Java type and size maps.
		// Adjust or add to these lists as new types are added/defined.
		// The char '#' will be replaced by the field variable name (any type).
		// The char '~' will be replaced by the compacted bit array size (type bit only).
		typeMap.put("bit", new DomainInfo(
			"boolean",										// Java code type
			"~",											// size
			"EncodingUtils.writeBooleans(buffer, #)",		// encode expression
			"# = EncodingUtils.readBooleans(buffer)"));		// decode expression
		typeMap.put("content", new DomainInfo(
			"Content",										// Java code type
			"EncodingUtils.encodedContentLength(#)", 	// size
			"EncodingUtils.writeContentBytes(buffer, #)", // encode expression
			"# = EncodingUtils.readContent(buffer)"));	// decode expression
		typeMap.put("long", new DomainInfo(
			"long",											// Java code type
			"4",											// size
			"EncodingUtils.writeUnsignedInteger(buffer, #)", // encode expression
			"# = buffer.getUnsignedInt()")); 				// decode expression
		typeMap.put("longlong", new DomainInfo(
			"long",											// Java code type
			"8",											// size
			"buffer.putLong(#)", 							// encode expression
			"# = buffer.getLong()")); 						// decode expression
		typeMap.put("longstr", new DomainInfo(
			"byte[]",										// Java code type
			"EncodingUtils.encodedLongstrLength(#)", 		// size
			"EncodingUtils.writeLongStringBytes(buffer, #)", // encode expression
			"# = EncodingUtils.readLongstr(buffer)"));		// decode expression
		typeMap.put("octet", new DomainInfo(
			"short",										// Java code type
			"1",											// size
			"EncodingUtils.writeUnsignedByte(buffer, #)",	// encode expression
			"# = buffer.getUnsigned()")); 					// decode expression
		typeMap.put("short", new DomainInfo(
			"int",											// Java code type
			"2",											// size
			"EncodingUtils.writeUnsignedShort(buffer, #)",	// encode expression
			"# = buffer.getUnsignedShort()")); 				// decode expression
		typeMap.put("shortstr", new DomainInfo(
			"AMQShortString",										// Java code type
			"EncodingUtils.encodedShortStringLength(#)",	// size
			"EncodingUtils.writeShortStringBytes(buffer, #)", // encode expression
			"# = EncodingUtils.readAMQShortString(buffer)"));	// decode expression
		typeMap.put("table", new DomainInfo(
			"FieldTable",									// Java code type
			"EncodingUtils.encodedFieldTableLength(#)", 	// size
			"EncodingUtils.writeFieldTableBytes(buffer, #)", // encode expression
			"# = EncodingUtils.readFieldTable(buffer)"));	// decode expression
		typeMap.put("timestamp", new DomainInfo(
			"long",											// Java code type
			"8",											// size
			"EncodingUtils.writeTimestamp(buffer, #)",		// encode expression
			"# = EncodingUtils.readTimestamp(buffer)"));	// decode expression
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

	
	// === Abstract methods from class Generator - Java-specific implementations ===
	
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
	protected void processTemplateA(String[] template)
	    throws IOException, AmqpTemplateException, AmqpTypeMappingException,
	    	IllegalAccessException, InvocationTargetException
   	{
		processTemplateD(template, null, null, null);
  	}
	
	@Override
	protected void processTemplateB(String[] template, AmqpClass thisClass)
	    throws IOException, AmqpTemplateException, AmqpTypeMappingException,
	    	IllegalAccessException, InvocationTargetException
	{
		processTemplateD(template, thisClass, null, null);
	}
	
	@Override
	protected void processTemplateC(String[] template, AmqpClass thisClass,
		AmqpMethod method)
	    throws IOException, AmqpTemplateException, AmqpTypeMappingException,
	    	IllegalAccessException, InvocationTargetException
	{
		processTemplateD(template, thisClass, method, null);
	}
	
	@Override
	protected void processTemplateD(String[] template, AmqpClass thisClass,
		AmqpMethod method, AmqpField field)
	    throws IOException, AmqpTemplateException, AmqpTypeMappingException,
	    	IllegalAccessException, InvocationTargetException
	{
		StringBuffer sb = new StringBuffer(template[1]);
		String filename = prepareFilename(getTemplateFileName(sb), thisClass, method, field);
		processTemplate(sb, thisClass, method, field, template[templateFileNameIndex], null);
		writeTargetFile(sb, new File(genDir + Utils.fileSeparator + filename));
		generatedFileCounter ++;
	}
	
	protected void processTemplate(StringBuffer sb, AmqpClass thisClass, AmqpMethod method,
		AmqpField field, String templateFileName, AmqpVersion version)
		throws InvocationTargetException, IllegalAccessException, AmqpTypeMappingException
	{
		try { processAllLists(sb, thisClass, method, version); }
		catch (AmqpTemplateException e)
		{
			System.out.println("WARNING: " + templateFileName + ": " + e.getMessage());
		}
		try { processAllTokens(sb, thisClass, method, field, version); }
		catch (AmqpTemplateException e)
		{
			System.out.println("WARNING: " + templateFileName + ": " + e.getMessage());
		}
	}

	@Override
	protected String processToken(String token, AmqpClass thisClass, AmqpMethod method, AmqpField field,
		AmqpVersion version)
	    throws AmqpTemplateException, AmqpTypeMappingException
	{
		if (token.compareTo("${GENERATOR}") == 0)
			return generatorInfo;
		if (token.compareTo("${CLASS}") == 0 && thisClass != null)
			return thisClass.name;
		if (token.compareTo("${CLASS_ID_INIT}") == 0 && thisClass != null)
			return generateIndexInitializer("registerClassId", thisClass.indexMap, 8);
		if (token.compareTo("${METHOD}") == 0 && method != null)
			return method.name;
		if (token.compareTo("${METHOD_ID_INIT}") == 0 && method != null)
			return generateIndexInitializer("registerMethodId", method.indexMap, 8);
		if (token.compareTo("${FIELD}") == 0 && field != null)
			return field.name;
		
		// This token is used only with class or method-level templates
		if (token.compareTo("${pch_property_flags_declare}") == 0)
		{
			return generatePchPropertyFlagsDeclare();
		}
		else if (token.compareTo("${pch_property_flags_initializer}") == 0)
		{
			int mapSize = method == null ? thisClass.fieldMap.size() : method.fieldMap.size();
			return generatePchPropertyFlagsInitializer(mapSize);
		}
		else if (token.compareTo("${pch_compact_property_flags_initializer}") == 0)
		{
			return generatePchCompactPropertyFlagsInitializer(thisClass, 8, 4);
		}
		else if (token.compareTo("${pch_compact_property_flags_check}") == 0)
		{
			return generatePchCompactPropertyFlagsCheck(thisClass, 8, 4);
		}
		
		// Oops!
		throw new AmqpTemplateException("Template token " + token + " unknown.");	
	}
	
	@Override
	protected void processClassList(StringBuffer sb, int listMarkerStartIndex, int listMarkerEndIndex,
        AmqpModel model)
	    throws AmqpTemplateException, AmqpTypeMappingException
	{
		String codeSnippet;
		int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
		String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
		int tokStart = tline.indexOf('$');
		String token = tline.substring(tokStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		if (token.compareTo("${reg_map_put_method}") == 0)
		{
			codeSnippet = generateRegistry(model, 8, 4); 
		}
		
		else // Oops!
		{
			throw new AmqpTemplateException("Template token " + token + " unknown.");
		}

		sb.insert(listMarkerStartIndex, codeSnippet);
	}
	
	@Override
	protected void processMethodList(StringBuffer sb, int listMarkerStartIndex, int listMarkerEndIndex,
        AmqpClass thisClass)
        throws AmqpTemplateException, AmqpTypeMappingException
	{
		String codeSnippet;
		int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
		String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
		int tokStart = tline.indexOf('$');
		String token = tline.substring(tokStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		//TODO - we don't have any cases of this (yet).
		if (token.compareTo("${???}") == 0)
		{
			codeSnippet = token; 
		}
		else // Oops!
		{
			throw new AmqpTemplateException("Template token " + token + " unknown.");
		}
		
		sb.insert(listMarkerStartIndex, codeSnippet);
	}
	
	@Override
	protected void processFieldList(StringBuffer sb, int listMarkerStartIndex, int listMarkerEndIndex,
		AmqpFieldMap fieldMap, AmqpVersion version)
	    throws AmqpTypeMappingException, AmqpTemplateException, IllegalAccessException,
	    	InvocationTargetException
	{
		String codeSnippet;
		int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
		String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
		int tokStart = tline.indexOf('$');
		String token = tline.substring(tokStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		// Field declarations - common to MethodBody and PropertyContentHeader classes
		if (token.compareTo("${field_declaration}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(declarationGenerateMethod,
				mangledDeclarationGenerateMethod, 4, 4, this);
		}
		
		// MethodBody classes
		else if (token.compareTo("${mb_field_get_method}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(mbGetGenerateMethod,
				mbMangledGetGenerateMethod, 4, 4, this);
		}
		else if (token.compareTo("${mb_field_parameter_list}") == 0)
		{
			// <cringe> The code generated by this is ugly... It puts a comma on a line by itself!
			// TODO: Find a more elegant solution here sometime...
			codeSnippet = fieldMap.size() > 0 ? Utils.createSpaces(42) + "," + cr : "";
			// </cringe>
			codeSnippet += fieldMap.parseFieldMap(mbParamListGenerateMethod,
				mbMangledParamListGenerateMethod, 42, 4, this);
		}

		else if (token.compareTo("${mb_field_passed_parameter_list}") == 0)
		{
			// <cringe> The code generated by this is ugly... It puts a comma on a line by itself!
			// TODO: Find a more elegant solution here sometime...
			codeSnippet = fieldMap.size() > 0 ? Utils.createSpaces(42) + "," + cr : "";
			// </cringe>
			codeSnippet += fieldMap.parseFieldMap(mbPassedParamListGenerateMethod,
				mbMangledPassedParamListGenerateMethod, 42, 4, this);
		}
		else if (token.compareTo("${mb_field_body_initialize}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(mbBodyInitGenerateMethod,
				mbMangledBodyInitGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_field_size}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(mbSizeGenerateMethod,
				mbBitSizeGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_field_encode}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(mbEncodeGenerateMethod,
				mbBitEncodeGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_field_decode}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(mbDecodeGenerateMethod,
				mbBitDecodeGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_field_to_string}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(mbToStringGenerateMethod,
				mbBitToStringGenerateMethod, 8, 4, this);
		}
		
		// PropertyContentHeader classes
		else if (token.compareTo("${pch_field_list_size}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(pchSizeGenerateMethod,
				pchBitSizeGenerateMethod, 12, 4, this);
		}
		else if (token.compareTo("${pch_field_list_payload}") == 0 )
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(pchEncodeGenerateMethod,
				pchBitEncodeGenerateMethod, 12, 4, this);
		}
		else if (token.compareTo("${pch_field_list_decode}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(pchDecodeGenerateMethod,
				pchBitDecodeGenerateMethod, 12, 4, this);
		}
		else if (token.compareTo("${pch_get_compact_property_flags}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(pchGetPropertyFlagsGenerateMethod,
				pchBitGetPropertyFlagsGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${pch_set_compact_property_flags}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(pchSetPropertyFlagsGenerateMethod,
				pchBitSetPropertyFlagsGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${pch_field_clear_methods}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(pchClearGenerateMethod,
					pchMangledClearGenerateMethod, 4, 4, this);
		}
		else if (token.compareTo("${pch_field_get_methods}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(pchGetGenerateMethod,
					pchMangledGetGenerateMethod, 4, 4, this);
		}
		else if (token.compareTo("${pch_field_set_methods}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(pchSetGenerateMethod,
					pchMangledSetGenerateMethod, 4, 4, this);
		}
		
		else // Oops!
		{
			throw new AmqpTemplateException("Template token " + token + " unknown.");
		}
		sb.insert(listMarkerStartIndex, codeSnippet);
	}

    @Override
    protected void processConstantList(StringBuffer sb, int listMarkerStartIndex, int listMarkerEndIndex,
        AmqpConstantSet constantSet)
        throws AmqpTemplateException, AmqpTypeMappingException

        {
        String codeSnippet;
        int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
        String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
        int tokStart = tline.indexOf('$');
        String token = tline.substring(tokStart).trim();
        sb.delete(listMarkerStartIndex, lend);

        if (token.compareTo("${const_get_method}") == 0)
        {
            codeSnippet = generateConstantGetMethods(constantSet, 4, 4); 
        }
       
        else // Oops!
        {
            throw new AmqpTemplateException("Template token " + token + " unknown.");
        }

        sb.insert(listMarkerStartIndex, codeSnippet);
        }
    
    
	// === Protected and private helper functions unique to Java implementation ===
	
	// Methods used for generation of code snippets called from the field map parsers
	
	// Common methods

	protected String generateFieldDeclaration(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return Utils.createSpaces(indentSize) + "public " + codeType + " " + field.name +
			"; // AMQP version(s): " + versionSet + cr;
	}

	protected String generateMangledFieldDeclaration(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
	    throws AmqpTypeMappingException
	{
		StringBuffer sb = new StringBuffer();
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			String domainName = dItr.next();
			AmqpVersionSet versionSet = field.domainMap.get(domainName);
			String codeType = getGeneratedType(domainName, versionSet.first());
			sb.append(Utils.createSpaces(indentSize) + "public " + codeType + " " +
				field.name + "_" + (domainCntr++) + "; // AMQP Version(s): " + versionSet +
				cr);
		}
		return sb.toString();		
	}

	protected String generateIndexInitializer(String mapName, AmqpOrdinalVersionMap indexMap, int indentSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		
		Iterator<Integer> iItr = indexMap.keySet().iterator();
		while (iItr.hasNext())
		{
			int index = iItr.next();
			AmqpVersionSet versionSet = indexMap.get(index);
			Iterator<AmqpVersion> vItr = versionSet.iterator();
			while (vItr.hasNext())
			{
				AmqpVersion version = vItr.next();
				sb.append(indent + mapName + "( (byte) " + version.getMajor() +", (byte) " + version.getMinor() + ", " + index + ");" + cr);
			}
		}
		return sb.toString();		
	}

	protected String generateRegistry(AmqpModel model, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		
		for (String className : model.classMap.keySet())
		{
			AmqpClass thisClass = model.classMap.get(className);
			for (String methodName : thisClass.methodMap.keySet())
			{
				AmqpMethod method = thisClass.methodMap.get(methodName);
				for (AmqpVersion version : globalVersionSet)
				{
					// Find class and method index for this version (if it exists)
					try
					{
						int classIndex = findIndex(thisClass.indexMap, version);
						int methodIndex = findIndex(method.indexMap, version);
						sb.append(indent + "registerMethod(" + cr);
						sb.append(indent + tab + "(short)" + classIndex +
							", (short)" + methodIndex + ", (byte)" + version.getMajor() +
							", (byte)" + version.getMinor() + ", " + cr);
						sb.append(indent + tab + Utils.firstUpper(thisClass.name) +
							Utils.firstUpper(method.name) + "Body.getFactory());" + cr);
					}
					catch (Exception e) {} // Ignore
				}
			}
		}
		return sb.toString();
	}
	
	protected int findIndex(TreeMap<Integer, AmqpVersionSet> map, AmqpVersion version)
	    throws Exception
	{
		Iterator<Integer> iItr = map.keySet().iterator();
		while (iItr.hasNext())
		{
			int index = iItr.next();
			AmqpVersionSet versionSet = map.get(index);
			if (versionSet.contains(version))
				return index;
		}
		throw new Exception("Index not found");
	}
    
    // Methods for AmqpConstants class
   
    protected String generateConstantGetMethods(AmqpConstantSet constantSet,
        int indentSize, int tabSize)
        throws AmqpTypeMappingException
    {
        String indent = Utils.createSpaces(indentSize);
        StringBuffer sb = new StringBuffer();
        Iterator<AmqpConstant> cItr = constantSet.iterator();
        while (cItr.hasNext())
        {
            AmqpConstant constant = cItr.next();
            if (constant.isVersionConsistent(globalVersionSet))
            {
                // return a constant
                String value = constant.firstKey();
                sb.append(indent + "public static String " + constant.name + "() { return \"" +
                    constant.firstKey() + "\"; }" + cr);
                if (Utils.containsOnlyDigits(value))
                {
                    sb.append(indent + "public static int " + constant.name + "AsInt() { return " +
                        constant.firstKey() + "; }" + cr);
                }
                if (Utils.containsOnlyDigitsAndDecimal(value))
                {
                    sb.append(indent + "public static double " + constant.name + "AsDouble() { return (double)" +
                        constant.firstKey() + "; }" + cr);
                }
                sb.append(cr);
            }
            else
            {
                // Return version-specific constant
                sb.append(generateVersionDependentGet(constant, "String", "", "\"", "\"", indentSize, tabSize));
                sb.append(generateVersionDependentGet(constant, "int", "AsInt", "", "", indentSize, tabSize));
                sb.append(generateVersionDependentGet(constant, "double", "AsDouble", "(double)", "", indentSize, tabSize));
                sb.append(cr);
           }
        }        
        return sb.toString();       
    }
    
    protected String generateVersionDependentGet(AmqpConstant constant,
        String methodReturnType, String methodNameSuffix, String returnPrefix, String returnPostfix,
        int indentSize, int tabSize)
        throws AmqpTypeMappingException
    {
        String indent = Utils.createSpaces(indentSize);
        String tab = Utils.createSpaces(tabSize);
        StringBuffer sb = new StringBuffer();
        sb.append(indent + "public static " + methodReturnType + " " + constant.name +
            methodNameSuffix + "(byte major, byte minor) throws AMQProtocolVersionException" + cr);
        sb.append(indent + "{" + cr);
        boolean first = true;
        Iterator<String> sItr = constant.keySet().iterator();
        while (sItr.hasNext())
        {
            String value = sItr.next();
            AmqpVersionSet versionSet = constant.get(value);
            sb.append(indent + tab + (first ? "" : "else ") + "if (" + generateVersionCheck(versionSet) +
                ")" + cr);
            sb.append(indent + tab + "{" + cr);
            if (methodReturnType.compareTo("int") == 0 && !Utils.containsOnlyDigits(value))
            {
                sb.append(generateConstantDeclarationException(constant.name, methodReturnType,
                    indentSize + (2*tabSize), tabSize));
            }
            else if (methodReturnType.compareTo("double") == 0 && !Utils.containsOnlyDigitsAndDecimal(value))
            {
                sb.append(generateConstantDeclarationException(constant.name, methodReturnType,
                    indentSize + (2*tabSize), tabSize));                            
            }
            else
            {
                sb.append(indent + tab + tab + "return " + returnPrefix + value + returnPostfix + ";" + cr);
            }
            sb.append(indent + tab + "}" + cr);
            first = false;
        }
        sb.append(indent + tab + "else" + cr);
        sb.append(indent + tab + "{" + cr);
        sb.append(indent + tab + tab + "throw new AMQProtocolVersionException(\"Constant \\\"" +
            constant.name + "\\\" \" +" + cr);
        sb.append(indent + tab + tab + tab +
            "\"is undefined for AMQP version \" + major + \"-\" + minor + \".\");" + cr);
        sb.append(indent + tab + "}" + cr);
        sb.append(indent + "}" + cr); 
        return sb.toString();       
    }
        
    protected String generateConstantDeclarationException(String name, String methodReturnType,
        int indentSize, int tabSize)
    {
        String indent = Utils.createSpaces(indentSize);
        String tab = Utils.createSpaces(tabSize);
        StringBuffer sb = new StringBuffer();
        sb.append(indent + "throw new AMQProtocolVersionException(\"Constant \\\"" +
            name + "\\\" \" +" + cr);
        sb.append(indent + tab + "\"cannot be converted to type " + methodReturnType +
            " for AMQP version \" + major + \"-\" + minor + \".\");" + cr);        
        return sb.toString();       
    }
    
	// Methods for MessageBody classes
	protected String generateMbGetMethod(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return Utils.createSpaces(indentSize) + "public " + codeType + " get" +
			Utils.firstUpper(field.name) + "() { return " + field.name + "; }" +
			cr;
	}
	
	protected String generateMbMangledGetMethod(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer(cr);
		sb.append(indent + "public <T> T get" + Utils.firstUpper(field.name) +
				"(Class<T> classObj) throws AMQProtocolVersionException" + cr);
		sb.append(indent + "{" + cr);		
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			String domainName = dItr.next();
			AmqpVersionSet versionSet = field.domainMap.get(domainName);
			String codeType = getGeneratedType(domainName, versionSet.first());
			sb.append(indent + tab + "if (classObj.equals(" + codeType +
				".class)) // AMQP Version(s): " + versionSet + cr);
			sb.append(indent + tab + tab + "return (T)(Object)" + field.name + "_" +
				(domainCntr++) + ";" + cr);
		}
		sb.append(indent + tab +
		"throw new AMQProtocolVersionException(\"None of the AMQP versions defines \" +" +
		cr + "            \"field \\\"" + field.name +
		"\\\" as domain \\\"\" + classObj.getName() + \"\\\".\");" + cr);
		sb.append(indent + "}" + cr);		
		sb.append(cr);		
		return sb.toString();		
	}
	
	protected String generateMbParamList(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return Utils.createSpaces(indentSize) + codeType + " " + field.name +
			(nextFlag ? "," : "") + " // AMQP version(s): " + versionSet + cr;
	}
	
	
	protected String generateMbPassedParamList(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return Utils.createSpaces(indentSize) + field.name +
			(nextFlag ? "," : "") + " // AMQP version(s): " + versionSet + cr;
	}
	
	
	protected String generateMbMangledParamList(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		StringBuffer sb = new StringBuffer();
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			String domainName = dItr.next();
			AmqpVersionSet versionSet = field.domainMap.get(domainName);
			String codeType = getGeneratedType(domainName, versionSet.first());
			sb.append(Utils.createSpaces(indentSize) + codeType + " " + field.name + "_" +
				(domainCntr++) + (nextFlag ? "," : "") + " // AMQP version(s): " +
				versionSet + cr);
		}
		return sb.toString();		
	}
	
	protected String generateMbMangledPassedParamList(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		StringBuffer sb = new StringBuffer();
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			String domainName = dItr.next();
			AmqpVersionSet versionSet = field.domainMap.get(domainName);
			sb.append(Utils.createSpaces(indentSize) + field.name + "_" +
				(domainCntr++) + (nextFlag ? "," : "") + " // AMQP version(s): " +
				versionSet + cr);
		}
		return sb.toString();		
	}
	
	
	protected String generateMbBodyInit(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return Utils.createSpaces(indentSize) + "this." + field.name + " = " + field.name +
			";" + cr;
	}
	
	protected String generateMbMangledBodyInit(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		StringBuffer sb = new StringBuffer();
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			dItr.next();
			sb.append(Utils.createSpaces(indentSize) + "this." + field.name + "_" + domainCntr +
					" = " + field.name + "_" + (domainCntr++) +	";" + cr);
		}
		return sb.toString();		
	}

	protected String generateMbFieldSize(String domainType, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(Utils.createSpaces(indentSize) + "size += " +
			typeMap.get(domainType).size.replaceAll("#", fieldName) +
			"; // " + fieldName + ": " + domainType + cr);
		return sb.toString();
	}
	
	protected String generateMbBitArrayFieldSize(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		StringBuffer sb = new StringBuffer();
		int numBytes = ((bitFieldList.size() - 1) / 8) + 1;
		String comment = bitFieldList.size() == 1 ?
			bitFieldList.get(0) + ": bit" :
			"Combinded bits: " + bitFieldList;
		sb.append(Utils.createSpaces(indentSize) + "size += " +
			typeMap.get("bit").size.replaceAll("~", String.valueOf(numBytes)) +
			"; // " + comment + cr);
		return sb.toString();
	}

	protected String generateMbFieldEncode(String domain, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(Utils.createSpaces(indentSize) +
			typeMap.get(domain).encodeExpression.replaceAll("#", fieldName) +
			"; // " + fieldName + ": " + domain + cr);
		return sb.toString();
	}

	protected String generateMbBitFieldEncode(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
	
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while(i <bitFieldList.size())
		{
		
			StringBuilder line = new StringBuilder();
			
			for (int j=0; i<bitFieldList.size() && j<8; i++, j++)
			{
				if (j != 0)
				{				
					line.append(", ");
				}
				line.append(bitFieldList.get(i));
			}
			
			sb.append(indent +
				typeMap.get("bit").encodeExpression.replaceAll("#", line.toString()) + ";" + cr);
		}
		return sb.toString();
	}

	protected String generateMbFieldDecode(String domain, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(Utils.createSpaces(indentSize) +
			typeMap.get(domain).decodeExpression.replaceAll("#", fieldName) +
			"; // " + fieldName + ": " + domain + cr);
		return sb.toString();
	}

	protected String generateMbBitFieldDecode(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		
		StringBuilder sb = new StringBuilder(indent);
		sb.append("byte packedValue;");
		sb.append(cr);
		
		// RG HERE!
		
		int i = 0;
		while(i < bitFieldList.size())
		{
			sb.append(indent + "packedValue = EncodingUtils.readByte(buffer);" + cr);
			
			for(int j = 0; i < bitFieldList.size() && j < 8; i++, j++)
			{
				sb.append(indent + bitFieldList.get(i) + " = ( packedValue & (byte) (1 << " + j + ") ) != 0;" + cr); 
			}
		}
		return sb.toString();
	}
	
	protected String generateMbFieldToString(String domain, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(Utils.createSpaces(indentSize) +
			"buf.append(\"  " + fieldName + ": \" + " + fieldName + ");" + cr);		
		return sb.toString();
	}
	
	protected String generateMbBitFieldToString(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<bitFieldList.size(); i++)
		{
			String bitFieldName = bitFieldList.get(i);
			sb.append(indent + "buf.append(\"  " + bitFieldName + ": \" + " + bitFieldName +
				");" + cr);
		}		
		return sb.toString();
	}

	// Methods for PropertyContentHeader classes

	protected String generatePchClearMethod(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		// This is one case where the ordinal info is the only significant factor,
		// the domain info plays no part. Defer to the mangled version; the code would be
		// identical anyway...
		return generatePchMangledClearMethod(field, indentSize, tabSize, nextFlag);		
	}
		
	protected String generatePchMangledClearMethod(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		sb.append(indent + "public void clear" + Utils.firstUpper(field.name) +
				"()" + cr);
		sb.append(indent + "{" + cr);
		
		// If there is more than one ordinal for this field or the ordinal does not
		// apply to all known versions, then we need to generate version checks so
		// we know which fieldProperty to clear.
		if (field.ordinalMap.size() == 1 &&
			field.ordinalMap.get(field.ordinalMap.firstKey()).size() == globalVersionSet.size())
		{
			int ordinal = field.ordinalMap.firstKey();
			sb.append(indent + tab + "clearEncodedForm();" + cr);
			sb.append(indent + tab + "propertyFlags[" + ordinal + "] = false;" + cr);
		}
		else
		{
			Iterator<Integer> oItr = field.ordinalMap.keySet().iterator();
			while (oItr.hasNext())
			{
				int ordinal = oItr.next();
				AmqpVersionSet versionSet = field.ordinalMap.get(ordinal);
				sb.append(indent + tab);
				if (ordinal != field.ordinalMap.firstKey())
					sb.append("else ");
				sb.append("if (");
				sb.append(generateVersionCheck(versionSet));
				sb.append(")" + cr);
				sb.append(indent + tab + "{" + cr);
				sb.append(indent + tab + tab + "clearEncodedForm();" + cr);
				sb.append(indent + tab + tab + "propertyFlags[" + ordinal + "] = false;" + cr);
				sb.append(indent + tab + "}" + cr);
			}
		}
		sb.append(indent + "}" + cr);
		sb.append(cr);
		return sb.toString();		
	}
	
	protected String generatePchGetMethod(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer(indent + "public " + codeType + " get" +
			Utils.firstUpper(field.name) + "()" + cr);
		sb.append(indent + "{" + cr);
		sb.append(indent + tab + "decodeIfNecessary();" + cr);
		sb.append(indent + tab + "return " + field.name + ";" + cr);
		sb.append(indent + "}" + cr);
		sb.append(cr);		
		return sb.toString();		
	}
		
	protected String generatePchMangledGetMethod(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer(indent + "public <T> T get" +
			Utils.firstUpper(field.name) +
			"(Class<T> classObj) throws AMQProtocolVersionException" + cr);
		sb.append(indent + "{" + cr);		
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			String domainName = dItr.next();
			AmqpVersionSet versionSet = field.domainMap.get(domainName);
			String codeType = getGeneratedType(domainName, versionSet.first());
			sb.append(indent + tab + "if (classObj.equals(" + codeType +
				".class)) // AMQP Version(s): " + versionSet + cr);
			sb.append(indent + tab + "{" + cr);		
			sb.append(indent + tab + tab + "decodeIfNecessary();" + cr);
			sb.append(indent + tab + tab + "return (T)(Object)" + field.name + "_" +
				(domainCntr++) + ";" + cr);
			sb.append(indent + tab + "}" + cr);		
		}
		sb.append(indent + tab +
		    "throw new AMQProtocolVersionException(\"None of the AMQP versions defines \" +" +
			cr + "            \"field \\\"" + field.name +
			"\\\" as domain \\\"\" + classObj.getName() + \"\\\".\");" + cr);
		sb.append(indent + "}" + cr);		
		sb.append(cr);		
		return sb.toString();		
	}
	
	protected String generatePchSetMethod(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		sb.append(indent + "public void set" + Utils.firstUpper(field.name) +
		    "(" + codeType + " " + field.name + ")" + cr);
		sb.append(indent + "{" + cr);
		
		// If there is more than one ordinal for this field or the ordinal does not
		// apply to all known versions, then we need to generate version checks so
		// we know which fieldProperty to clear.
		if (field.ordinalMap.size() == 1 &&
			field.ordinalMap.get(field.ordinalMap.firstKey()).size() == globalVersionSet.size())
		{
			int ordinal = field.ordinalMap.firstKey();
			sb.append(indent + tab + "clearEncodedForm();" + cr);
			sb.append(indent + tab + "propertyFlags[" + ordinal + "] = true;" + cr);
			sb.append(indent + tab + "this." + field.name + " = " + field.name + ";" + cr);
		}
		else
		{
			Iterator<Integer> oItr = field.ordinalMap.keySet().iterator();
			while (oItr.hasNext())
			{
				int ordinal = oItr.next();
				AmqpVersionSet oVersionSet = field.ordinalMap.get(ordinal);
				sb.append(indent + tab);
				if (ordinal != field.ordinalMap.firstKey())
					sb.append("else ");
				sb.append("if (");
				sb.append(generateVersionCheck(oVersionSet));
				sb.append(")" + cr);
				sb.append(indent + tab + "{" + cr);			
				sb.append(indent + tab + tab + "clearEncodedForm();" + cr);
				sb.append(indent + tab + tab + "propertyFlags[" + ordinal + "] = true;" + cr);
				sb.append(indent + tab + tab + "this." + field.name + " = " + field.name + ";" + cr);
				sb.append(indent + tab + "}" + cr);
			}
		}
		sb.append(indent + "}" + cr);
		sb.append(cr);
		return sb.toString();		
	}
		
	protected String generatePchMangledSetMethod(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			String domainName = dItr.next();
			AmqpVersionSet versionSet = field.domainMap.get(domainName);
			String codeType = getGeneratedType(domainName, versionSet.first());
			
			// Find ordinal with matching version
			AmqpVersionSet commonVersionSet = new AmqpVersionSet();
			Iterator<Integer> oItr = field.ordinalMap.keySet().iterator();
			while (oItr.hasNext())
			{
				int ordinal = oItr.next();
				AmqpVersionSet oVersionSet = field.ordinalMap.get(ordinal);
				Iterator<AmqpVersion> vItr = oVersionSet.iterator();
				boolean first = true;
				while (vItr.hasNext())
				{
					AmqpVersion thisVersion = vItr.next();
					if (versionSet.contains(thisVersion))
						commonVersionSet.add(thisVersion);
				}
				if (!commonVersionSet.isEmpty())
				{
					sb.append(indent + "public void set" + Utils.firstUpper(field.name) +
						    "(" + codeType + " " + field.name + ")" + cr);
					sb.append(indent + "{" + cr);
					sb.append(indent + tab);
					if (!first)
						sb.append("else ");
					sb.append("if (");
					sb.append(generateVersionCheck(commonVersionSet));
					sb.append(")" + cr);
					sb.append(indent + tab + "{" + cr);
					sb.append(indent + tab + tab + "clearEncodedForm();" + cr);
					sb.append(indent + tab + tab + "propertyFlags[" + ordinal + "] = true;" + cr);
					sb.append(indent + tab + tab + "this." + field.name + "_" + (domainCntr++) +
						" = " + field.name + ";" + cr);
					sb.append(indent + tab + "}" + cr);
					sb.append(indent + "}" + cr);
					sb.append(cr);
					first = false;
				}
			}
		}
		return sb.toString();		
	}

	protected String generatePchFieldSize(String domainType, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer(indent + "if (propertyFlags[" + ordinal + "]) // " +
			fieldName + ": " + domainType + cr);
		sb.append(indent + Utils.createSpaces(tabSize) + "size += " +
			typeMap.get(domainType).size.replaceAll("#", fieldName) + ";" + cr);
		sb.append(cr);
		return sb.toString();
	}
	
	protected String generatePchBitArrayFieldSize(ArrayList<String> bitFieldList,
			int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		String comment = bitFieldList.size() == 1 ?
			bitFieldList.get(0) + ": bit" :
			"Combinded bits: " + bitFieldList;
		StringBuffer sb = new StringBuffer();
				
		if (bitFieldList.size() == 1) // single bit
		{
			sb.append(indent + "if (propertyFlags[" + (ordinal - 1) + "]) // " + comment + cr);
			sb.append(indent + tab + "size += " +
				typeMap.get("bit").size.replaceAll("~", "1") + ";" + cr);
		}
		else // multiple bits - up to 8 are combined into one byte
		{
			String bitCntrName = "bitCntr_" + ordinal;
			int startOrdinal = ordinal - bitFieldList.size();
			sb.append(indent + "// " + comment + cr);
			sb.append(indent + "int " + bitCntrName + " = 0;" + cr);
			sb.append(indent + "for (int i=" + startOrdinal + "; i<" + ordinal + "; i++)" + cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + tab + "if (propertyFlags[i])" + cr);
			sb.append(indent + tab + tab + bitCntrName + "++;" + cr);
			sb.append(indent + "}" + cr);
			sb.append(indent + "size += " +
				typeMap.get("bit").size.replaceAll("~", bitCntrName +
					" > 0 ? ((" + bitCntrName + " - 1) / 8) + 1 : 0") + ";" + cr);
		}
		sb.append(cr);
		return sb.toString();
	}

	protected String generatePchFieldEncode(String domainType, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		sb.append(indent + "if (propertyFlags[" + ordinal + "]) // " + fieldName + ": " +
			domainType + cr);
		sb.append(indent + Utils.createSpaces(tabSize) +
			typeMap.get(domainType).encodeExpression.replaceAll("#", fieldName) + ";" + cr);
		sb.append(cr);
		return sb.toString();
	}

	protected String generatePchBitFieldEncode(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		String comment = bitFieldList.size() == 1 ?
			bitFieldList.get(0) + ": bit" :
			"Combinded bits: " + bitFieldList;
		StringBuffer sb = new StringBuffer();
		
		if (bitFieldList.size() == 1) // single bit
		{
			sb.append(indent + "if (propertyFlags[" + (ordinal - 1) + "]) // " +
				bitFieldList.get(0) + ": bit" + cr);
			sb.append(indent + tab + typeMap.get("bit").encodeExpression.replaceAll("#",
				"new boolean[] {" + bitFieldList.get(0) + "}") + ";" + cr);
		}
		else // multiple bits - up to 8 are combined into one byte
		{
			int startOrdinal = ordinal - bitFieldList.size();
			String bitCntrName = "bitCntr" + startOrdinal;
			sb.append(indent + "// " + comment + cr);
			sb.append(indent + "int " + bitCntrName + " = 0;" + cr);
			sb.append(indent + "for (int i=" + startOrdinal + "; i<=" + (ordinal - 1) + "; i++)" + cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + tab + "if (propertyFlags[i])" + cr);
			sb.append(indent + tab + tab + bitCntrName + "++;" + cr);
			sb.append(indent + "}" + cr);
			sb.append(indent + "if (" + bitCntrName + " > 0) // Are any of the property bits set?" + cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + tab + "boolean[] fullBitArray = new boolean[] { ");
			for (int i=0; i<bitFieldList.size(); i++)
			{
				if (i != 0)
					sb.append(", ");
				sb.append(bitFieldList.get(i));
			}
			sb.append(" };" + cr);
			sb.append(indent + tab + "boolean[] flaggedBitArray = new boolean[" +bitCntrName +
				"];" + cr);
			sb.append(indent + tab + bitCntrName + " = 0;" + cr);
			sb.append(indent + tab + "for (int i=" + startOrdinal + "; i<=" + (ordinal - 1) +
				"; i++)" + cr);
			sb.append(indent + tab + "{" + cr);
			sb.append(indent + tab + tab+ "if (propertyFlags[i])" + cr);
			sb.append(indent + tab + tab + tab + "flaggedBitArray[" + bitCntrName +
				"++] = fullBitArray[i];" + cr);
			sb.append(indent + tab + "}" + cr);
			sb.append(indent + tab + typeMap.get("bit").encodeExpression.replaceAll("#",
					"flaggedBitArray") + ";" + cr);
			sb.append(indent + "}" + cr);
		}
		sb.append(cr);
		return sb.toString();
	}

	protected String generatePchFieldDecode(String domainType, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		sb.append(indent + "if (propertyFlags[" + ordinal + "]) // " + fieldName + ": " +
				domainType + cr);
		sb.append(indent + Utils.createSpaces(tabSize) +
			typeMap.get(domainType).decodeExpression.replaceAll("#", fieldName) + ";" + cr);
		sb.append(cr);
		return sb.toString();
	}

	protected String generatePchBitFieldDecode(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		String comment = bitFieldList.size() == 1 ?
				bitFieldList.get(0) + ": bit" :
				"Combinded bits: " + bitFieldList;
		StringBuffer sb = new StringBuffer();
			
		if (bitFieldList.size() == 1) // single bit
		{
			sb.append(indent + "if (propertyFlags[" + (ordinal - 1) + "]) // " +
				bitFieldList.get(0) + ": bit" + cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + tab + typeMap.get("bit").decodeExpression.replaceAll("#",
					"boolean[] flaggedBitArray") + ";" + cr);
			sb.append(indent + tab + bitFieldList.get(0) + " = flaggedBitArray[0];" + cr);
			sb.append(indent + "}" + cr);
		}
		else // multiple bits - up to 8 are combined into one byte
		{
			int startOrdinal = ordinal - bitFieldList.size();
			String bitCntr = "bitCntr" + startOrdinal;
			sb.append(indent + "// " + comment + cr);
			sb.append(indent + "int " + bitCntr + " = 0;" + cr);
			sb.append(indent + "for (int i=" + startOrdinal + "; i<=" + (ordinal - 1) + "; i++)" + cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + tab + "if (propertyFlags[i])" + cr);
			sb.append(indent + tab + tab + bitCntr + "++;" + cr);
			sb.append(indent + "}" + cr);
			sb.append(indent + "if (" + bitCntr + " > 0) // Are any of the property bits set?" + cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + tab + typeMap.get("bit").decodeExpression.replaceAll("#",
					"boolean[] flaggedBitArray") + ";" + cr);
			sb.append(indent + tab + bitCntr + " = 0;" + cr);
			for (int i=0; i<bitFieldList.size(); i++)
			{
				sb.append(indent + tab + "if (propertyFlags[" + (startOrdinal + i) + "])" + cr);
				sb.append(indent + tab + tab + bitFieldList.get(i) + " = flaggedBitArray[" +
					bitCntr + "++];" + cr);
			}
			sb.append(indent + "}" + cr);
		}

		sb.append(cr);
		return sb.toString();
	}

	protected String generatePchGetPropertyFlags(String domainType, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		int word = ordinal / 15;
		int bit = 15 - (ordinal % 15);
		sb.append(indent + "if (propertyFlags[" + ordinal + "]) // " + fieldName + ": " +
			domainType + cr);
		sb.append(indent + tab + "compactPropertyFlags[" + word + "] |= (1 << " +
			bit + ");" + cr);
		sb.append(cr);
		return sb.toString();
	}

	protected String generatePchBitGetPropertyFlags(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		int startOrdinal = ordinal - bitFieldList.size();
		
		for (int i=0; i<bitFieldList.size(); i++)
		{
			int thisOrdinal = startOrdinal + i;
			int word = thisOrdinal / 15;
			int bit = 15 - (thisOrdinal % 15);
			sb.append(indent + "if (propertyFlags[" + thisOrdinal + "])" + cr);
			sb.append(indent + tab + "compactPropertyFlags[" + word +
					"] |= (1 << " + bit + ");" + cr);
		}
		
		sb.append(cr);
		return sb.toString();
	}

	protected String generatePchSetPropertyFlags(String domainType, String fieldName,
			int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		int word = ordinal / 15;
		int bit = 15 - (ordinal % 15);
		sb.append(indent + "propertyFlags[" + ordinal + "] = (compactPropertyFlags[" +
			word + "] & (1 << " + bit + ")) > 0;" + cr);
		return sb.toString();
	}
	
	protected String generatePchBitSetPropertyFlags(ArrayList<String> bitFieldList,
			int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		int startOrdinal = ordinal - bitFieldList.size();
			
		for (int i=0; i<bitFieldList.size(); i++)
		{
			int thisOrdinal = startOrdinal + i;
			int word = thisOrdinal / 15;
			int bit = 15 - (thisOrdinal % 15);
			sb.append(indent + "propertyFlags[" + thisOrdinal + "] = (compactPropertyFlags[" +
				word + "] & (1 << " + bit + ")) > 0;" + cr);
		}		
		return sb.toString();
	}
	
	private String generatePchPropertyFlagsDeclare()
	{
		return "private boolean[] propertyFlags;";
	}
	
	private String generatePchPropertyFlagsInitializer(int totNumFields)
	{
		return "propertyFlags = new boolean[" + totNumFields + "];";
	}
	
	private String generatePchCompactPropertyFlagsInitializer(AmqpClass thisClass, int indentSize,
		int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		Iterator<AmqpVersion> vItr = globalVersionSet.iterator();
		while (vItr.hasNext())
		{
			AmqpVersion version = vItr.next();
			int numBytes = ((thisClass.fieldMap.getNumFields(version) - 1) / 15) + 1;
			
			sb.append(indent);
			if (!version.equals(globalVersionSet.first()))
				sb.append("else ");
			sb.append("if ( major == " + version.getMajor() + " && minor == " +
				version.getMinor() + " )" + cr);
			sb.append(indent + tab + "compactPropertyFlags = new int[] { ");
			for (int i=0; i<numBytes; i++)
			{
				if (i!= 0)
					sb.append(", ");
				sb.append(i < numBytes - 1 ? "1" : "0"); // Set the "continue" flag where required
			}
			sb.append(" };" + cr);
		}
		return sb.toString();
	}
	
	private String generatePchCompactPropertyFlagsCheck(AmqpClass thisClass, int indentSize,
		int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		Iterator<AmqpVersion> vItr = globalVersionSet.iterator();
		while (vItr.hasNext())
		{
			AmqpVersion version = vItr.next();
			int numFields = thisClass.fieldMap.getNumFields(version);
			int numBytes = ((numFields - 1) / 15) + 1;
			
			sb.append(indent);
			if (!version.equals(globalVersionSet.first()))
				sb.append("else ");
			sb.append("if ( major == " + version.getMajor() + " && minor == " +
				version.getMinor() + " && compactPropertyFlags.length != " + numBytes + " )" + cr);
			sb.append(indent + tab +
				"throw new AMQProtocolVersionException(\"Property flag array size mismatch:\" +" + cr);
			sb.append(indent + tab + tab + "\"(Size found: \" + compactPropertyFlags.length +" + cr);
			sb.append(indent + tab + tab + "\") Version " + version + " has " + numFields +
				" fields which requires an int array of size " + numBytes + ".\");" + cr);
		}
		return sb.toString();
	}
	
	private String generateVersionCheck(AmqpVersionSet v)
        throws AmqpTypeMappingException
	{
		StringBuffer sb = new StringBuffer();
		AmqpVersion[] versionArray = new AmqpVersion[v.size()];
		v.toArray(versionArray);
		for (int i=0; i<versionArray.length; i++)
		{
			if (i != 0)
				sb.append(" || ");
			if (versionArray.length > 1)
				sb.append("(");
			sb.append("major == (byte)" + versionArray[i].getMajor() + " && minor == (byte)" +
				versionArray[i].getMinor());
			if (versionArray.length > 1)
				sb.append(")");
		}
		return sb.toString();
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
