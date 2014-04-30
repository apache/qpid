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
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.apache.qpid.server.License;

public class ConfiguredObjectFactoryGenerator extends AbstractProcessor
{
    @Override
    public SourceVersion getSupportedSourceVersion()
    {
        return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes()
    {
        return Collections.singleton(ManagedObjectFactoryConstructor.class.getName());
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv)
    {

        if(roundEnv.processingOver())
        {

            return true;
        }

        Filer filer = processingEnv.getFiler();

        try
        {

            for (Element e : roundEnv.getElementsAnnotatedWith(ManagedObjectFactoryConstructor.class))
            {
                if(e.getKind() == ElementKind.CONSTRUCTOR)
                {
                    ExecutableElement constructorElement = (ExecutableElement) e;
                    String factoryName = generateObjectFactory(filer, constructorElement);
                }
            }

        }
        catch (Exception e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error: " + e.getLocalizedMessage());
        }

        return true;
    }

    private String generateObjectFactory(final Filer filer, final ExecutableElement constructorElement)
    {
        TypeElement classElement = (TypeElement) constructorElement.getEnclosingElement();
        String factoryName = classElement.getQualifiedName().toString() + "Factory";
        String factorySimpleName = classElement.getSimpleName().toString() + "Factory";
        String objectSimpleName = classElement.getSimpleName().toString();
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generating factory file for " + classElement.getQualifiedName().toString());

        PackageElement packageElement = (PackageElement) classElement.getEnclosingElement();

        try
        {
            JavaFileObject factoryFile = filer.createSourceFile(factoryName);
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(factoryFile.openOutputStream(), "UTF-8"));
            pw.println("/*");
            for(String headerLine : License.LICENSE)
            {
                pw.println(" *" + headerLine);
            }
            pw.println(" */");
            pw.println();
            pw.print("package ");
            pw.print(packageElement.getQualifiedName());
            pw.println(";");
            pw.println();

            pw.println("import java.util.Map;");
            pw.println();
            pw.println("import org.apache.qpid.server.model.AbstractConfiguredObjectTypeFactory;");
            pw.println("import org.apache.qpid.server.model.ConfiguredObject;");
            pw.println("import org.apache.qpid.server.plugin.PluggableService;");
            pw.println();
            pw.println("@PluggableService");
            pw.println("public final class " + factorySimpleName + " extends AbstractConfiguredObjectTypeFactory<"+ objectSimpleName +">");
            pw.println("{");
            pw.println("    public " + factorySimpleName + "()");
            pw.println("    {");
            pw.println("        super(" + objectSimpleName + ".class);");
            pw.println("    }");
            pw.println();
            pw.println("    @Override");
            pw.println("    protected "+objectSimpleName+" createInstance(final Map<String, Object> attributes, final ConfiguredObject<?>... parents)");
            pw.println("    {");
            pw.print("        return new "+objectSimpleName+"(attributes");
            boolean first = true;
            for(VariableElement param : constructorElement.getParameters())
            {
                if(first)
                {
                    first = false;
                }
                else
                {
                    TypeMirror erasureType = processingEnv.getTypeUtils().erasure(param.asType());
                    pw.print(", getParent("+erasureType.toString()+".class,parents)");
                }
            }
            pw.println(");");
            pw.println("    }");

            pw.println("}");

            pw.close();
        }
        catch (IOException e)
        {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                                     "Failed to write factory file: "
                                                     + factoryName
                                                     + " - "
                                                     + e.getLocalizedMessage());
        }

        return factoryName;
    }

}
