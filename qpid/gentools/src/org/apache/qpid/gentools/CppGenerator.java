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
	protected static final String versionNamespaceStartToken = "${version_namespace_start}";
	protected static final String versionNamespaceEndToken = "${version_namespace_end}";
	
	// TODO: Move this to parent class
	protected static final int FIELD_NAME = 0;
	protected static final int FIELD_CODE_TYPE = 1;
    
    /**
     * A complete list of C++ reserved words. The names of varous XML elements within the AMQP
     * specification file are used for C++ identifier names in the generated code. Each proposed
     * name is checked against this list and is modified (by adding an '_' to the end of the
     * name - see function parseForReservedWords()) if found to be present.
     */
	protected static final String[] cppReservedWords = {"and", "and_eq", "asm", "auto", "bitand",
		"bitor", "bool", "break", "case", "catch", "char", "class", "compl", "const", "const_cast",
		"continue", "default", "delete", "do", "DomainInfo", "double", "dynamic_cast", "else",
		"enum", "explicit", "extern", "false", "float", "for", "friend", "goto", "if", "inline",
		"int", "long", "mutable", "namespace", "new", "not", "not_eq", "operator", "or", "or_eq",
		"private", "protected", "public", "register", "reinterpret_cast", "return", "short",
		"signed", "sizeof", "static", "static_cast", "struct", "switch", "template", "this",
		"throw", "true", "try", "typedef", "typeid", "typename", "union", "unsigned", "using",
		"virtual", "void", "volatile", "wchar_t", "while", "xor", "xor_eq"};
    
    /**
     * Although not reserved words, the following list of variable names that may cause compile
     * problems within a C++ environment because they clash with common #includes. The names of
     * varous XML elements within the AMQP specification file are used for C++ identifier names
     * in the generated code. Each proposed name is checked against this list and is modified
     * (by adding an '_' to the end of the name - see function parseForReservedWords()) if found
     * to be present. This list is best added to on an as-needed basis.
     */
    protected static final String[] cppCommonDefines = {"string"};
    
    // TODO: Move this to the Generator superclass?
    protected boolean quietFlag; // Supress warning messages to the console
	
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
        quietFlag = true;
		// Load C++ type and size maps.
		// Adjust or add to these lists as new types are added/defined.
		// The char '#' will be replaced by the field variable name (any type).
		// The char '~' will be replaced by the compacted bit array size (type bit only).
		typeMap.put("bit", new DomainInfo(
				"bool",					// type
				"~", 					// size
		        "",						// encodeExpression
    			""));					// decodeExpression
		typeMap.put("content", new DomainInfo(
				"Content",				// type
				"#.size()", 			// size
		        "buffer.putContent(#)",	// encodeExpression
    			"buffer.getContent(#)")); // decodeExpression
		typeMap.put("long", new DomainInfo(
				"u_int32_t",			// type
				"4", 					// size
                "buffer.putLong(#)",	// encodeExpression
				"# = buffer.getLong()"));	// decodeExpression
		typeMap.put("longlong", new DomainInfo(
				"u_int64_t",			// type
				"8", 					// size
                "buffer.putLongLong(#)", // encodeExpression
				"# = buffer.getLongLong()")); // decodeExpression
		typeMap.put("longstr", new DomainInfo(
				"string",				// type
				"4 + #.length()", 		// size
                "buffer.putLongString(#)", // encodeExpression
				"buffer.getLongString(#)")); // decodeExpression
		typeMap.put("octet", new DomainInfo(
				"u_int8_t",				// type
				"1", 					// size
                "buffer.putOctet(#)",	// encodeExpression
				"# = buffer.getOctet()"));	// decodeExpression
		typeMap.put("short", new DomainInfo(
				"u_int16_t",			// type
				"2",					// size
                "buffer.putShort(#)",	// encodeExpression
				"# = buffer.getShort()"));	// decodeExpression
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

    public boolean isQuietFlag()
    {
        return quietFlag;
    }

    public void setQuietFlag(boolean quietFlag)
    {
        this.quietFlag = quietFlag;
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
        if (version == null)
            version = globalVersionSet.first();
		return globalDomainMap.getDomainType(domainName, version);
	}
	
	public String getGeneratedType(String domainName, AmqpVersion version)
		throws AmqpTypeMappingException
	{
		String domainType = getDomainType(domainName, version);
		if (domainType == null)
        {
			throw new AmqpTypeMappingException("Domain type \"" + domainName +
				"\" not found in C++ typemap.");
        }
        DomainInfo info = typeMap.get(domainType);
        if (info == null)
        {
            throw new AmqpTypeMappingException("Unknown domain: \"" + domainType + "\"");
        }
        return info.type;
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
					for (AmqpVersion v : method.versionSet)
					{
						StringBuffer nssb = new StringBuffer(namespaceSpan);
						processTemplate(nssb, thisClass, method, null, template[templateFileNameIndex],	v);
						sb.insert(namespaceStartIndex, nssb);						
					}
                    // Process all tokens *not* within the namespace span prior to inserting namespaces
                    processTemplate(sb, thisClass, method, null, template[templateFileNameIndex], null);
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
	protected void processTemplateD(String[] template, AmqpClass thisClass, AmqpMethod method,
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
			System.out.println("ERROR: " + templateFileName + ": " + e.getMessage());
		}
		try { processAllTokens(sb, thisClass, method, field, version); }
		catch (AmqpTemplateException e)
		{
			System.out.println("ERROR: " + templateFileName + ": " + e.getMessage());
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
        if (token.compareTo("${mb_constructor_with_initializers}") == 0)
            return generateConstructor(thisClass, method, version, 4, 4);
        if (token.compareTo("${mb_server_operation_invoke}") == 0)
            return generateServerOperationsInvoke(thisClass, method, version, 4, 4);
        if (token.compareTo("${mb_buffer_param}") == 0)
            return method.fieldMap.size() > 0 ? " buffer" : "";
        if (token.compareTo("${hv_latest_major}") == 0)
            return String.valueOf(globalVersionSet.last().getMajor());
        if (token.compareTo("${hv_latest_minor}") == 0)
            return String.valueOf(globalVersionSet.last().getMinor());
            
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
        else if (token.compareTo("${cph_handler_pointer_defn}") == 0)
        {
            codeSnippet = generateHandlerPointerDefinitions(model, false, 4);
        }
        else if (token.compareTo("${cph_handler_pointer_get_method}") == 0)
        {
            codeSnippet = generateHandlerPointerGetMethods(model, false, 4);
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
        else if (token.compareTo("${sph_handler_pointer_defn}") == 0)
        {
            codeSnippet = generateHandlerPointerDefinitions(model, true, 4);
        }
        else if (token.compareTo("${sph_handler_pointer_get_method}") == 0)
        {
            codeSnippet = generateHandlerPointerGetMethods(model, true, 4);
        }
		
        // amqp_methods.h/cpp
        else if (token.compareTo("${mh_method_body_class_indlude}") == 0)
        {
            codeSnippet = generateMethodBodyIncludeList(model, 0);
        }
        else if (token.compareTo("${mh_method_body_class_instance}") == 0)
        {
            codeSnippet = generateMethodBodyInstances(model, 0);
        }
        else if (token.compareTo("${mc_create_method_body_map_entry}") == 0)
        {
            codeSnippet = generateMethodBodyMapEntry(model, 4);
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
        String codeSnippet;
        int lend = sb.indexOf(cr, listMarkerStartIndex) + 1; // Include cr at end of line
        String tline = sb.substring(listMarkerEndIndex, lend); // Line excluding line marker, including cr
        int tokxStart = tline.indexOf('$');
        String token = tline.substring(tokxStart).trim();
        sb.delete(listMarkerStartIndex, lend);
        
        if (token.compareTo("${cpc_method_body_include}") == 0)
        {
            codeSnippet = generateMethodBodyIncludes(thisClass, 0);
        }
        else if (token.compareTo("${spc_method_body_include}") == 0)
        {
            codeSnippet = generateMethodBodyIncludes(thisClass, 0);
        }
        else if (token.compareTo("${mc_method_body_include}") == 0)
        {
            codeSnippet = generateMethodBodyIncludes(thisClass, 0);
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
        
        if (token.compareTo("${ch_get_value_method}") == 0)
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
    
    protected String generateConstantGetMethods(AmqpConstantSet constantSet,
        int indentSize, int tabSize)
        throws AmqpTypeMappingException
    {
        String indent = Utils.createSpaces(indentSize);
        StringBuffer sb = new StringBuffer();
        for (AmqpConstant thisConstant : constantSet)
        {
            if (thisConstant.isVersionConsistent(globalVersionSet))
            {
                // return a constant
                String value = thisConstant.firstKey();
                sb.append(indent + "static const char* " + thisConstant.name + "() { return \"" +
                    thisConstant.firstKey() + "\"; }" + cr);
                if (Utils.containsOnlyDigits(value))
                {
                    sb.append(indent + "static int " + thisConstant.name + "AsInt() { return " +
                        thisConstant.firstKey() + "; }" + cr);
                }
                if (Utils.containsOnlyDigitsAndDecimal(value))
                {
                    sb.append(indent + "static double " + thisConstant.name + "AsDouble() { return (double)" +
                        thisConstant.firstKey() + "; }" + cr);
                }
                sb.append(cr);
            }
            else
            {
                // Return version-specific constant
                sb.append(generateVersionDependentGet(thisConstant, "const char*", "", "\"", "\"", indentSize, tabSize));
                sb.append(generateVersionDependentGet(thisConstant, "int", "AsInt", "", "", indentSize, tabSize));
                sb.append(generateVersionDependentGet(thisConstant, "double", "AsDouble", "(double)", "", indentSize, tabSize));
                sb.append(cr);
            }
        }        
        return sb.toString();       
    }
    
    protected String generateVersionDependentGet(AmqpConstant constant, String methodReturnType,
        String methodNameSuffix, String returnPrefix, String returnPostfix, int indentSize, int tabSize)
        throws AmqpTypeMappingException
    {
        String indent = Utils.createSpaces(indentSize);
        String tab = Utils.createSpaces(tabSize);
        StringBuffer sb = new StringBuffer();
        sb.append(indent + methodReturnType + " " + constant.name + methodNameSuffix +
            "() const" + cr);
        sb.append(indent + "{" + cr);
        boolean first = true;
        for (String thisValue : constant.keySet())
        {
            AmqpVersionSet versionSet = constant.get(thisValue);
            sb.append(indent + tab + (first ? "" : "else ") + "if (" + generateVersionCheck(versionSet) +
                ")" + cr);
            sb.append(indent + tab + "{" + cr);
            if (methodReturnType.compareTo("int") == 0 && !Utils.containsOnlyDigits(thisValue))
            {
                sb.append(generateConstantDeclarationException(constant.name, methodReturnType,
                    indentSize + (2*tabSize), tabSize));
            }
            else if (methodReturnType.compareTo("double") == 0 && !Utils.containsOnlyDigitsAndDecimal(thisValue))
            {
                sb.append(generateConstantDeclarationException(constant.name, methodReturnType,
                    indentSize + (2*tabSize), tabSize));                            
            }
            else
            {
                sb.append(indent + tab + tab + "return " + returnPrefix + thisValue + returnPostfix + ";" + cr);
            }
            sb.append(indent + tab + "}" + cr);
            first = false;
        }
        sb.append(indent + tab + "else" + cr);
        sb.append(indent + tab + "{" + cr);
        sb.append(indent + tab + tab + "std::stringstream ss;" + cr);
        sb.append(indent + tab + tab + "ss << \"Constant \\\"" + constant.name +
            "\\\" is undefined for AMQP version \" <<" + cr);
        sb.append(indent + tab + tab + tab + "version.toString() << \".\";" + cr);
        sb.append(indent + tab + tab + "throw ProtocolVersionException(ss.str());" + cr);
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
        sb.append(indent + "std::stringstream ss;" + cr);
        sb.append(indent + "ss << \"Constant \\\"" + name + "\\\" cannot be converted to type " +
            methodReturnType + " for AMQP version \" <<" + cr);        
        sb.append(indent + tab + "version.toString() << \".\";" + cr);        
        sb.append(indent + "throw ProtocolVersionException(ss.str());" + cr);        
        return sb.toString();       
    }
	
	// Methods used for generation of code snippets for Server/ClientOperations class generation
	
	protected String generateOpsMethodHandlerGetMethods(AmqpModel model, boolean serverFlag, int indentSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		for (String thisClassName : model.classMap.keySet())
		{
			AmqpClass thisClass = model.classMap.get(thisClassName);
			// Only generate for this class if there is at least one method of the
			// required chassis (server/client flag).
			boolean chassisFoundFlag = false;
			for (String thisMethodName : thisClass.methodMap.keySet())
			{
				AmqpMethod method = thisClass.methodMap.get(thisMethodName);
				boolean clientChassisFlag = method.clientMethodFlagMap.isSet();
				boolean serverChassisFlag = method.serverMethodFlagMap.isSet();
				if ((serverFlag && serverChassisFlag) || (!serverFlag && clientChassisFlag))
					chassisFoundFlag = true;
			}
			if (chassisFoundFlag)
			{
				sb.append(indent + "virtual AMQP_" + (serverFlag ? "Server" : "Client") + "Operations::" +
				    thisClass.name + "Handler* get" + thisClass.name + "Handler() = 0;" + cr);
			}
		}
		return sb.toString();
	}
	
	protected String generateOpsInnerClasses(AmqpModel model, boolean serverFlag, int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		
		String proxyClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Proxy";
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		for (String thisClassName : model.classMap.keySet())
		{
			AmqpClass thisClass = model.classMap.get(thisClassName);
			String handlerClassName = thisClass.name + "Handler";
			if (!first)
				sb.append(cr);
			sb.append(indent + "// ==================== class " + handlerClassName +
				" ====================" + cr);
			sb.append(indent + "class " + handlerClassName);
			if (thisClass.versionSet.size() != globalVersionSet.size())
				sb.append(" // AMQP Version(s) " + thisClass.versionSet + cr);
			else
				sb.append(cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + "private:" + cr);
			sb.append(indent + tab + proxyClassName+ "* parent;" + cr);
            sb.append(cr);
            sb.append(indent + tab + "// Constructors and destructors" + cr);
            sb.append(cr);
			sb.append(indent + "protected:" + cr);
            sb.append(indent + tab + handlerClassName + "() {}" + cr);
			sb.append(indent + "public:" + cr);
			sb.append(indent + tab + handlerClassName +
				"(" + proxyClassName + "* _parent) {parent = _parent;}" + cr);
			sb.append(indent + tab + "virtual ~" + handlerClassName + "() {}" + cr);
			sb.append(cr);
			sb.append(indent + tab + "// Protocol methods" + cr);
			sb.append(cr);
			sb.append(generateInnerClassMethods(thisClass, serverFlag, true, indentSize + tabSize, tabSize));
			sb.append(indent + "}; // class " + handlerClassName + cr);
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
        String outerClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + (abstractMethodFlag ? "Operations" : "Proxy");
		boolean first = true;
		for (String thisMethodName : thisClass.methodMap.keySet())
		{
			AmqpMethod method = thisClass.methodMap.get(thisMethodName);
			boolean clientChassisFlag = method.clientMethodFlagMap.isSet();
			boolean serverChassisFlag = method.serverMethodFlagMap.isSet();
			if ((serverFlag && serverChassisFlag) || (!serverFlag && clientChassisFlag))
			{
				String methodName = parseForReservedWords(method.name, outerClassName + "." + thisClass.name);				
				AmqpOverloadedParameterMap overloadededParameterMap =
					method.getOverloadedParameterLists(thisClass.versionSet, this);
				for (AmqpOrdinalFieldMap thisFieldMap : overloadededParameterMap.keySet())
				{
					AmqpVersionSet versionSet = overloadededParameterMap.get(thisFieldMap);
					if (!first)
						sb.append(cr);
					sb.append(indent + "virtual void " + methodName + "( u_int16_t channel");
					sb.append(generateMethodParameterList(thisFieldMap, indentSize + (5*tabSize), true, true, true));
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

    protected String generateHandlerPointerDefinitions(AmqpModel model, boolean serverFlag,
        int indentSize)
    {
        String indent = Utils.createSpaces(indentSize);
        StringBuffer sb = new StringBuffer();
        String outerClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Operations";
        for (String thisClassName : model.classMap.keySet())
        {
            AmqpClass thisClass = model.classMap.get(thisClassName);
            sb.append(indent + outerClassName + "::" + thisClass.name + "Handler* " +
                thisClass.name + "HandlerPtr;" + cr);
        }
        return sb.toString();
    }
    
    protected String generateHandlerPointerGetMethods(AmqpModel model, boolean serverFlag,
        int indentSize)
    {
        String indent = Utils.createSpaces(indentSize);
        StringBuffer sb = new StringBuffer();
        String outerClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Operations";
        for (String thisClassName : model.classMap.keySet())
        {
            AmqpClass thisClass = model.classMap.get(thisClassName);
            sb.append(indent + "virtual inline " + outerClassName + "::" + thisClass.name + "Handler* get" +
                thisClass.name + "Handler() { return &" + Utils.firstLower(thisClass.name) + ";}" + cr);
        }
        return sb.toString();
   }
    	
	protected String generateProxyInnerClassInstances(AmqpModel model, boolean serverFlag,
		int indentSize)
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
        String outerClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Proxy";
        for (String thisClassName : model.classMap.keySet())
		{
			AmqpClass thisClass = model.classMap.get(thisClassName);
			String instanceName = parseForReservedWords(Utils.firstLower(thisClass.name), outerClassName);
			String className = parseForReservedWords(thisClass.name, null);
			sb.append(indent + className + " " + instanceName + ";");
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
        String outerClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Proxy";
        for (String thisClassName : model.classMap.keySet())
		{
			AmqpClass thisClass = model.classMap.get(thisClassName);
			String className = parseForReservedWords(thisClass.name, outerClassName);
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
		String proxyClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Proxy";
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		boolean first = true;
        for (String thisClassName : model.classMap.keySet())
		{
			AmqpClass thisClass = model.classMap.get(thisClassName);
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
            sb.append(indent + tab + "OutputHandler* out;" + cr);
			sb.append(indent + tab + proxyClassName + "* parent;" + cr);
			sb.append(cr);
			sb.append(indent + "public:" + cr);
			sb.append(indent + tab + "// Constructors and destructors" + cr);
			sb.append(cr);
			sb.append(indent + tab + className + "(OutputHandler* out, " + proxyClassName + "* _parent) : " + cr);
			sb.append(indent + tab + tab + "out(out) {parent = _parent;}" + cr);
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
        String outerClassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Proxy";
        String superclassName = "AMQP_" + (serverFlag ? "Server" : "Client") + "Operations";
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer(indent + superclassName + "(major, minor)," + cr);
		sb.append(indent + "version(major, minor)," + cr);
        sb.append(indent + "out(out)");
		Iterator<String> cItr = model.classMap.keySet().iterator();
		while (cItr.hasNext())
		{
			AmqpClass thisClass = model.classMap.get(cItr.next());
			String instanceName = parseForReservedWords(Utils.firstLower(thisClass.name), outerClassName);
			sb.append("," + cr);
			sb.append(indent + instanceName + "(out, this)");
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
			String instanceName = parseForReservedWords(Utils.firstLower(thisClass.name), outerClassName);
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
        for (String thisClassName : model.classMap.keySet())
		{
			AmqpClass thisClass = model.classMap.get(thisClassName);
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
		for (String thisMethodName : thisClass.methodMap.keySet())
		{
			AmqpMethod method = thisClass.methodMap.get(thisMethodName);
			String methodBodyClassName = thisClass.name + Utils.firstUpper(method.name) + "Body";
			boolean clientChassisFlag = method.clientMethodFlagMap.isSet();
			boolean serverChassisFlag = method.serverMethodFlagMap.isSet();
            boolean versionConsistentFlag = method.isVersionConsistent(globalVersionSet);
			if ((serverFlag && serverChassisFlag) || (!serverFlag && clientChassisFlag))
			{
				String methodName = parseForReservedWords(method.name, outerclassName + "." + thisClass.name);
				AmqpOverloadedParameterMap overloadededParameterMap =
					method.getOverloadedParameterLists(thisClass.versionSet, this);
				for (AmqpOrdinalFieldMap thisFieldMap : overloadededParameterMap.keySet())
				{
					AmqpVersionSet versionSet = overloadededParameterMap.get(thisFieldMap);
					if (!first)
						sb.append(cr);
					sb.append(indent + "void " + outerclassName + "::" + thisClass.name + "::" +
                        methodName + "( u_int16_t channel");
					sb.append(generateMethodParameterList(thisFieldMap, indentSize + (5*tabSize), true, true, true));
					sb.append(" )");
					if (versionSet.size() != globalVersionSet.size())
						sb.append(" // AMQP Version(s) " + versionSet);
					sb.append(cr);
					sb.append(indent + "{" + cr);
					sb.append(generateMethodBodyCallContext(thisFieldMap, outerclassName, methodBodyClassName,
                        versionConsistentFlag, versionSet, indentSize + tabSize, tabSize));
					sb.append(indent + "}" + cr);
					sb.append(cr);
					first = false;
				}
			}
		}
		return sb.toString();		
	}
	
	protected String generateMethodBodyCallContext(AmqpOrdinalFieldMap fieldMap, String outerclassName,
		String methodBodyClassName, boolean versionConsistentFlag, AmqpVersionSet versionSet,
        int indentSize, int tabSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		String tab = Utils.createSpaces(tabSize);
		StringBuffer sb = new StringBuffer();
		if (versionConsistentFlag)
		{
			sb.append(generateMethodBodyCall(fieldMap, methodBodyClassName, null, indentSize, tabSize));
		}
		else
		{
			boolean firstOverloadedMethodFlag = true;
			for (AmqpVersion thisVersion : versionSet)
			{
				sb.append(indent);
				if (!firstOverloadedMethodFlag)
					sb.append("else ");
				sb.append("if (" + generateVersionCheck(thisVersion) + ")" + cr);
				sb.append(indent + "{" + cr);
				sb.append(generateMethodBodyCall(fieldMap, methodBodyClassName, thisVersion,
					indentSize + tabSize, tabSize));
				sb.append(indent + "}" + cr);
				firstOverloadedMethodFlag = false;
			}
			sb.append(indent + "else" + cr);
			sb.append(indent + "{" + cr);
			sb.append(indent + tab + "std::stringstream ss;" + cr);
			sb.append(indent + tab + "ss << \"Call to " + outerclassName + "::" + methodBodyClassName +
				"(u_int16_t" + generateMethodParameterList(fieldMap, 0, true, true, false) + ")\"" + cr);
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
		StringBuffer sb = new StringBuffer(indent + "out->send( new AMQFrame(parent->getProtocolVersion(), channel," + cr);
		sb.append(indent + tab + "new " + namespace + methodBodyClassName + "( parent->getProtocolVersion()");
		sb.append(generateMethodParameterList(fieldMap, indentSize + (5*tabSize), true, false, true));
		sb.append(" )));" + cr);	
		return sb.toString();		
	}
    
    protected String generateMethodBodyIncludes(AmqpClass thisClass, int indentSize)
    {
        StringBuffer sb = new StringBuffer();
        if (thisClass != null)
        {
            sb.append(generateClassMethodBodyInclude(thisClass, indentSize));
        }
        else
        {
        	for (String thisClassName : model.classMap.keySet())
            {
                thisClass = model.classMap.get(thisClassName);
                sb.append(generateClassMethodBodyInclude(thisClass, indentSize));
           }
        }
        return sb.toString();       
    }
    
    protected String generateClassMethodBodyInclude(AmqpClass thisClass, int indentSize)
    {
        StringBuffer sb = new StringBuffer();
        String indent = Utils.createSpaces(indentSize);
        for (String thisMethodName : thisClass.methodMap.keySet())
        {
            AmqpMethod method = thisClass.methodMap.get(thisMethodName);
            sb.append(indent + "#include <" + thisClass.name +
                Utils.firstUpper(method.name) + "Body.h>" + cr);
        }
        return sb.toString();       
    }
    
	// Methods used for generation of code snippets for MethodBody class generation

	protected String getIndex(AmqpOrdinalVersionMap indexMap, AmqpVersion version)
		throws AmqpTemplateException
	{
		for (Integer thisIndex : indexMap.keySet())
		{
			AmqpVersionSet versionSet = indexMap.get(thisIndex);
			if (versionSet.contains(version))
				return String.valueOf(thisIndex);			
		}
		throw new AmqpTemplateException("Unable to find index for version " + version); 
	}
	
	protected String generateFieldDeclarations(AmqpFieldMap fieldMap, AmqpVersion version, int indentSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
        
        if (version == null)
            version = globalVersionSet.first();
        AmqpOrdinalFieldMap ordinalFieldMap = fieldMap.getMapForVersion(version, true, this);
        for (Integer thisOrdinal : ordinalFieldMap.keySet())
        {
            String[] fieldDomainPair = ordinalFieldMap.get(thisOrdinal);
            sb.append(indent + fieldDomainPair[FIELD_CODE_TYPE] + " " + fieldDomainPair[FIELD_NAME] + ";" + cr);        	
        }
		return sb.toString();
	}
	
	protected String generateFieldGetMethods(AmqpFieldMap fieldMap, AmqpVersion version, int indentSize)
		throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		
        if (version == null)
            version = globalVersionSet.first();
        AmqpOrdinalFieldMap ordinalFieldMap = fieldMap.getMapForVersion(version, true, this);
        for (Integer thisOrdinal : ordinalFieldMap.keySet())
        {
            String[] fieldDomainPair = ordinalFieldMap.get(thisOrdinal);
			sb.append(indent + "inline " + setRef(fieldDomainPair[FIELD_CODE_TYPE]) + " get" +
				Utils.firstUpper(fieldDomainPair[FIELD_NAME]) + "() { return " +
				fieldDomainPair[FIELD_NAME] + "; }" + cr);
        }
		return sb.toString();
	}
	
	protected String generatePrintMethodContents(AmqpFieldMap fieldMap, AmqpVersion version, int indentSize)
        throws AmqpTypeMappingException
	{
		String indent = Utils.createSpaces(indentSize);
		StringBuffer sb = new StringBuffer();
		
        if (version == null)
            version = globalVersionSet.first();
        AmqpOrdinalFieldMap ordinalFieldMap = fieldMap.getMapForVersion(version, true, this);
        boolean firstFlag = true;
        for (Integer thisOrdinal : ordinalFieldMap.keySet())
        {
            String[] fieldDomainPair = ordinalFieldMap.get(thisOrdinal);
            String cast = fieldDomainPair[FIELD_CODE_TYPE].compareTo("u_int8_t") == 0 ? "(int)" : "";
            sb.append(indent + "out << \"");
            if (!firstFlag)
                sb.append("; ");
            sb.append(fieldDomainPair[FIELD_NAME] + "=\" << " + cast + fieldDomainPair[FIELD_NAME] + ";" + cr);
            firstFlag = false;
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
			String domainType = getDomainType(fieldDomainPair[FIELD_CODE_TYPE], thisVersion);
			
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
			String domainType = getDomainType(fieldDomainPair[FIELD_CODE_TYPE], thisVersion);
			
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
		StringBuffer sb = new StringBuffer(indent + "u_int8_t " + bitArrayName +
            "[" + numBytes + "] = {0};" + 
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
			String domainType = getDomainType(fieldDomainPair[FIELD_CODE_TYPE], thisVersion);
			
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
		StringBuffer sb = new StringBuffer(indent + "u_int8_t " + bitArrayName +
            "[" + numBytes + "];" + cr);	
		for (int i=0; i<numBytes; i++)
		{
			sb.append(indent + bitArrayName + "[" + i + "] = buffer.getOctet();" + cr);
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
		while (oItr.hasNext())
		{
			int ordinal = oItr.next();
			String[] fieldDomainPair = ordinalFieldMap.get(ordinal);
			sb.append(indent + (defineFlag ? setRef(fieldDomainPair[FIELD_CODE_TYPE]) + " " : "") +
				fieldDomainPair[FIELD_NAME] + (initializerFlag ? "(" + fieldDomainPair[FIELD_NAME] + ")" : "") +
				(oItr.hasNext() ? "," : "") + cr);
		}
		return sb.toString();				
	}
	
	protected String generateMethodParameterList(AmqpOrdinalFieldMap fieldMap, int indentSize,
		boolean leadingCommaFlag, boolean fieldTypeFlag, boolean fieldNameFlag)
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
			sb.append(
                (fieldTypeFlag ? setRef(field[FIELD_CODE_TYPE]) : "") +
				(fieldNameFlag ? " " + field[FIELD_NAME] : "") +
				(pItr.hasNext() ? "," + (fieldNameFlag ? cr : " ") : ""));
			first = false;
		}
		return sb.toString();		
	}
    
    protected String generateConstructor(AmqpClass thisClass, AmqpMethod method,
        AmqpVersion version, int indentSize, int tabSize)
        throws AmqpTypeMappingException
    {
        String indent = Utils.createSpaces(indentSize);
        String tab = Utils.createSpaces(tabSize);
        StringBuffer sb = new StringBuffer();
        if (method.fieldMap.size() > 0)
        {
            sb.append(indent + thisClass.name + Utils.firstUpper(method.name) + "Body(ProtocolVersion& version," + cr);
            sb.append(generateFieldList(method.fieldMap, version, true, false, 8));
            sb.append(indent + tab + ") :" + cr);
            sb.append(indent + tab + "AMQMethodBody(version)," + cr);
            sb.append(generateFieldList(method.fieldMap, version, false, true, 8));
            sb.append(indent + "{ }" + cr);
       }
        return sb.toString();         
    }
    
    protected String generateServerOperationsInvoke(AmqpClass thisClass, AmqpMethod method,
        AmqpVersion version, int indentSize, int tabSize)
        throws AmqpTypeMappingException
    {
        String indent = Utils.createSpaces(indentSize);
        String tab = Utils.createSpaces(tabSize);
        StringBuffer sb = new StringBuffer();
        
        if (method.serverMethodFlagMap.size() > 0) // At least one AMQP version defines this method as a server method
        {
            Iterator<Boolean> bItr = method.serverMethodFlagMap.keySet().iterator();
            while (bItr.hasNext())
            {
                if (bItr.next()) // This is a server operation
                {
                    boolean fieldMapNotEmptyFlag = method.fieldMap.size() > 0;
                    sb.append(indent + "inline void invoke(AMQP_ServerOperations& target, u_int16_t channel)" + cr);
                    sb.append(indent + "{" + cr);
                    sb.append(indent + tab + "target.get" + thisClass.name + "Handler()->" +
                        parseForReservedWords(Utils.firstLower(method.name),
                        thisClass.name + Utils.firstUpper(method.name) + "Body.invoke()") + "(channel");
                    if (fieldMapNotEmptyFlag)
                    {
                        sb.append("," + cr);
                        sb.append(generateFieldList(method.fieldMap, version, false, false, indentSize + 4*tabSize));
                        sb.append(indent + tab + tab + tab + tab);                    
                    }
                    sb.append(");" + cr);
                    sb.append(indent + "}" + cr);
                }
            }
        }
        return sb.toString();       
    }
    
    // Methods for generation of code snippets for amqp_methods.h/cpp files
    
    protected String generateMethodBodyIncludeList(AmqpModel model, int indentSize)
    {
        String indent = Utils.createSpaces(indentSize);
        StringBuffer sb = new StringBuffer();
        
        for (String thisClassName : model.classMap.keySet())
        {
            AmqpClass thisClass = model.classMap.get(thisClassName);
            for (String thisMethodName : thisClass.methodMap.keySet())
            {
                AmqpMethod method = thisClass.methodMap.get(thisMethodName);
                sb.append(indent + "#include \"" + thisClass.name + Utils.firstUpper(method.name) + "Body.h\"" + cr);
            }
        }
        
        return sb.toString();       
    }
    
    protected String generateMethodBodyInstances(AmqpModel model, int indentSize)
    {
        String indent = Utils.createSpaces(indentSize);
        StringBuffer sb = new StringBuffer();
        
        for (String thisClassName : model.classMap.keySet())
        {
            AmqpClass thisClass = model.classMap.get(thisClassName);
            for (String thisMethodName : thisClass.methodMap.keySet())
            {
                AmqpMethod method = thisClass.methodMap.get(thisMethodName);
                sb.append(indent + "const " + thisClass.name + Utils.firstUpper(method.name) + "Body " +
                    Utils.firstLower(thisClass.name) + "_" + method.name + ";" + cr);
            }
        }
       
        return sb.toString();       
    }
    
    protected String generateMethodBodyMapEntry(AmqpModel model, int indentSize)
        throws AmqpTypeMappingException
    {
        String indent = Utils.createSpaces(indentSize);
        StringBuffer sb = new StringBuffer();
        
        for (AmqpVersion version : globalVersionSet)
        {
            for (String thisClassName : model.classMap.keySet())
            {
                AmqpClass thisClass = model.classMap.get(thisClassName);
                for (String thisMethodName : thisClass.methodMap.keySet())
                {
                    AmqpMethod method = thisClass.methodMap.get(thisMethodName);
                    String namespace = method.isVersionConsistent(globalVersionSet) ? "" : version.namespace() + "::";
                    try
                    {
                        int classOrdinal = thisClass.indexMap.getOrdinal(version);
                        int methodOrdinal = method.indexMap.getOrdinal(version);
                        String methodModyClassName = namespace + thisClass.name + Utils.firstUpper(method.name) + "Body";
                        sb.append(indent + "insert(std::make_pair(createMapKey(" + classOrdinal + ", " +
                            methodOrdinal + ", " + version.getMajor() + ", " + version.getMinor() +
                            "), &createMethodBodyFn<" + methodModyClassName + ">));" + cr);
                    }
                    catch (AmqpTypeMappingException e) {} // ignore
                }
            }
        }
        
        return sb.toString();       
    }
   
    
    // Helper functions
	
	private String generateVersionCheck(AmqpVersion version)
	{
		return "version.equals(" + version.getMajor() + ", " + version.getMinor() + ")";
	}
	
	private String generateVersionCheck(AmqpVersionSet versionSet)
        throws AmqpTypeMappingException
	{
		StringBuffer sb = new StringBuffer();
		for (AmqpVersion v : versionSet)
		{
			if (!v.equals(versionSet.first()))
				sb.append(" || ");
			if (versionSet.size() > 1)
				sb.append("(");
			sb.append("version.equals(" + v.getMajor() + ", " + v.getMinor() + ")");
			if (versionSet.size() > 1)
				sb.append(")");
		}
		return sb.toString();
	}
	
	private String parseForReservedWords(String name, String context)
	{
		for (String cppReservedWord : cppReservedWords)
			if (name.compareTo(cppReservedWord) == 0)
			{
                if (!quietFlag)
                {
                    System.out.println("WARNING: " + (context == null ? "" : context + ": ") +
                        "Found XML method \"" + name + "\", which is a C++ reserved word. " +
                        "Changing generated name to \"" + name + "_\".");
                }
				return name + "_";
			}
        
		for (String cppCommonDefine : cppCommonDefines)
            if (name.compareTo(cppCommonDefine) == 0)
            {
                if (!quietFlag)
                {
                    System.out.println("WARNING: " + (context == null ? "" : context + ": ") +
                        "Found XML method \"" + name + "\", which may clash with commonly used defines within C++. " +
                        "Changing generated name to \"" + name + "_\".");
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
