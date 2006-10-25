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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

public class CppGenerator extends Generator
{
	static String cr = Utils.lineSeparator;
	
	private class DomainInfo
	{
		public String type;
		public String size;
		public String encodeExpression;
		public String decodeExpression;
		public DomainInfo(String domain, String size, String encodeExpression,
			String decodeExpression)
		{
			this.type = domain;
			this.size = size;
			this.encodeExpression = encodeExpression;
			this.decodeExpression = decodeExpression;
		}
	}
	
	private static TreeMap<String, DomainInfo> typeMap = new TreeMap<String, DomainInfo>();

	// Methods used for generation of code snippets called from the field map parsers
	
	// MessageBody methods
	static private Method declarationGenerateMethod;
	static private Method mangledDeclarationGenerateMethod;
	static private Method mbGetGenerateMethod;
	static private Method mbMangledGetGenerateMethod;
	static private Method mbParamListGenerateMethod;
	static private Method mbMangledParamListGenerateMethod;
	static private Method mbParamDeclareListGenerateMethod;
	static private Method mbMangledParamDeclareListGenerateMethod;
	static private Method mbParamInitListGenerateMethod;
	static private Method mbMangledParamInitListGenerateMethod;
	
	static private Method mbPrintGenerateMethod;
	static private Method mbBitPrintGenerateMethod;
	static private Method mbSizeGenerateMethod;
	static private Method mbBitSizeGenerateMethod;
	static private Method mbEncodeGenerateMethod;
	static private Method mbBitEncodeGenerateMethod;
	static private Method mbDecodeGenerateMethod;
	static private Method mbBitDecodeGenerateMethod;

