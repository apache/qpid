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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

public class CppGenerator extends Generator
{
	protected static final String cr = Utils.lineSeparator;
	protected static final String versionNamespaceStartToken = "${version_namespace_start}";
	protected static final String versionNamespaceEndToken = "${version_namespace_end}";
	protected static final int FIELD_NAME = 0;
	protected static final int FIELD_DOMAIN = 1;
	protected static final String[] cppReservedWords = {"and", "and_eq", "asm", "auto", "bitand",
		"bitor", "bool", "break", "case", "catch", "char", "class", "compl", "const", "const_cast",
		"continue", "default", "delete", "do", "DomainInfo", "double", "dynamic_cast", "else",
		"enum", "explicit", "extern", "false", "float", "for", "friend", "goto", "if", "inline",
		"int", "long", "mutable", "namespace", "new", "not", "not_eq", "operator", "or", "or_eq",
		"private", "protected", "public", "register", "reinterpret_cast", "return", "short",
		"signed", "sizeof", "static", "static_cast", "struct", "switch", "template", "this",
		"throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using",
		"virtual", "void", "volatile", "wchar_t", "while", "xor", "xor_eq"};
	
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
		if (version == null)
			version = globalVersionSet.first();
		String domainType = globalDomainMap.getDomainType(domainName, version);
		if (domainType == null)
			throw new AmqpTypeMappingException("Domain type \"" + domainName +
				"\" not found in C++ typemap.");
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
	protected void processTemplate(String[] template)
	    throws IOException, AmqpTemplateException, AmqpTypeMappingException,
	    	IllegalAccessException, InvocationTargetException
   	{
		processTemplate(template, null, null, null);
  	}
	
	@Override
	protected void processTemplate(String[] template, AmqpClass thisClass)
	    throws IOException, AmqpTemplateException, AmqpTypeMappingException,
	    	IllegalAccessException, InvocationTargetException
	{
		processTemplate(template, thisClass, null, null);
	}
	
	@Override
	protected void processTemplate(String[] template, AmqpClass thisClass,
		AmqpMethod method)
	    throws IOException, AmqpTemplateException, AmqpTypeMappingException,
	    	IllegalAccessException, InvocationTargetException
	{
		StringBuffer sb = new StringBuffer(template[templateStringIndex]);
		String filename = prepareFilename(getTemplateFileName(sb), thisClass, method, null);
		boolean templateProcessedFlag = false;
		
		// If method is not version consistent, create a namespace for each version
		// i.e. copy the bit between the versionNamespaceStartToken and versionNamespaceEndToken
		// once for each namespace.
		if (method != null)
		{
			if (!method.isVersionConsistent(globalVersionSet))
			{
				int namespaceStartIndex = sb.indexOf(versionNamespaceStartToken);
				int namespaceEndIndex = sb.indexOf(versionNamespaceEndToken) +
					versionNamespaceEndToken.length();
				if (namespaceStartIndex >= 0 && namespaceEndIndex >= 0 &&
					namespaceStartIndex <= namespaceEndIndex)
				{
					String namespaceSpan = sb.substring(namespaceStartIndex, namespaceEndIndex) + cr;
					sb.delete(namespaceStartIndex, namespaceEndIndex);
					Iterator<AmqpVersion> vItr = method.versionSet.iterator();
					while (vItr.hasNext())
					{
						AmqpVersion version = vItr.next();
						StringBuffer nssb = new StringBuffer(namespaceSpan);
						processTemplate(nssb, thisClass, method, null, template[templateFileNameIndex],
							version);
						sb.insert(namespaceStartIndex, nssb);
					}
				}
				templateProcessedFlag = true;
			}
		}
		// Remove any remaining namespace tags
		int nsTokenIndex = sb.indexOf(versionNamespaceStartToken);
		while (nsTokenIndex > 0)
		{
			sb.delete(nsTokenIndex, nsTokenIndex + versionNamespaceStartToken.length());
			nsTokenIndex = sb.indexOf(versionNamespaceStartToken);
		}
		nsTokenIndex = sb.indexOf(versionNamespaceEndToken);
		while (nsTokenIndex > 0)
		{
			sb.delete(nsTokenIndex, nsTokenIndex + versionNamespaceEndToken.length());
			nsTokenIndex = sb.indexOf(versionNamespaceEndToken);
		}
		
		if (!templateProcessedFlag)
		{
			processTemplate(sb, thisClass, method, null, template[templateFileNameIndex], null);
		}
		writeTargetFile(sb, new File(genDir + Utils.fileSeparator + filename));
		generatedFileCounter ++;
	}
	
