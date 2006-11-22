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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class Main
{
    private static final String defaultOutDir = ".." + Utils.fileSeparator + "gen";
    private static final String defaultCppTemplateDir = ".." + Utils.fileSeparator + "templ.cpp";
    private static final String defaultJavaTemplateDir = ".." + Utils.fileSeparator + "templ.java";
    
	private DocumentBuilder docBuilder;
	private AmqpVersionSet versionSet;
	private Generator generator;
    private AmqpConstantSet constants;
	private AmqpDomainMap domainMap;
	private AmqpModel model;
    
    private String outDir;
    private String tmplDir;
    private boolean javaFlag;
    private ArrayList<String> xmlFiles;
    private File[] modelTemplateFiles;
    private File[] classTemplateFiles;
    private File[] methodTemplateFiles;
    private File[] fieldTemplateFiles;
	
	public Main() throws ParserConfigurationException
	{
		docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		versionSet = new AmqpVersionSet();
        xmlFiles = new ArrayList<String>();
	}
	
	public void run(String[] args)
		throws 	IOException,
				SAXException,
				AmqpParseException,
				AmqpTypeMappingException,
				AmqpTemplateException,
				TargetDirectoryException,
				IllegalAccessException,
		    	InvocationTargetException
	{
		modelTemplateFiles = new File[]{};
		classTemplateFiles = new File[]{};
		methodTemplateFiles = new File[]{};
		fieldTemplateFiles = new File[]{};
        
        // 0. Initialize
        outDir = defaultOutDir;
        tmplDir = null;
        javaFlag = true;
        xmlFiles.clear();
        processArgs(args);
		if (javaFlag)
            prepareJava();
        else
            prepareCpp();

		if (modelTemplateFiles.length == 0 && classTemplateFiles.length == 0 &&
			methodTemplateFiles.length == 0 && fieldTemplateFiles.length == 0)
			System.err.println("  WARNING: No template files.");
		
		// 1. Suck in all the XML spec files provided on the command line
        analyzeXML();
		
		// 2. Load up all templates
		generator.initializeTemplates(modelTemplateFiles, classTemplateFiles,
			methodTemplateFiles, fieldTemplateFiles);
		
		// 3. Generate output
		generator.generate(new File(outDir));
		
		System.out.println("Files generated: " + generator.getNumberGeneratedFiles());
		System.out.println("Done.");
	}

    private void processArgs(String[] args)
    {
        // Crude but simple...
        for (int i=0; i<args.length; i++)
        {
            String arg = args[i];
            if (arg.charAt(0) == '-')
            {
                switch (arg.charAt(1))
                {
                    case 'c':
                    case 'C':
                        javaFlag = false;
                        break;
                    case 'j':
                    case 'J':
                        javaFlag = true;
                        break;
                    case 'o':
                    case 'O':
                        if (++i < args.length)
                        {
                            outDir = args[i];
                        }
                       break;
                    case 't':
                    case 'T':
                        if (++i < args.length)
                        {
                            tmplDir = args[i];
                        }
                        break;
                }
            }
            else
            {
                xmlFiles.add(args[i]);
            }
        }
    }
    
    private void prepareJava()
    {
        if (tmplDir == null)
            tmplDir = defaultJavaTemplateDir;
        System.out.println("Java generation mode.");
        generator = new JavaGenerator(versionSet);
        constants = new AmqpConstantSet(generator);
        domainMap = new AmqpDomainMap(generator);
        model = new AmqpModel(generator);       
        modelTemplateFiles = new File[]
        {
            new File(tmplDir + Utils.fileSeparator + "MethodRegistryClass.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AmqpConstantsClass.tmpl")
        };
        classTemplateFiles = new File[]
        {
            new File(tmplDir + Utils.fileSeparator + "PropertyContentHeaderClass.tmpl")
        };
        methodTemplateFiles = new File[]
        {
            new File(tmplDir + Utils.fileSeparator + "MethodBodyClass.tmpl")
        };
    }
    
    private void prepareCpp()
    {
        if (tmplDir == null)
            tmplDir = defaultCppTemplateDir;
        System.out.println("C++ generation mode.");
        generator = new CppGenerator(versionSet);
        constants = new AmqpConstantSet(generator);
        domainMap = new AmqpDomainMap(generator);
        model = new AmqpModel(generator);           
        modelTemplateFiles = new File[]
        {
            new File(tmplDir + Utils.fileSeparator + "AMQP_ServerOperations.h.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AMQP_ClientOperations.h.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AMQP_ServerProxy.h.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AMQP_ClientProxy.h.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AMQP_ServerProxy.cpp.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AMQP_ClientProxy.cpp.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AMQP_Constants.h.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AMQP_MethodVersionMap.h.tmpl"),
            new File(tmplDir + Utils.fileSeparator + "AMQP_MethodVersionMap.cpp.tmpl")
        };
        methodTemplateFiles = new File[]
        {
            new File(tmplDir + Utils.fileSeparator + "MethodBodyClass.h.tmpl")
        };        
    }

    private void analyzeXML()
        throws IOException, SAXException, AmqpParseException, AmqpTypeMappingException
    {
        System.out.println("XML files: " + xmlFiles);
        for (int i=0; i<xmlFiles.size(); i++)
        {
            String filename = xmlFiles.get(i);
            File f = new File(filename);
            if (f.exists())
            {
                // 1a. Initialize dom
                System.out.print("  \"" + filename + "\":");
                Document doc = docBuilder.parse(new File(filename));
                Node amqpNode = Utils.findChild(doc, Utils.ELEMENT_AMQP);
                
                // 1b. Extract version (major and minor) from the XML file
                int major = Utils.getNamedIntegerAttribute(amqpNode, Utils.ATTRIBUTE_MAJOR);
                int minor = Utils.getNamedIntegerAttribute(amqpNode, Utils.ATTRIBUTE_MINOR);
                AmqpVersion version = new AmqpVersion(major, minor);
                System.out.println(" Found version " + version.toString() + ".");
                versionSet.add(version);
                
                // 1c. Extract domains
                constants.addFromNode(amqpNode, 0, version);
                
                // 1d. Extract domains
                domainMap.addFromNode(amqpNode, 0, version);
                
                // 1e. Extract class/method/field heirarchy
                model.addFromNode(amqpNode, 0, version);
            }
            else
                System.err.println("ERROR: AMQP XML file \"" + filename + "\" not found.");
        }
// *** DEBUG INFO ***  Uncomment bits from this block to see lots of stuff....
//      System.out.println();
//      System.out.println("*** Debug output ***");
//      System.out.println();
//      versionSet.print(System.out, 0, 2);
//      System.out.println();
//      constants.print(System.out, 0, 2);
//      System.out.println();
//      domainMap.print(System.out, 0, 2);
//      System.out.println();
//      model.print(System.out, 0, 2);
//      System.out.println();
//      System.out.println("*** End debug output ***");
//      System.out.println();        
    }
    
	public static void main(String[] args)
	{
		if (args.length < 2)
			usage();
		try { new Main().run(args); }
		catch (IOException e) { e.printStackTrace(); }
		catch (ParserConfigurationException e) { e.printStackTrace(); }
		catch (SAXException e) { e.printStackTrace(); }
		catch (AmqpParseException e) { e.printStackTrace(); }
		catch (AmqpTypeMappingException e) { e.printStackTrace(); }
		catch (AmqpTemplateException e) { e.printStackTrace(); }
		catch (TargetDirectoryException e) { e.printStackTrace(); }
		catch (IllegalAccessException e) { e.printStackTrace(); }
		catch (InvocationTargetException e) { e.printStackTrace(); }
	}

	public static void usage()
	{
		System.out.println("AMQP XML generator v.0.0");
		System.out.println("Usage: Main -c|-j [-o outDir] [-t tmplDir] XMLfile [XMLfile ...]");
		System.out.println("       where -c:         Generate C++.");
		System.out.println("             -j:         Generate Java.");
        System.out.println("             -o outDir:  Use outDir as the output dir (default=\"" + defaultOutDir + "\").");
        System.out.println("             -t tmplDir: Find templates in tmplDir.");
        System.out.println("                         Defaults: \"" + defaultCppTemplateDir + "\" for C++;");
        System.out.println("                                   \"" + defaultJavaTemplateDir + "\" for java.");
		System.out.println("             XMLfile is a space-separated list of AMQP XML files to be parsed.");
		System.exit(0);
	}
	
	public static String ListTemplateList(File[] list)
	{
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<list.length; i++)
		{
			if (i != 0)
				sb.append(", ");
			sb.append(list[i].getName());
		}
		return sb.toString();
	}
}
