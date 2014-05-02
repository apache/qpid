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
package org.apache.qpid.server.model;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.apache.qpid.server.License;

public class ConfiguredObjectRegistrationGenerator extends AbstractProcessor
{

    public static final String MANAGED_OBJECT_CANONICAL_NAME = "org.apache.qpid.server.model.ManagedObject";

    private Map<String, Set<String>> _managedObjectClasses = new HashMap<>();

    @Override
    public SourceVersion getSupportedSourceVersion()
    {
        return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes()
    {
        return Collections.singleton(MANAGED_OBJECT_CANONICAL_NAME);
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv)
    {

        Elements elementUtils = processingEnv.getElementUtils();
        TypeElement annotationElement = elementUtils.getTypeElement(MANAGED_OBJECT_CANONICAL_NAME);


        try
        {

            for (Element e : roundEnv.getElementsAnnotatedWith(annotationElement))
            {
                if (e.getKind().equals(ElementKind.INTERFACE) || e.getKind().equals(ElementKind.CLASS))
                {
                    PackageElement packageElement = elementUtils.getPackageOf(e);
                    String packageName = packageElement.getQualifiedName().toString();

                    Set<String> classNames = _managedObjectClasses.get(packageName);
                    if (classNames == null)
                    {
                        classNames = new HashSet<>();
                        _managedObjectClasses.put(packageName, classNames);
                    }
                    classNames.add(e.getSimpleName().toString());
                }
            }
            for (Map.Entry<String, Set<String>> entry : _managedObjectClasses.entrySet())
            {
                generateRegistrationFile(entry.getKey(), entry.getValue());
            }
            _managedObjectClasses.clear();
        }
        catch (Exception e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error: " + e.getLocalizedMessage());
        }

        return false;
    }

    private void generateRegistrationFile(final String packageName, final Set<String> classNames)
    {
        final String className = "ConfiguredObjectRegistrationImpl";
        final String qualifiedClassName = packageName + "." + className;

        try
        {


            JavaFileObject factoryFile = processingEnv.getFiler().createSourceFile(qualifiedClassName);

            PrintWriter pw = new PrintWriter(new OutputStreamWriter(factoryFile.openOutputStream(), "UTF-8"));
            pw.println("/*");
            for (String headerLine : License.LICENSE)
            {
                pw.println(" *" + headerLine);
            }
            pw.println(" */");
            pw.println();
            pw.print("package ");
            pw.print(packageName);
            pw.println(";");
            pw.println();

            pw.println("import java.util.Collections;");
            pw.println("import java.util.HashSet;");
            pw.println("import java.util.Set;");
            pw.println();
            pw.println("import org.apache.qpid.server.model.ConfiguredObject;");
            pw.println("import org.apache.qpid.server.plugin.ConfiguredObjectRegistration;");
            pw.println("import org.apache.qpid.server.plugin.PluggableService;");
            pw.println();
            pw.println("@PluggableService");
            pw.println("public class " + className + " implements ConfiguredObjectRegistration");
            pw.println("{");
            pw.println("    private final Set<Class<? extends ConfiguredObject>> _implementations;");
            pw.println();
            pw.println("    public " + className + "()");
            pw.println("    {");
            pw.println("        Set<Class<? extends ConfiguredObject>> implementations = new HashSet<>();");
            for(String implementationName : classNames)
            {
                pw.println("        implementations.add("+implementationName+".class);");
            }
            pw.println("        _implementations = Collections.unmodifiableSet(implementations);");
            pw.println("    }");
            pw.println();
            pw.println("    public String getType()");
            pw.println("    {");
            pw.println("        return \""+packageName+"\";");
            pw.println("    }");
            pw.println();
            pw.println("    public Set<Class<? extends ConfiguredObject>> getConfiguredObjectClasses()");
            pw.println("    {");
            pw.println("        return _implementations;");
            pw.println("    }");
            pw.println();


            pw.println("}");

            pw.close();
        }
        catch (IOException e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                                     "Failed to write file: "
                                                     + qualifiedClassName
                                                     + " - "
                                                     + e.getLocalizedMessage()
                                                    );
        }
    }

}