	@Override
	protected void processTemplate(String[] template, AmqpClass thisClass, AmqpMethod method,
		AmqpField field)
	    throws IOException, AmqpTemplateException, AmqpTypeMappingException, IllegalAccessException,
	       	InvocationTargetException
	{
		StringBuffer sb = new StringBuffer(template[templateStringIndex]);
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
	    throws AmqpTemplateException
	{
		if (token.compareTo("${GENERATOR}") == 0)
			return generatorInfo;
		if (token.compareTo("${CLASS}") == 0 && thisClass != null)
			return thisClass.name;
		if (token.compareTo("${CLASS_ID_INIT}") == 0 && thisClass != null)
		{
			if (version == null)
				return String.valueOf(thisClass.indexMap.firstKey());
			return getIndex(thisClass.indexMap, version);
		}
		if (token.compareTo("${METHOD}") == 0 && method != null)
			return method.name;
		if (token.compareTo("${METHOD_ID_INIT}") == 0 && method != null)
		{
			if (version == null)
				return String.valueOf(method.indexMap.firstKey());
			return getIndex(method.indexMap, version);
		}
		if (token.compareTo("${FIELD}") == 0 && field != null)
			return field.name;
		if (token.compareTo(versionNamespaceStartToken) == 0 && version != null)
			return "namespace " + version.namespace() + cr + "{";
		if (token.compareTo(versionNamespaceEndToken) == 0 && version != null)
			return "} // namespace " + version.namespace();
		
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
		int tokxStart = tline.indexOf('$');
		String token = tline.substring(tokxStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		// ClientOperations.h
		if (token.compareTo("${coh_method_handler_get_method}") == 0)
		{
			codeSnippet = generateOpsMethodHandlerGetMethods(model, false, 4);
		}
		else if (token.compareTo("${coh_inner_class}") == 0)
		{
			codeSnippet = generateOpsInnerClasses(model, false, 4, 4);
		}
		
		// ServerOperations.h
		else if (token.compareTo("${soh_method_handler_get_method}") == 0)
		{
			codeSnippet = generateOpsMethodHandlerGetMethods(model, true, 4);
		}
		else if (token.compareTo("${soh_inner_class}") == 0)
		{
			codeSnippet = generateOpsInnerClasses(model, true, 4, 4);
		}
		
		// ClientProxy.h/cpp
		else if (token.compareTo("${cph_inner_class_instance}") == 0)
		{
			codeSnippet = generateProxyInnerClassInstances(model, false, 4);
		}
		else if (token.compareTo("${cph_inner_class_get_method}") == 0)
		{
			codeSnippet = generateProxyInnerClassGetMethodDecls(model, false, 4);
		}
		else if (token.compareTo("${cph_inner_class_defn}") == 0)
		{
			codeSnippet = generateProxyInnerClassDefinitions(model, false, 4, 4);
		}
		else if (token.compareTo("${cpc_constructor_initializer}") == 0)
		{
			codeSnippet = generateProxyConstructorInitializers(model, false, 4);
		}
		else if (token.compareTo("${cpc_inner_class_get_method}") == 0)
		{
			codeSnippet = generateProxyInnerClassGetMethodImpls(model, false, 0, 4);
		}
		else if (token.compareTo("${cpc_inner_class_impl}") == 0)
		{
			codeSnippet = generateProxyInnerClassImpl(model, false, 0, 4);
		}
		
		// SerrverProxy.h/cpp
		else if (token.compareTo("${sph_inner_class_instance}") == 0)
		{
			codeSnippet = generateProxyInnerClassInstances(model, true, 4);
		}
		else if (token.compareTo("${sph_inner_class_get_method}") == 0)
		{
			codeSnippet = generateProxyInnerClassGetMethodDecls(model, true, 4);
		}
		else if (token.compareTo("${sph_inner_class_defn}") == 0)
		{
			codeSnippet = generateProxyInnerClassDefinitions(model, true, 4, 4);
		}
		else if (token.compareTo("${spc_constructor_initializer}") == 0)
		{
			codeSnippet = generateProxyConstructorInitializers(model, true, 4);
		}
		else if (token.compareTo("${spc_inner_class_get_method}") == 0)
		{
			codeSnippet = generateProxyInnerClassGetMethodImpls(model, true, 0, 4);
		}
		else if (token.compareTo("${spc_inner_class_impl}") == 0)
		{
			codeSnippet = generateProxyInnerClassImpl(model, true, 0, 4);
		}
		
		else // Oops!
		{
			throw new AmqpTemplateException("Template token \"" + token + "\" unknown.");
		}
		sb.insert(listMarkerStartIndex, codeSnippet);
	}
	
	@Override
	protected void processMethodList(StringBuffer sb, int listMarkerStartIndex, int listMarkerEndIndex,
		AmqpClass thisClass)
        throws AmqpTemplateException, AmqpTypeMappingException
	{
//		TODO
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
		int tokxStart = tline.indexOf('$');
		String token = tline.substring(tokxStart).trim();
		sb.delete(listMarkerStartIndex, lend);
		
		if (token.compareTo("${mb_field_declaration}") == 0)
		{
			codeSnippet = generateFieldDeclarations(fieldMap, version, 4);
		}
		else if (token.compareTo("${mb_field_get_method}") == 0)
		{
			codeSnippet = generateFieldGetMethods(fieldMap, version, 4);
		}
		else if (token.compareTo("${mb_field_print}") == 0)
		{
			codeSnippet = generatePrintMethodContents(fieldMap, version, 8);
		}
		else if (token.compareTo("${mb_body_size}") == 0)
		{
			codeSnippet = generateBodySizeMethodContents(fieldMap, version, 8);
		}
		else if (token.compareTo("${mb_encode}") == 0)
		{
			codeSnippet = generateEncodeMethodContents(fieldMap, version, 8);
		}
		else if (token.compareTo("${mb_decode}") == 0)
		{
			codeSnippet = generateDecodeMethodContents(fieldMap, version, 8);
		}
		else if (token.compareTo("${mb_field_list}") == 0)
		{
			codeSnippet = generateFieldList(fieldMap, version, false, false, 8);
		}
		else if (token.compareTo("${mb_field_list_initializer}") == 0)
		{
			codeSnippet = generateFieldList(fieldMap, version, false, true, 8);
		}
		else if (token.compareTo("${mb_field_list_declare}") == 0)
		{
			codeSnippet = generateFieldList(fieldMap, version, true, false, 8);
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
        int tokxStart = tline.indexOf('$');
        String token = tline.substring(tokxStart).trim();
        sb.delete(listMarkerStartIndex, lend);
        
        if (token.compareTo("${ch_static_const_decl}") == 0)
        {
            codeSnippet = generateConstantDeclarations(constantSet, 4, 4);
        }
        else if (token.compareTo("${ch_get_value_method}") == 0)
        {
            codeSnippet = generateConstantGetMethods(constantSet, 4, 4);
        }

        else // Oops!
        {
            throw new AmqpTemplateException("Template token " + token + " unknown.");
        }
       sb.insert(listMarkerStartIndex, codeSnippet);
        }
		
	// === Protected and private helper functions unique to C++ implementation ===
    
    // Methods for generation of code snippets for AMQP_Constants.h file
    
    protected String generateConstantDeclarations(AmqpConstantSet constantSet,
        int indentSize, int tabSize)
    {
        String indent = Utils.createSpaces(indentSize);
        String tab = Utils.createSpaces(tabSize);
        StringBuffer sb = new StringBuffer();
        Iterator<AmqpConstant> cItr = constantSet.iterator();
        while (cItr.hasNext())
        {
            AmqpConstant constant = cItr.next();
            // Do nothing if version is consistent across all versions
            if (!constant.isVersionConsistent(globalVersionSet))
            {
                
            }
        }
        return sb.toString();       
    }
    
    protected String generateConstantGetMethods(AmqpConstantSet constantSet,
        int indentSize, int tabSize)
    {
        String indent = Utils.createSpaces(indentSize);
        String tab = Utils.createSpaces(tabSize);
        StringBuffer sb = new StringBuffer();
        Iterator<AmqpConstant> cItr = constantSet.iterator();
        while (cItr.hasNext())
        {
            AmqpConstant constant = cItr.next();
            if (constant.isVersionConsistent(globalVersionSet))
            {
                // return a constant
                String value = constant.firstKey();
                sb.append(indent + "public const char* " + constant.name + "() const { return \"" +
                    constant.firstKey() + "\"; }" + cr);
                sb.append(indent + "public const string& " + constant.name + "AsString() const { return new string(\"" +
                    constant.firstKey() + "\"); }" + cr);
                if (Utils.containsOnlyDigits(value))
                {
                    sb.append(indent + "public int " + constant.name + "AsInt() const { return " +
                        constant.firstKey() + "; }" + cr);
                }
                if (Utils.containsOnlyDigitsAndDecimal(value))
                {
                    sb.append(indent + "public double " + constant.name + "AsDouble() const { return (double)" +
                        constant.firstKey() + "; }" + cr);
                }
                sb.append(cr);
           }
            else
            {
                // return value from version map
            }
        }        
        return sb.toString();       
    }
	
	// Methods used for generation of code snippets for Server/ClientOperations class generation
	
	protected String generateOpsMethodHandlerGetMethods(AmqpModel model, boolean serverFlag, int indentSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			// Only generate for this class if there is at least one method of the
			// required chassis (server/client flag).
			boolean chassisFoundFlag = false;
			Iterator<String> mItr = thisClass.methodMap.keySet().iterator();
			while (mItr.hasNext() && !chassisFoundFlag)
			{
				AmqpMethod method = thisClass.methodMap.get(mItr.next());
				boolean clientChassisFlag = method.clientMethodFlagMap.isSet();
				boolean serverChassisFlag = method.serverMethodFlagMap.isSet();
				if ((serverFlag && serverChassisFlag) || (!serverFlag && clientChassisFlag))
					chassisFoundFlag = true;
			}
			if (chassisFoundFlag)
				sb.append(indent + "virtual AMQP_ServerOperations::" +
				    thisClass.name + "Handler* get" + thisClass.name + "Handler() = 0;" + cr);
		}
		return sb.toString();
	}
	
	protected String generateOpsInnerClasses(AmqpModel model, boolean serverFlag, int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			String className = thisClass.name + "Handler";
			if (!first)
				sb.append(cr);
			sb.append(indent + "// ==================== class " + className +
				" ====================" + cr);
			sb.append(indent + "class " + className);
			if (thisClass.versionSet.size() != globalVersionSet.size())
				sb.append(" // AMQP Version(s) " + thisClass.versionSet + cr);
			else
				sb.append(cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + "private:" + cr);
			sb.append(indent + tab + "ProtocolVersion version;" + cr);
			sb.append(cr);
			sb.append(indent + "public:" + cr);
			sb.append(indent + tab + "// Constructors and destructors" + cr);
			sb.append(cr);
			sb.append(indent + tab + className +
				"(u_int8_t major, u_int8_t minor) : version(major, minor) {}" + cr);
			sb.append(indent + tab + className +
					"(ProtocolVersion version) : version(version) {}" + cr);
			sb.append(indent + tab + "virtual ~" + className + "() {}" + cr);
			sb.append(cr);
			sb.append(indent + tab + "// Protocol methods" + cr);
			sb.append(cr);
			sb.append(generateInnerClassMethods(thisClass, serverFlag, true, indentSize + tabSize, tabSize));
			sb.append(indent + "}; // class " + className + cr);
			first = false;
		}
		return sb.toString();		
	}
	
	protected String generateInnerClassMethods(AmqpClass thisClass, boolean serverFlag,
		boolean abstractMethodFlag, int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		Iterator<String> mItr = thisClass.methodMap.keySet().iterator();
		while (mItr.hasNext())
		{
			AmqpMethod method = thisClass.methodMap.get(mItr.next());
			boolean clientChassisFlag = method.clientMethodFlagMap.isSet();
			boolean serverChassisFlag = method.serverMethodFlagMap.isSet();
			if ((serverFlag && serverChassisFlag) || (!serverFlag && clientChassisFlag))
			{
				String methodName = parseForReservedWords(method.name, thisClass.name);				
				AmqpOverloadedParameterMap overloadededParameterMap =
					method.getOverloadedParameterLists(thisClass.versionSet, this);
				Iterator<AmqpOrdinalFieldMap> ofmItr = overloadededParameterMap.keySet().iterator();
				while (ofmItr.hasNext())
				{
					AmqpOrdinalFieldMap fieldMap = ofmItr.next();
					AmqpVersionSet versionSet = overloadededParameterMap.get(fieldMap);
					if (!first)
						sb.append(cr);
					sb.append(indent + "virtual void " + methodName + "( u_int16_t channel");
					sb.append(generateMethodParameterList(fieldMap, indentSize + (5*tabSize), true, true));
					sb.append(" )");
					if (abstractMethodFlag)
						sb.append(" = 0");
					sb.append(";");
					if (versionSet.size() != globalVersionSet.size())
						sb.append(" // AMQP Version(s) " + versionSet);
					sb.append(cr);
					first = false;
				}
			}
		}
		return sb.toString();		
	}
	
	// Methods used for generation of code snippets for Server/ClientProxy class generation
	
	protected String generateProxyInnerClassInstances(AmqpModel model, boolean serverFlag,
		int indentSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			String instanceName = parseForReservedWords(Utils.firstLower(thisClass.name), null);
			String className = parseForReservedWords(thisClass.name, null);
			sb.append(indent + instanceName + " " + className + ";");
			if (thisClass.versionSet.size() != globalVersionSet.size())
				sb.append(" // AMQP Version(s) " + thisClass.versionSet + cr);
			else
				sb.append(cr);
		}
		return sb.toString();
	}
	