	static 
	{
		// *******************************
		// Methods for MessageBody classes
		// *******************************
		
		// Methods for AmqpFieldMap.parseFieldMap()
		
		try { declarationGenerateMethod = CppGenerator.class.getDeclaredMethod(
		    "generateFieldDeclaration", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mangledDeclarationGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMangledFieldDeclaration", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbGetGenerateMethod = CppGenerator.class.getDeclaredMethod(
		    "generateMbGetMethod", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbMangledGetGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbMangledGetMethod", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }

		try { mbParamListGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbParamList", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbMangledParamListGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbMangledParamList", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }

		try { mbParamDeclareListGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbParamDeclareList", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbMangledParamDeclareListGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbMangledParamDeclareList", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }

		try { mbParamInitListGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbParamInitList", String.class, AmqpField.class,
			AmqpVersionSet.class, int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbMangledParamInitListGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbMangledParamInitList", AmqpField.class,
			int.class, int.class, boolean.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		// Methods for  AmqpFieldMap.parseFieldMapOrdinally()
		
		try { mbPrintGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbFieldPrint", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbBitPrintGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbBitFieldPrint", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
		
		try { mbSizeGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbFieldSize", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
			
		try { mbBitSizeGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbBitArrayFieldSize", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
			
		try { mbEncodeGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbFieldEncode", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
			
		try { mbBitEncodeGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbBitFieldEncode", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
			
		try { mbDecodeGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbFieldDecode", String.class, String.class,
			int.class, int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
			
		try { mbBitDecodeGenerateMethod = CppGenerator.class.getDeclaredMethod(
			"generateMbBitFieldDecode", ArrayList.class, int.class,
			int.class, int.class); }
		catch (NoSuchMethodException e) { e.printStackTrace(); }
	}
	
	public CppGenerator(AmqpVersionSet versionList)
	{
		super(versionList);
		// Load C++ type and size maps.
		// Adjust or add to these lists as new types are added/defined.
		// The char '#' will be replaced by the field variable name (any type).
		// The char '~' will be replaced by the compacted bit array size (type bit only).
		typeMap.put("bit", new DomainInfo(
				"bool",					// type
				"~", 					// size
		        "",						// encodeExpression
    			""));					// decodeExpression
		typeMap.put("long", new DomainInfo(
				"u_int32_t",			// type
				"4", 					// size
                "buffer.putLong(#)",	// encodeExpression
				"buffer.getLong(#)"));	// decodeExpression
		typeMap.put("longlong", new DomainInfo(
				"u_int64_t",			// type
				"8", 					// size
                "buffer.putLongLong(#)", // encodeExpression
				"buffer.getLongLong(#)")); // decodeExpression
		typeMap.put("longstr", new DomainInfo(
				"string",				// type
				"4 + #.length()", 		// size
                "buffer.putLongString(#)", // encodeExpression
				"buffer.getLongString(#)")); // decodeExpression
		typeMap.put("octet", new DomainInfo(
				"u_int8_t",				// type
				"1", 					// size
                "buffer.putOctet(#)",	// encodeExpression
				"buffer.getOctet(#)"));	// decodeExpression
		typeMap.put("short", new DomainInfo(
				"u_int16_t",			// type
				"2",					// size
                "buffer.putShort(#)",	// encodeExpression
				"buffer.getShort(#)"));	// decodeExpression
		typeMap.put("shortstr", new DomainInfo(
				"string",				// type
				"1 + #.length()",		// size
                "buffer.putShortString(#)", // encodeExpression
				"buffer.getShortString(#)")); // decodeExpression
		typeMap.put("table", new DomainInfo(
				"FieldTable",			// type
				"#.size()", 			// size
                "buffer.putFieldTable(#)", // encodeExpression
				"buffer.getFieldTable(#)")); // decodeExpression
		typeMap.put("timestamp", new DomainInfo(
				"u_int64_t",			// type
				"8", 					// size
                "buffer.putLongLong(#)", // encodeExpression
				"buffer.getLongLong(#)")); // decodeExpression
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
			throw new AmqpTypeMappingException("Domain type \"" + domainName + "\" not found in C++ typemap.");
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
	protected String processToken(String token, AmqpClass thisClass, AmqpMethod method, AmqpField field)
	    throws AmqpTemplateException
	{
		if (token.compareTo("${GENERATOR}") == 0)
			return generatorInfo;
		if (token.compareTo("${CLASS}") == 0 && thisClass != null)
			return thisClass.name;
		if (token.compareTo("${CLASS_ID_INIT}") == 0 && thisClass != null)
			return generateIndexInitializer("classIdMap", thisClass.indexMap, 12);
		if (token.compareTo("${METHOD}") == 0 && method != null)
			return method.name;
		if (token.compareTo("${METHOD_ID_INIT}") == 0 && method != null)
			return generateIndexInitializer("methodIdMap", method.indexMap, 12);
		if (token.compareTo("${FIELD}") == 0 && field != null)
			return field.name;
		
//		if (token.compareTo("${mb_get_class_id}") == 0 || token.compareTo("${mb_get_method_id}") == 0)
//			return("/* === TODO === */");
		throw new AmqpTemplateException("Template token " + token + " unknown.");	
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
        throws AmqpTypeMappingException, AmqpTemplateException, IllegalAccessException,
    	InvocationTargetException
	{
		String codeSnippet;
		int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
		String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
		int tokxStart = tline.indexOf('$');
		String token = tline.substring(tokxStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		// Field declarations - common to MethodBody and PropertyContentHeader classes
		if (token.compareTo("${mb_field_declaration}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(declarationGenerateMethod,
				mangledDeclarationGenerateMethod, 4, 4, this);
		}
		else if (token.compareTo("${mb_field_get_method}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(mbGetGenerateMethod,
				mbMangledGetGenerateMethod, 4, 4, this);
		}
		else if (token.compareTo("${mb_field_print}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(mbPrintGenerateMethod,
				mbBitPrintGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_body_size}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(mbSizeGenerateMethod,
				mbBitSizeGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_encode}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(mbEncodeGenerateMethod,
				mbBitEncodeGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_decode}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMapOrdinally(mbDecodeGenerateMethod,
				mbBitDecodeGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_field_list}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(mbParamListGenerateMethod,
				mbMangledParamListGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_field_list_initializer}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(mbParamInitListGenerateMethod,
					mbMangledParamInitListGenerateMethod, 8, 4, this);
		}
		else if (token.compareTo("${mb_field_list_declare}") == 0)
		{
			codeSnippet = fieldMap.parseFieldMap(mbParamDeclareListGenerateMethod,
					mbMangledParamDeclareListGenerateMethod, 8, 4, this);
		}
		
		else // Oops!
		{
			throw new AmqpTemplateException("Template token " + token + " unknown.");
		}
		sb.insert(listMarkerStartIndex, codeSnippet);
	}
		
	// === Protected and private helper functions unique to C++ implementation ===

	// Methods used for generation of code snippets called from the field map parsers

	// Common methods

	protected String generateIndexInitializer(String mapName, AmqpOrdinalVersionMap indexMap,
		int indentSize)
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
				sb.append(indent + mapName + "[\"" + version.toString() + "\"] = " +
					index + ";" + cr);
			}
		}
		return sb.toString();		
	}

	protected String generateFieldDeclaration(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return Utils.createSpaces(indentSize) + codeType + " " + field.name +
			"; /* AMQP version(s): " + versionSet + " */" + cr;
	}

	protected String generateMangledFieldDeclaration(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
	    throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			String domainName = dItr.next();
			AmqpVersionSet versionSet = field.domainMap.get(domainName);
			String codeType = getGeneratedType(domainName, versionSet.first());
			sb.append(indent + codeType + " " + field.name + "_" + (domainCntr++) +
				"; /* AMQP Version(s): " + versionSet + " */" + cr);
		}
		return sb.toString();		
	}
	
	protected String generateMbGetMethod(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		String indent = Utils.createSpaces(indentSize);
			return indent + "inline " + setRef(codeType) + " get" +
				Utils.firstUpper(field.name) + "() { return " + field.name +
				"; } /* AMQP Version(s): " + versionSet + " */" + cr;
	}
		
	protected String generateMbMangledGetMethod(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		Iterator<String> dItr = field.domainMap.keySet().iterator();
		int domainCntr = 0;
		while (dItr.hasNext())
		{
			String domainName = dItr.next();
			AmqpVersionSet versionSet = field.domainMap.get(domainName);
			String codeType = getGeneratedType(domainName, versionSet.first());
			sb.append(indent + "inline " + setRef(codeType) + " get" +
				Utils.firstUpper(field.name) + "() { return " + field.name + "_" +
				domainCntr++ + "; } /* AMQP Version(s): " + versionSet +
				" */" + cr);
		}
		return sb.toString();		
	}
	
	protected String generateMbParamList(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return mbParamList(codeType, field, versionSet, indentSize, nextFlag, false, false);
	}
	
	protected String generateMbMangledParamList(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		return mbMangledParamList(field, indentSize, nextFlag, false, false);
	}
	
	protected String generateMbParamDeclareList(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return mbParamList(codeType, field, versionSet, indentSize, nextFlag, true, false);
	}
	
	protected String generateMbMangledParamDeclareList(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		return mbMangledParamList(field, indentSize, nextFlag, true, false);
	}
	
	protected String generateMbParamInitList(String codeType, AmqpField field,
		AmqpVersionSet versionSet, int indentSize, int tabSize, boolean nextFlag)
	{
		return mbParamList(codeType, field, versionSet, indentSize, nextFlag, false, true);
	}
	
	protected String generateMbMangledParamInitList(AmqpField field, int indentSize,
		int tabSize, boolean nextFlag)
		throws AmqpTypeMappingException
	{
		return mbMangledParamList(field, indentSize, nextFlag, false, true);
	}
	
	protected String mbParamList(String codeType, AmqpField field, AmqpVersionSet versionSet,
		int indentSize, boolean nextFlag, boolean defineFlag, boolean initializerFlag)
		{
			return Utils.createSpaces(indentSize) + (defineFlag ? codeType + " " : "") +
				(initializerFlag ? "this." : "") + field.name +
				(initializerFlag ? "(" + field.name + ")" : "") +
				(nextFlag ? "," : "") + " /* AMQP version(s): " + versionSet + " */" + cr;
		}
	
	protected String mbMangledParamList(AmqpField field, int indentSize,
		boolean nextFlag, boolean defineFlag, boolean initializerFlag)
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
			sb.append(Utils.createSpaces(indentSize) + (defineFlag ? codeType + " " : "") +
				(initializerFlag ? "this." : "") + field.name + "_" + domainCntr +
				(initializerFlag ? "(" + field.name + "_" + domainCntr + ")" : "") +
				(nextFlag ? "," : "") + " /* AMQP version(s): " + versionSet + " */" + cr);
			domainCntr++;
		}
		return sb.toString();		
	}
	
	protected String generateMbFieldPrint(String domain, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer(indent + "out << \"");
		if (ordinal == 0)
			sb.append(": \"");
		else
			sb.append("; \"");
		sb.append(" << \"" + fieldName + "=\" << " + fieldName + ";" + cr);		
		return sb.toString();
	}
	
	protected String generateMbBitFieldPrint(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<bitFieldList.size(); i++)
		{
			String bitFieldName = bitFieldList.get(i);
			sb.append(indent + "out << \"");
			if (ordinal-bitFieldList.size()+i == 0)
				sb.append(": \"");
			else
				sb.append("; \"");
			sb.append(" << \"" + bitFieldName + "=\" << (" + bitFieldName +
				" ? \"T\" : \"F\");" + cr);
		}		
		return sb.toString();
	}

	protected String generateMbFieldSize(String domainType, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(Utils.createSpaces(indentSize) + "size += " +
			typeMap.get(domainType).size.replaceAll("#", fieldName) +
			"; /* " + fieldName + ": " + domainType + " */" + cr);
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
			"; /* " + comment + " */" + cr);
		return sb.toString();
	}

	protected String generateMbFieldEncode(String domain, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(Utils.createSpaces(indentSize) +
			typeMap.get(domain).encodeExpression.replaceAll("#", fieldName) +
			"; /* " + fieldName + ": " + domain + " */" + cr);
		return sb.toString();
	}

	protected String generateMbBitFieldEncode(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		int numBytes = (bitFieldList.size() - 1)/8 + 1;
		String bitArrayName = "flags_" + ordinal;
		StringBuffer sb = new StringBuffer(indent + "u_int8_t[" + numBytes + "] " +
			bitArrayName + " = {0};" +
			(numBytes != 1 ? " /* All array elements will be initialized to 0 */" : "") +
			cr);
		for (int i=0; i<bitFieldList.size(); i++)
		{
			int bitIndex = i%8;
			int byteIndex = i/8;
			sb.append(indent + bitArrayName + "[" + byteIndex + "] |= " + bitFieldList.get(i) +
				" << " + bitIndex + "; /* " + bitFieldList.get(i) + ": bit */" + cr);
		}
		for (int i=0; i<numBytes; i++)
		{
			sb.append(indent + "buffer.putOctet(" + bitArrayName + "[" + i + "]);" + cr);
		}
		return sb.toString();
	}

	protected String generateMbFieldDecode(String domain, String fieldName,
		int ordinal, int indentSize, int tabSize)
	{
		StringBuffer sb = new StringBuffer();
		sb.append(Utils.createSpaces(indentSize) +
			typeMap.get(domain).decodeExpression.replaceAll("#", fieldName) +
			"; /* " + fieldName + ": " + domain + " */" + cr);
		return sb.toString();
	}

	protected String generateMbBitFieldDecode(ArrayList<String> bitFieldList,
		int ordinal, int indentSize, int tabSize)
	{
		String indent = Utils.createSpaces(indentSize);
		int numBytes = (bitFieldList.size() - 1)/8 + 1;
		String bitArrayName = "flags_" + ordinal;
		StringBuffer sb = new StringBuffer(indent + "u_int8_t[" + numBytes + "] " +
			bitArrayName + ";" + cr);
		for (int i=0; i<numBytes; i++)
		{
			sb.append(indent + "buffer.getOctet(" + bitArrayName + "[" + i + "]);" + cr);
		}
		for (int i=0; i<bitFieldList.size(); i++)
		{
			int bitIndex = i%8;
			int byteIndex = i/8;
			sb.append(indent + bitFieldList.get(i) + " = (1 << " + bitIndex + ") & " +
				bitArrayName + "[" + byteIndex + "]; /* " + bitFieldList.get(i) +
				": bit */" + cr);
		}
		return sb.toString();
	}
	
	private String setRef(String codeType)
	{
		if (codeType.compareTo("string") == 0 ||
			codeType.compareTo("FieldTable") == 0)
			return "const " + codeType + "&";
		return codeType;
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
