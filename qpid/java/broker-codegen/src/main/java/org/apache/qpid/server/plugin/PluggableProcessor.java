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
package org.apache.qpid.server.plugin;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.apache.qpid.server.License;

public class PluggableProcessor extends AbstractProcessor
{
    private Map<String, Set<String>> factoryImplementations = new HashMap<>();


    @Override
    public SourceVersion getSupportedSourceVersion()
    {
        return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes()
    {
        return Collections.singleton(PluggableService.class.getName());
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv)
    {
        if(roundEnv.processingOver())
        {
            generateServiceFiles(processingEnv.getFiler());

            return true;
        }
        try
        {

            for (Element e : roundEnv.getElementsAnnotatedWith(PluggableService.class))
            {

                if (e.getKind() == ElementKind.CLASS)
                {
                    TypeElement classElement = (TypeElement) e;
                    Set<String> pluggableTypes = getPluggableTypes(classElement);
                    for(String pluggableType : pluggableTypes)
                    {
                        Set<String> existingFactories = factoryImplementations.get(pluggableType);
                        if(existingFactories == null)
                        {
                            existingFactories = new HashSet<>();
                            factoryImplementations.put(pluggableType, existingFactories);
                        }
                        existingFactories.add(classElement.getQualifiedName().toString());
                    }
                }
            }

        }
        catch (Exception e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error: " + e.getLocalizedMessage());
        }

        return true;
    }

    private Set<String> getPluggableTypes(final TypeElement classElement)
    {

        final Set<String> types = new HashSet<>();

        List<? extends TypeMirror> interfaces = classElement.getInterfaces();
        for(TypeMirror typeMirror : interfaces)
        {
            TypeElement interfaceElt = (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror);
            if(interfaceElt.getQualifiedName().toString().equals("org.apache.qpid.server.plugin.Pluggable"))
            {
                types.add(classElement.getQualifiedName().toString());
            }
            else
            {
                types.addAll(getPluggableTypes(interfaceElt));
            }

        }
        TypeMirror superClass = classElement.getSuperclass();
        if(!(superClass instanceof NoType))
        {
            types.addAll(getPluggableTypes((TypeElement) processingEnv.getTypeUtils().asElement(superClass)));
        }

        return types;
    }

    private void generateServiceFiles(Filer filer)
    {
        for(String serviceName : factoryImplementations.keySet())
        {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.NOTE, "Generating service file for " + serviceName);

            String relativeName = "META-INF/services/" + serviceName;
            loadExistingServicesFile(filer, serviceName);
            try
            {
                FileObject serviceFile = filer.createResource(StandardLocation.CLASS_OUTPUT, "", relativeName);
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(serviceFile.openOutputStream(), "UTF-8"));

                for (String headerLine : License.LICENSE)
                {
                    pw.println("#" + headerLine);
                }
                pw.println("#");
                pw.println("# Note: Parts of this file are auto-generated from annotations.");
                pw.println("#");
                for (String implementation : factoryImplementations.get(serviceName))
                {
                    pw.println(implementation);
                }

                pw.close();
            }
            catch (IOException e)
            {
                processingEnv.getMessager()
                        .printMessage(Diagnostic.Kind.ERROR,
                                      "Failed to write services file: "
                                      + relativeName
                                      + " - "
                                      + e.getLocalizedMessage()
                                     );
            }
        }
    }

    private String loadExistingServicesFile(final Filer filer, String serviceName)
    {
        String relativeName = "META-INF/services/" + serviceName;
        try
        {

            FileObject existingFile = filer.getResource(StandardLocation.CLASS_OUTPUT, "", relativeName);
            BufferedReader r = new BufferedReader(new InputStreamReader(existingFile.openInputStream(), "UTF-8"));
            String line;
            while((line=r.readLine())!=null)
            {
                if(!line.matches(" *#"))
                {
                    factoryImplementations.get(serviceName).add(line);
                }
            }
            r.close();
        }
        catch (FileNotFoundException e)
        {
            // no existing file (ignore)
        }
        catch (IOException e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                                     "Error loading existing services file: " + relativeName
                                                     + " - " + e.getLocalizedMessage());
        }
        return relativeName;
    }

}
