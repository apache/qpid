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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public abstract class Generator implements LanguageConverter
{
	// This string is reproduced in every generated file as a comment
	// TODO: Tie the version info into the build system.
	protected static final String generatorInfo = "Qpid Gentools v.0.1";
	
	protected ArrayList<String[]> modelTemplateList;
	protected ArrayList<String[]> classTemplateList;
	protected ArrayList<String[]> methodTemplateList;
	protected ArrayList<String[]> fieldTemplateList;
	protected String genDir;
	
	protected AmqpVersionSet globalVersionSet;
	protected AmqpDomainMap globalDomainMap;
	protected AmqpModel model;
	
	protected int generatedFileCounter;
	
	public Generator(AmqpVersionSet versionList)
	{
		this.globalVersionSet = versionList;
		modelTemplateList = new ArrayList<String[]>();
		classTemplateList = new ArrayList<String[]>();
		methodTemplateList = new ArrayList<String[]>();
		fieldTemplateList = new ArrayList<String[]>();
		generatedFileCounter = 0;	
	}
	
	public int getNumberGeneratedFiles()
	{
		return generatedFileCounter;
	}
	
	public void setDomainMap(AmqpDomainMap domainMap)
	{
		this.globalDomainMap = domainMap;
	}
	
	public AmqpDomainMap getDomainMap()
	{
		return globalDomainMap;
	}
	
	public void setModel(AmqpModel model)
	{
		this.model = model;
	}
	
	public AmqpModel getModel()
	{
		return model;
	}
	
	public void initializeTemplates(File[] modelTemplateFiles, File[] classTemplatesFiles,
		File[] methodTemplatesFiles, File[] fieldTemplatesFiles)
	    throws FileNotFoundException, IOException
	{
		if (modelTemplateFiles.length > 0)
		{
			System.out.println("Model template file(s):");
			for (int t=0; t<modelTemplateFiles.length; t++)
			{
				System.out.println("  " + modelTemplateFiles[t].getAbsolutePath());
				String template[] = {modelTemplateFiles[t].getName(), loadTemplate(modelTemplateFiles[t])};
				modelTemplateList.add(template);
			}
		}
		if (classTemplatesFiles.length > 0)
		{
			System.out.println("Class template file(s):");
			for (int c=0; c<classTemplatesFiles.length; c++)
			{
				System.out.println("  " + classTemplatesFiles[c].getAbsolutePath());
				String template[] = {classTemplatesFiles[c].getName(), loadTemplate(classTemplatesFiles[c])};
				classTemplateList.add(template);
			}
		}
		if (methodTemplatesFiles.length > 0)
		{
			System.out.println("Method template file(s):");
			for (int m=0; m<methodTemplatesFiles.length; m++)
			{
				System.out.println("  " + methodTemplatesFiles[m].getAbsolutePath());
				String template[] = {methodTemplatesFiles[m].getName(), loadTemplate(methodTemplatesFiles[m])};
				methodTemplateList.add(template);
			}
		}
		if (fieldTemplatesFiles.length > 0)
		{
			System.out.println("Field template file(s):");
			for (int f=0; f<fieldTemplatesFiles.length; f++)
			{
				System.out.println("  " + fieldTemplatesFiles[f].getAbsolutePath());
				String template[] = {fieldTemplatesFiles[f].getName(), loadTemplate(fieldTemplatesFiles[f])};
				fieldTemplateList.add(template);
			}
		}
	}
	
	public void generate(File genDir)
	    throws TargetDirectoryException, IOException, AmqpTypeMappingException,
	    	AmqpTemplateException, IllegalAccessException, InvocationTargetException
	{
		prepareTargetDirectory(genDir, true);
		System.out.println("Generation directory: " + genDir.getAbsolutePath());
		this.genDir = genDir.getAbsolutePath();

		// Use all model-level templates
		for (int t = 0; t < classTemplateList.size(); t++)
		{
			processTemplate(modelTemplateList.get(t), null, null, null);
		}
		
		// Cycle through classes
		Set<String> ckeys = model.classMap.keySet();
		Iterator<String> citr = ckeys.iterator();
		while (citr.hasNext())
		{
			String className = citr.next();
			AmqpClass thisClass = model.classMap.get(className);
			
			// Use all class-level templates
			for (int c = 0; c < classTemplateList.size(); c++)
			{
				processTemplate(classTemplateList.get(c), thisClass, null, null);
			}
			
			// Cycle through all methods
			Set<String> mkeys = thisClass.methodMap.keySet();
			Iterator<String> mitr = mkeys.iterator();
			while (mitr.hasNext())
			{
				String methodName = mitr.next();
				AmqpMethod method = thisClass.methodMap.get(methodName);
				
				// Use all method-level templates
				for (int m = 0; m < methodTemplateList.size(); m++)
				{
					processTemplate(methodTemplateList.get(m), thisClass, method, null);
				}
				
				// Cycle through all fields
				Set<String> fkeys = method.fieldMap.keySet();
				Iterator<String> fitr = fkeys.iterator();
				while (fitr.hasNext())
				{
					String fieldName = fitr.next();
					AmqpField field = method.fieldMap.get(fieldName);
					
					// Use all field-level templates
					for (int f = 0; f < fieldTemplateList.size(); f++)
					{
						processTemplate(fieldTemplateList.get(f), thisClass, method, field);
					}
				}
			}
		}
	}

	abstract protected String prepareFilename(String filenameTemplate, AmqpClass thisClass, AmqpMethod method,
		AmqpField field);

	abstract protected String processToken(String token, AmqpClass thisClass, AmqpMethod method, AmqpField field)
	    throws AmqpTemplateException;
	
	abstract protected void processClassList(StringBuffer sb, int tokStart, int tokEnd, AmqpModel model)
        throws AmqpTemplateException;
	
	abstract protected void processMethodList(StringBuffer sb, int tokStart, int tokEnd, AmqpClass thisClass)
        throws AmqpTemplateException;
	
	abstract protected void processFieldList(StringBuffer sb, int listMarkerStartIndex, int listMarkerEndIndex,
		AmqpFieldMap fieldMap)
        throws AmqpTypeMappingException, AmqpTemplateException, IllegalAccessException,
        	InvocationTargetException;
	
	protected void processVersionList(StringBuffer sb, int tokStart, int tokEnd)
	    throws AmqpTypeMappingException
	{
		int lend = sb.indexOf(Utils.lineSeparator, tokStart) + 1; // Include cr at end of line
		String tline = sb.substring(tokEnd, lend); // Line excluding line marker, including cr
		sb.delete(tokStart, lend);
		AmqpVersion[] versionArray = new AmqpVersion[globalVersionSet.size()];
		globalVersionSet.toArray(versionArray);
		for (int i=0; i<versionArray.length; i++)
		{
			// Insert copy of target line
			StringBuffer isb = new StringBuffer(tline);
			replaceToken(isb, "${major}", String.valueOf(versionArray[i].getMajor()));
			replaceToken(isb, "${minor}", String.valueOf(versionArray[i].getMinor()));
			sb.insert(tokStart, isb.toString());
			tokStart += isb.length();
		}							
	}

	// TODO: This could be a little more elegant - overload this for the various combinations
	// of call instead of passing nulls.
	protected void processTemplate(String[] template, AmqpClass thisClass, AmqpMethod method, AmqpField field)
        throws IOException, AmqpTemplateException, AmqpTypeMappingException, IllegalAccessException,
        	InvocationTargetException
    {
		StringBuffer sb = new StringBuffer(template[1]);
		String filename = prepareFilename(getTemplateFileName(sb), thisClass, method, field);
		try { processAllLists(sb, thisClass, method); }
		catch (AmqpTemplateException e)
		{
			System.out.println("WARNING: " + template[0] + ": " + e.getMessage());
		}
		try { processAllTokens(sb, thisClass, method, field); }
		catch (AmqpTemplateException e)
		{
			System.out.println("WARNING: " + template[0] + ": " + e.getMessage());
		}
		writeTargetFile(sb, new File(genDir + Utils.fileSeparator + filename));
		generatedFileCounter ++;
    }
	
	// Helper functions common to all generators
	
	protected static void prepareTargetDirectory(File dir, boolean createFlag)
	    throws TargetDirectoryException
	{
		if (dir.exists())
		{
			if (!dir.isDirectory())
				throw new TargetDirectoryException("\"" + dir.getAbsolutePath() +
						"\" exists, but is not a directory.");
		}
		else if (createFlag) // Create dir
		{
			if(!dir.mkdirs())
				throw new TargetDirectoryException("Unable to create directory \"" +
						dir.getAbsolutePath() + "\".");
		}
		else
			throw new TargetDirectoryException("Directory \"" + dir.getAbsolutePath() +
					"\" not found.");
			
	}

	protected void processAllLists(StringBuffer sb, AmqpClass thisClass, AmqpMethod method)
	    throws AmqpTemplateException, AmqpTypeMappingException, IllegalAccessException,
	    	InvocationTargetException
	{
		int lstart = sb.indexOf("%{");
		while (lstart != -1)
		{
			int lend = sb.indexOf("}", lstart + 2);
			if (lend > 0)
			{
				String listToken = sb.substring(lstart + 2, lend);
				if (listToken.compareTo("VLIST") == 0)
				{
					processVersionList(sb, lstart, lend + 1);
				}
				else if (listToken.compareTo("CLIST") == 0)
				{
					processClassList(sb, lstart, lend + 1, model);
				}
				else if (listToken.compareTo("MLIST") == 0)
				{
					processMethodList(sb, lstart, lend + 1, thisClass);
				}
				else if (listToken.compareTo("FLIST") == 0)
				{
					// Pass the FieldMap from either a class or a method.
					// If this is called from a class-level template, we assume that the
					// class field list is required. In this case, method will be null.
					processFieldList(sb, lstart, lend + 1,
						(method == null ? thisClass.fieldMap : method.fieldMap));
				}
				else
				{
					throw new AmqpTemplateException("Unknown list token \"%{" + listToken +
						"}\" found in template at index " + lstart + ".");
				}
			}
			lstart = sb.indexOf("%{", lstart + 1);
		}		
	}
	
	protected void processAllTokens(StringBuffer sb, AmqpClass thisClass, AmqpMethod method, AmqpField field)
        throws AmqpTemplateException
	{
		int lstart = sb.indexOf("${");
		while (lstart != -1)
		{
			int lend = sb.indexOf("}", lstart + 2);
			if (lend > 0)
			{
				String token = sb.substring(lstart, lend + 1);
				replaceToken(sb, lstart, token, processToken(token, thisClass, method, field));
			}
			lstart = sb.indexOf("${", lstart);
		}			
	}
	
	protected static void writeTargetFile(StringBuffer sb, File f)
	    throws IOException
	{
		FileWriter fw = new FileWriter(f);
		fw.write(sb.toString().toCharArray());
		fw.flush();
		fw.close();
	}
	
	protected static String getTemplateFileName(StringBuffer sb)
	    throws AmqpTemplateException
	{
		if (sb.charAt(0) != '&')
			throw new AmqpTemplateException("No filename marker &{filename} found at start of template.");
		int cr = sb.indexOf(Utils.lineSeparator);
		if (cr < 0)
			throw new AmqpTemplateException("Bad template structure - unable to find first line.");
		String fileName = sb.substring(2, cr-1);
		sb.delete(0, cr + 1);
		return fileName;
	}

	protected static void replaceToken(StringBuffer sb, String token, String replacement)
	{
		replaceToken(sb, 0, token, replacement);
	}

	protected static void replaceToken(StringBuffer sb, int index, String token, String replacement)
	{
		if (replacement != null)
		{
			int start = sb.indexOf(token, index);
			int len = token.length();
			// Find first letter in token and determine if it is capitalized
			char firstTokenLetter = getFirstLetter(token);
			if (firstTokenLetter != 0 && Character.isUpperCase(firstTokenLetter))
				sb.replace(start, start+len, Utils.firstUpper(replacement));
			else
				sb.replace(start, start+len, replacement);
		}
	}
	
	private static char getFirstLetter(String str)
	{
		int len = str.length();
		int index = 0;
		char tokChar = str.charAt(index);
		while (!Character.isLetter(tokChar) && index<len-1)
			tokChar = str.charAt(++index);
		if (Character.isLetter(tokChar))
			return tokChar;
		return 0;
	}
	
	private static String loadTemplate(File f)
	    throws FileNotFoundException, IOException
	{
		StringBuffer sb = new StringBuffer();
		FileReader fr = new FileReader(f);
		LineNumberReader lnr = new LineNumberReader(fr);
		String line = lnr.readLine();
		while (line != null)
		{
			// Strip lines starting with '#' in template - treat these lines as template comments
			if (line.length() > 0 && line.charAt(0) != '#')
				sb.append(line + Utils.lineSeparator);
			else
				sb.append(Utils.lineSeparator);
			line = lnr.readLine();
		}
		lnr.close();
		fr.close();
		return sb.toString();
	}
}
