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
package org.apache.qpid.server.model.validation;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes(ManagedAnnotationValidator.MANAGED_ANNOTATION_CLASS_NAME)
public class ManagedAnnotationValidator extends AbstractProcessor
{
    public static final String MANAGED_ANNOTATION_CLASS_NAME = "org.apache.qpid.server.model.ManagedAnnotation";


    @Override
    public SourceVersion getSupportedSourceVersion()
    {
        return SourceVersion.latest();
    }


    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv)
    {
        Elements elementUtils = processingEnv.getElementUtils();
        Types typeUtils = processingEnv.getTypeUtils();

        TypeElement annotationElement = elementUtils.getTypeElement(MANAGED_ANNOTATION_CLASS_NAME);

        String className = "org.apache.qpid.server.model.ManagedInterface";
        TypeMirror configuredObjectType = getErasure(className);

        for (Element e : roundEnv.getElementsAnnotatedWith(annotationElement))
        {
            if (e.getKind() != ElementKind.INTERFACE)
            {
                processingEnv.getMessager()
                        .printMessage(Diagnostic.Kind.ERROR,
                                      "@"
                                      + annotationElement.getSimpleName()
                                      + " can only be applied to an interface",
                                      e
                                     );
            }


            if(!typeUtils.isAssignable(typeUtils.erasure(e.asType()), configuredObjectType))
            {

                processingEnv.getMessager()
                        .printMessage(Diagnostic.Kind.ERROR,
                                      "@"
                                      + annotationElement.getSimpleName()
                                      + " can only be applied to an interface which extends " + className,
                                      e
                                     );
            }
        }

        return false;
    }


    private TypeMirror getErasure(final String className)
    {
        final Types typeUtils = processingEnv.getTypeUtils();
        final Elements elementUtils = processingEnv.getElementUtils();
        return typeUtils.erasure(elementUtils.getTypeElement(className).asType());
    }
}