	protected String generateProxyInnerClassGetMethodDecls(AmqpModel model, boolean serverFlag,
		int indentSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			String className = parseForReservedWords(thisClass.name, null);
			sb.append(indent + className + "& get" + className + "();");
			if (thisClass.versionSet.size() != globalVersionSet.size())
				sb.append(" // AMQP Version(s) " + thisClass.versionSet + cr);
			else
				sb.append(cr);
		}
		return sb.toString();
	}
	
	protected String generateProxyInnerClassDefinitions(AmqpModel model, boolean serverFlag,
		int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			String className = thisClass.name;
			String superclassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Operations::" +
				thisClass.name + "Handler";
			if (!first)
				sb.append(cr);
			sb.append(indent + "// ==================== class " + className +
				" ====================" + cr);
			sb.append(indent + "class " + className + " : virtual public " + superclassName);
			if (thisClass.versionSet.size() != globalVersionSet.size())
				sb.append(" // AMQP Version(s) " + thisClass.versionSet + cr);
			else
				sb.append(cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + "private:" + cr);
			sb.append(indent + tab + "ProtocolVersion version;" + cr);
			sb.append(indent + tab + "OutputHandler* out;" + cr);
			sb.append(cr);
			sb.append(indent + "public:" + cr);
			sb.append(indent + tab + "// Constructors and destructors" + cr);
			sb.append(cr);
			sb.append(indent + tab + className + "(OutputHandler* out, u_int8_t major, u_int8_t minor) : " + cr);
			sb.append(indent + tab + tab + "out(out), version(major, minor) {}" + cr);
			sb.append(indent + tab + className + "(OutputHandler* out, ProtocolVersion version) : " + cr);
			sb.append(indent + tab + tab + "out(out), version(version) {}" + cr);
			sb.append(indent + tab + "virtual ~" + className + "() {}" + cr);
			sb.append(cr);
			sb.append(indent + tab + "// Protocol methods" + cr);
			sb.append(cr);
			sb.append(generateInnerClassMethods(thisClass, serverFlag, false, indentSize + tabSize, tabSize));
			sb.append(indent + "}; // class " + className + cr);
			first = false;
		}
		return sb.toString();
	}
	
	protected String generateProxyConstructorInitializers(AmqpModel model, boolean serverFlag,
		int indentSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer(indent + "out(out)," + cr);
		sb.append(indent + "major(major)," + cr);
		sb.append(indent + "minor(minor)");
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			String instanceName = parseForReservedWords(Utils.firstLower(thisClass.name), null);
			sb.append("," + cr);
			sb.append(indent + instanceName + "(out)");
			if (!cItr.hasNext())
				sb.append(cr);
		}
		return sb.toString();	
	}
	
	protected String generateProxyInnerClassGetMethodImpls(AmqpModel model, boolean serverFlag,
		int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		String outerClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Proxy";
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			String className = thisClass.name;
			String instanceName = parseForReservedWords(Utils.firstLower(thisClass.name), null);
			sb.append(indent + outerClassName + "::" + className + "& " +
				outerClassName + "::get" + className + "()" + cr);
			sb.append(indent + "{" + cr);
			if (thisClass.versionSet.size() != globalVersionSet.size())
			{
				sb.append(indent + tab + "if (!" + generateVersionCheck(thisClass.versionSet) + ")" + cr);
				sb.append(indent + tab + tab + "throw new ProtocolVersionException();" + cr);
			}
			sb.append(indent + tab + "return " + instanceName + ";" + cr);
			sb.append(indent + "}" + cr);
			if (cItr.hasNext())
				sb.append(cr);
		}
		return sb.toString();
	}
	
	protected String generateProxyInnerClassImpl(AmqpModel model, boolean serverFlag,
		int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		boolean firstClassFlag = true;
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			String className = thisClass.name;
			if (!firstClassFlag)
				sb.append(cr);
			sb.append(indent + "// ==================== class " + className +
				" ====================" + cr);
			sb.append(generateInnerClassMethodImpls(thisClass, serverFlag, indentSize, tabSize));
			firstClassFlag = false;
		}
		return sb.toString();
	}
	
	protected String generateInnerClassMethodImpls(AmqpClass thisClass, boolean serverFlag,
		int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		String outerclassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Proxy";
		boolean first = true;
		Iterator<String> mItr = thisClass.methodMap.keySet().iterator();
		while (mItr.hasNext())
		{
			AmqpMethod method = thisClass.methodMap.get(mItr.next());
			String methodBodyClassName = thisClass.name + Utils.firstUpper(method.name) + "Body";
			boolean clientChassisFlag = method.clientMethodFlagMap.isSet();
			boolean serverChassisFlag = method.serverMethodFlagMap.isSet();
			if ((serverFlag && serverChassisFlag) || (!serverFlag && clientChassisFlag))
			{
				String methodName = parseForReservedWords(method.name, thisClass.name);
				AmqpOverloadedParameterMap overloadededParameterMap =
					method.getOverloadedParameterLists(thisClass.versionSet, this);
				Iterator<AmqpOrdinalFieldMap> ofmItr = overloadededParameterMap.keySet().iterator();
				while (ofmItr.hasNext())
				{
					AmqpOrdinalFieldMap fieldMap = ofmItr.next();
					AmqpVersionSet versionSet = overloadededParameterMap.get(fieldMap);
					boolean versionConsistentFlag = versionSet.size() == globalVersionSet.size();
					if (!first)
						sb.append(cr);
					sb.append(indent + "void " + outerclassName + "::" + methodName + "( u_int16_t channel");
					sb.append(generateMethodParameterList(fieldMap, indentSize + (5*tabSize), true, true));
					sb.append(" )");
					if (!versionConsistentFlag)
						sb.append(" // AMQP Version(s) " + versionSet);
					sb.append(cr);
					sb.append(indent + "{" + cr);
					sb.append(generateMethodBodyCallContext(fieldMap, outerclassName, methodBodyClassName,
						versionSet, indentSize + tabSize, tabSize));
					sb.append(indent + "}" + cr);
					sb.append(cr);
					first = false;
				}
			}
		}
		return sb.toString();		
	}
	
	protected String generateMethodBodyCallContext(AmqpOrdinalFieldMap fieldMap, String outerclassName,
		String methodBodyClassName, AmqpVersionSet versionSet, int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		if (versionSet.size() == globalVersionSet.size())
		{
			sb.append(generateMethodBodyCall(fieldMap, methodBodyClassName, null, indentSize, tabSize));
		}
		else
		{
			boolean firstOverloadedMethodFlag = true;
			Iterator<AmqpVersion> vItr = versionSet.iterator();
			while (vItr.hasNext())
			{
				AmqpVersion version = vItr.next();
				sb.append(indent);
				if (!firstOverloadedMethodFlag)
					sb.append("else ");
				sb.append("if (" + generateVersionCheck(version) + ")" + cr);
				sb.append(indent + "{" + cr);
				sb.append(generateMethodBodyCall(fieldMap, methodBodyClassName, version,
					indentSize + tabSize, tabSize));
				sb.append(indent + "}" + cr);
				firstOverloadedMethodFlag = false;
			}
			sb.append(indent + "else" + cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + tab + "stringstream ss;" + cr);
			sb.append(indent + tab + "ss << \"Call to " + outerclassName + "::" + methodBodyClassName +
				"(u_int16_t" + generateMethodParameterList(fieldMap, 0, true, false) + ")\"" + cr);
			sb.append(indent + tab + tab + "<< \" is invalid for AMQP version \" << version.toString() << \".\";" + cr);
			sb.append(indent + tab + "throw new ProtocolVersionException(ss.str());" + cr);
			sb.append(indent + "}" + cr);
		}
		return sb.toString();		
	}
	
	protected String generateMethodBodyCall(AmqpOrdinalFieldMap fieldMap, String methodBodyClassName,
		AmqpVersion version, int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		String namespace = version != null ? version.namespace() + "::" : "";
		StringBuffer sb = new StringBuffer(indent + "out->send( new AMQP_Frame( channel," + cr);
		sb.append(indent + tab + "new " + namespace + methodBodyClassName + "(");
		sb.append(generateMethodParameterList(fieldMap, indentSize + (5*tabSize), false, true));
		sb.append(" )));" + cr);	
		return sb.toString();		
	}
	
	// Methods used for generation of code snippets for MethodBody class generation

	protected String getIndex(AmqpOrdinalVersionMap indexMap, AmqpVersion version)
		throws AmqpTemplateException
	{
		Iterator<Integer> iItr = indexMap.keySet().iterator();
		while (iItr.hasNext())
		{
			int index = iItr.next();
			AmqpVersionSet versionSet = indexMap.get(index);
			if (versionSet.contains(version))
				return String.valueOf(index);
		}
		throw new AmqpTemplateException("Unable to find index for version " + version); 
	}
	
	protected String generateFieldDeclarations(AmqpFieldMap fieldMap, AmqpVersion version, int indentSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		Iterator<String> fItr = fieldMap.keySet().iterator();
		while(fItr.hasNext())
		{
			AmqpField fieldDetails = fieldMap.get(fItr.next());
			if (version == null) // Version consistent - there *should* be only one domain
			{
				String domainName =  fieldDetails.domainMap.firstKey();
				String codeType = getGeneratedType(domainName, globalVersionSet.first());
				sb.append(indent + codeType + " " + fieldDetails.name + ";" + cr);
			}
			else
			{
				Iterator<String> dItr = fieldDetails.domainMap.keySet().iterator();
				while (dItr.hasNext())
				{
					String domainName = dItr.next();
					AmqpVersionSet versionSet = fieldDetails.domainMap.get(domainName);
					if (versionSet.contains(version))
					{
						String codeType = getGeneratedType(domainName, version);
						sb.append(indent + codeType + " " + fieldDetails.name + ";" + cr);
					}
				}
			}
		}
		return sb.toString();
	}
	
	protected String generateFieldGetMethods(AmqpFieldMap fieldMap, AmqpVersion version, int indentSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		Iterator<String> fItr = fieldMap.keySet().iterator();
		while(fItr.hasNext())
		{
			AmqpField fieldDetails = fieldMap.get(fItr.next());
			if (version == null) // Version consistent - there *should* be only one domain
			{
				String domainName =  fieldDetails.domainMap.firstKey();
				String codeType = getGeneratedType(domainName, globalVersionSet.first());
				sb.append(indent + "inline " + setRef(codeType) + " get" +
					Utils.firstUpper(fieldDetails.name) + "() { return " +
					fieldDetails.name + "; }" + cr);
			}
			else
			{
				Iterator<String> dItr = fieldDetails.domainMap.keySet().iterator();
				while (dItr.hasNext())
				{
					String domainName = dItr.next();
					AmqpVersionSet versionSet = fieldDetails.domainMap.get(domainName);
					if (versionSet.contains(version))
					{
						String codeType = getGeneratedType(domainName, version);
						sb.append(indent + "inline " + setRef(codeType) + " get" +
								Utils.firstUpper(fieldDetails.name) + "() { return " +
								fieldDetails.name + "; }" + cr);
					}
				}
			}
		}
		return sb.toString();
	}
	
	protected String generatePrintMethodContents(AmqpFieldMap fieldMap, AmqpVersion version, int indentSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		Iterator<String> fItr = fieldMap.keySet().iterator();
		boolean firstFlag = true;
		while(fItr.hasNext())
		{
			String fieldName = fItr.next();
			AmqpField fieldDetails = fieldMap.get(fieldName);
			if (version == null || fieldDetails.versionSet.contains(version))
			{
				sb.append(indent + "out << \"");
				if (!firstFlag)
					sb.append("; ");
				sb.append(fieldName + "=\" << " + fieldName + ";" + cr);
				firstFlag = false;
			}
		}
		return sb.toString();		
	}
	
	protected String generateBodySizeMethodContents(AmqpFieldMap fieldMap, AmqpVersion version,
		int indentSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		ArrayList<String> bitFieldList = new ArrayList<String>();
		AmqpOrdinalFieldMap ordinalFieldMap = fieldMap.getMapForVersion(version, false, this);
		Iterator<Integer> oItr = ordinalFieldMap.keySet().iterator();
		int ordinal = 0;
		while (oItr.hasNext())
		{
			ordinal = oItr.next();
			String[] fieldDomainPair = ordinalFieldMap.get(ordinal);
			AmqpVersion thisVersion = version == null ? globalVersionSet.first() : version;
			String domainType = getDomainType(fieldDomainPair[FIELD_DOMAIN], thisVersion);
			
			// Defer bit types by adding them to an array. When the first subsequent non-bit
			// type is encountered, then handle the bits. This allows consecutive bits to be
			// placed into the same byte(s) - 8 bits to the byte.
			if (domainType.compareTo("bit") == 0)
			{
				bitFieldList.add(fieldDomainPair[FIELD_NAME]);
			}
			else
			{
				if (bitFieldList.size() > 0) // Handle accumulated bit types (if any)
				{
					sb.append(generateBitArrayBodySizeMethodContents(bitFieldList, ordinal, indentSize));
				}
				sb.append(indent + "size += " +
					typeMap.get(domainType).size.replaceAll("#", fieldDomainPair[FIELD_NAME]) +
				    "; /* " + fieldDomainPair[FIELD_NAME] + ": " +
				    domainType + " */" + cr);
			}
		}
		if (bitFieldList.size() > 0) // Handle any remaining accumulated bit types
		{
			sb.append(generateBitArrayBodySizeMethodContents(bitFieldList, ordinal, indentSize));
		}
		return sb.toString();				
	}

	protected String generateBitArrayBodySizeMethodContents(ArrayList<String> bitFieldList,
		int ordinal, int indentSize)
	{
		int numBytes = ((bitFieldList.size() - 1) / 8) + 1;
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		String comment = bitFieldList.size() == 1 ?
			bitFieldList.get(0) + ": bit" :
			"Combinded bits: " + bitFieldList;
		sb.append(indent + "size += " +
			typeMap.get("bit").size.replaceAll("~", String.valueOf(numBytes)) +
			"; /* " + comment + " */" + cr);
		bitFieldList.clear();		
		return sb.toString();				
	}
	
	protected String generateEncodeMethodContents(AmqpFieldMap fieldMap, AmqpVersion version,
		int indentSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		ArrayList<String> bitFieldList = new ArrayList<String>();
		AmqpOrdinalFieldMap ordinalFieldMap = fieldMap.getMapForVersion(version, false, this);
		Iterator<Integer> oItr = ordinalFieldMap.keySet().iterator();
		int ordinal = 0;
		while (oItr.hasNext())
		{
			ordinal = oItr.next();
			String[] fieldDomainPair = ordinalFieldMap.get(ordinal);
			AmqpVersion thisVersion = version == null ? globalVersionSet.first() : version;
			String domainType = getDomainType(fieldDomainPair[FIELD_DOMAIN], thisVersion);
			
			// Defer bit types by adding them to an array. When the first subsequent non-bit
			// type is encountered, then handle the bits. This allows consecutive bits to be
			// placed into the same byte(s) - 8 bits to the byte.
			if (domainType.compareTo("bit") == 0)
			{
				bitFieldList.add(fieldDomainPair[FIELD_NAME]);
			}
			else
			{
				if (bitFieldList.size() > 0) // Handle accumulated bit types (if any)
				{
					sb.append(generateBitEncodeMethodContents(bitFieldList, ordinal, indentSize));
				}
				sb.append(indent +
					typeMap.get(domainType).encodeExpression.replaceAll("#", fieldDomainPair[FIELD_NAME]) +
					"; /* " + fieldDomainPair[FIELD_NAME] + ": " + domainType + " */"+ cr);
			}
		}
		if (bitFieldList.size() > 0) // Handle any remaining accumulated bit types
		{
			sb.append(generateBitEncodeMethodContents(bitFieldList, ordinal, indentSize));
		}
		
		return sb.toString();				
	}
	
	protected String generateBitEncodeMethodContents(ArrayList<String> bitFieldList, int ordinal,
		int indentSize)
	{
		int numBytes = ((bitFieldList.size() - 1) / 8) + 1;
		String indent = Utils.createSpaces(indentSize);
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
		bitFieldList.clear();		
		return sb.toString();				
	}

	protected String generateDecodeMethodContents(AmqpFieldMap fieldMap, AmqpVersion version,
		int indentSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		ArrayList<String> bitFieldList = new ArrayList<String>();
		AmqpOrdinalFieldMap ordinalFieldMap = fieldMap.getMapForVersion(version, false, this);
		Iterator<Integer> oItr = ordinalFieldMap.keySet().iterator();
		int ordinal = 0;
		while (oItr.hasNext())
		{
			ordinal = oItr.next();
			String[] fieldDomainPair = ordinalFieldMap.get(ordinal);
			AmqpVersion thisVersion = version == null ? globalVersionSet.first() : version;
			String domainType = getDomainType(fieldDomainPair[FIELD_DOMAIN], thisVersion);
			
			// Defer bit types by adding them to an array. When the first subsequent non-bit
			// type is encountered, then handle the bits. This allows consecutive bits to be
			// placed into the same byte(s) - 8 bits to the byte.
			if (domainType.compareTo("bit") == 0)
			{
				bitFieldList.add(fieldDomainPair[FIELD_NAME]);
			}
			else
			{
				if (bitFieldList.size() > 0) // Handle accumulated bit types (if any)
				{
					sb.append(generateBitDecodeMethodContents(bitFieldList, ordinal, indentSize));
				}
				sb.append(indent +
					typeMap.get(domainType).decodeExpression.replaceAll("#", fieldDomainPair[FIELD_NAME]) +
						"; /* " + fieldDomainPair[FIELD_NAME] + ": " + domainType + " */" + cr);
			}
		}
		if (bitFieldList.size() > 0) // Handle any remaining accumulated bit types
		{
			sb.append(generateBitDecodeMethodContents(bitFieldList, ordinal, indentSize));
		}
		
		return sb.toString();				
	}
	
	protected String generateBitDecodeMethodContents(ArrayList<String> bitFieldList, int ordinal,
		int indentSize)
	{
		int numBytes = ((bitFieldList.size() - 1) / 8) + 1;
		String indent = Utils.createSpaces(indentSize);
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
		bitFieldList.clear();		
		return sb.toString();				
	}
	
	protected String generateFieldList(AmqpFieldMap fieldMap, AmqpVersion version, boolean defineFlag,
		boolean initializerFlag, int indentSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		AmqpOrdinalFieldMap ordinalFieldMap = fieldMap.getMapForVersion(version, true, this);
		Iterator<Integer> oItr = ordinalFieldMap.keySet().iterator();
		int ordinal = 0;
		while (oItr.hasNext())
		{
			ordinal = oItr.next();
			String[] fieldDomainPair = ordinalFieldMap.get(ordinal);
			sb.append(indent + (defineFlag ? fieldDomainPair[FIELD_DOMAIN] + " " : "") +
				fieldDomainPair[FIELD_NAME] + (initializerFlag ? "(" + fieldDomainPair[FIELD_NAME] + ")" : "") +
				(oItr.hasNext() ? "," : "") + cr);
		}
		return sb.toString();				
	}
	
	protected String generateMethodParameterList(AmqpOrdinalFieldMap fieldMap, int indentSize,
		boolean leadingCommaFlag, boolean fieldNameFlag)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		Iterator<Integer> pItr = fieldMap.keySet().iterator();
		while(pItr.hasNext())
		{
			String[] field = fieldMap.get(pItr.next());
			if (first && leadingCommaFlag)
			{
				sb.append("," + (fieldNameFlag ? cr : " "));
			}
			if (!first || leadingCommaFlag)
			{
				sb.append(indent);
			}
			sb.append(setRef(field[FIELD_DOMAIN]) +
				(fieldNameFlag ? " " + field[FIELD_NAME] : "") +
				(pItr.hasNext() ? "," + (fieldNameFlag ? cr : " ") : ""));
			first = false;
		}
		return sb.toString();		
	}
	
	private String generateVersionCheck(AmqpVersion version)
	{
		return "version.equals(" + version.getMajor() + ", " + version.getMinor() + ")";
	}
	
	private String generateVersionCheck(AmqpVersionSet versionSet)
        throws AmqpTypeMappingException
	{
		StringBuffer sb = new StringBuffer();
		AmqpVersion[] versionArray = new AmqpVersion[versionSet.size()];
		versionSet.toArray(versionArray);
		for (int i=0; i<versionArray.length; i++)
		{
			if (i != 0)
				sb.append(" || ");
			if (versionArray.length > 1)
				sb.append("(");
			sb.append("version.equals(" + versionArray[i].getMajor() + ", " + versionArray[i].getMinor() + ")");
			if (versionArray.length > 1)
				sb.append(")");
		}
		return sb.toString();
	}
	
	private String parseForReservedWords(String name, String className)
	{
		for (int i=0; i<cppReservedWords.length; i++)
			if (name.compareTo(cppReservedWords[i]) == 0)
			{
				if (className == null)
				{
					System.out.println("WARNING: Found name \"" + name +
						"\", which is a C/C++ reserved word. " +
						"Changing generated name to \"" + name + "_\".");
				}
				else
				{
					System.out.println("WARNING: Found method \"" + name +
						"\" in class \"" + className +
						"\", which is a C/C++ reserved word. " +
						"Changing generated method name to \"" + name + "_\".");
				}
				return name + "_";
			}
		return name;
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
