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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.apache.qpid.server.License;

public class ConfiguredObjectRegistrationGenerator extends AbstractProcessor
{

    public static final String MANAGED_OBJECT_CANONICAL_NAME = "org.apache.qpid.server.model.ManagedObject";

    private Map<String, Set<String>> _managedObjectClasses = new HashMap<>();

    private Map<String, String> _typeMap = new HashMap<>();
    private Map<String, String> _categoryMap = new HashMap<>();

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
                    String className = e.getSimpleName().toString();
                    for(AnnotationMirror a : e.getAnnotationMirrors())
                    {
                        if(a.getAnnotationType().asElement().equals(annotationElement))
                        {
                            for(Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : a.getElementValues().entrySet())
                            {
                                if(entry.getKey().getSimpleName().toString().equals("type"))
                                {
                                    _typeMap.put(packageName + "." + className, (String) entry.getValue().getValue());
                                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "looking for " + packageName + "." + className);
                                    _categoryMap.put(packageName + "." + className, getCategory((TypeElement)e));
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    Set<String> classNames = _managedObjectClasses.get(packageName);
                    if (classNames == null)
                    {
                        classNames = new HashSet<>();
                        _managedObjectClasses.put(packageName, classNames);
                    }
                    classNames.add(className);
                }
            }
            for (Map.Entry<String, Set<String>> entry : _managedObjectClasses.entrySet())
            {
                generateRegistrationFile(entry.getKey(), entry.getValue());
            }
            _managedObjectClasses.clear();
            _typeMap.clear();
            _categoryMap.clear();
        }
        catch (Exception e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error: " + e.getLocalizedMessage());
        }

        return false;
    }

    private String getCategory(final TypeElement e)
    {
        Elements elementUtils = processingEnv.getElementUtils();
        TypeElement annotationElement = elementUtils.getTypeElement(MANAGED_OBJECT_CANONICAL_NAME);
        String category = null;
        List<? extends AnnotationMirror> annotationMirrors = e.getAnnotationMirrors();
        if(annotationMirrors != null)
        {
            for (AnnotationMirror a : annotationMirrors)
            {
                if (a.getAnnotationType().asElement().equals(annotationElement))
                {
                    category = e.getSimpleName().toString().toLowerCase();

                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : a.getElementValues()
                            .entrySet())
                    {
                        if (entry.getKey().getSimpleName().toString().equals("category"))
                        {
                            if (!Boolean.TRUE.equals(entry.getValue().getValue()))
                            {
                                category = null;
                            }

                            break;
                        }
                    }
                    break;
                }
            }
        }

        if (category == null)
        {
            for (TypeMirror interfaceMirror : e.getInterfaces())
            {
                category = getCategory((TypeElement) processingEnv.getTypeUtils().asElement(interfaceMirror));
                if (category != null)
                {
                    break;
                }
            }
        }

        if (category == null && e.getSuperclass() != null)
        {
            TypeElement parent = (TypeElement) processingEnv.getTypeUtils().asElement(e.getSuperclass());
            if(parent != null)
            {
                category = getCategory(parent);
            }
        }

        return category;

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
                String qualifiedImplementationName = packageName + "." + implementationName;
                if(_typeMap.get(qualifiedImplementationName) != null && _categoryMap.get(qualifiedImplementationName) != null)
                {
                    pw.println("        if(!Boolean.getBoolean(\"qpid.type.disabled:"
                               +_categoryMap.get(qualifiedImplementationName)
                               +"."+_typeMap.get(qualifiedImplementationName)+"\"))");
                    pw.println("        {");
                    pw.println("             implementations.add("+implementationName+".class);");
                    pw.println("        }");

                }
                else
                {
                    pw.println("        implementations.add(" + implementationName + ".class);");
                }
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
