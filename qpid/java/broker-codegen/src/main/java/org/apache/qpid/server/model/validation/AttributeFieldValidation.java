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

import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes(AttributeFieldValidation.MANAGED_ATTRIBUTE_FIELD_CLASS_NAME)
public class AttributeFieldValidation extends AbstractProcessor
{
    public static final String MANAGED_ATTRIBUTE_FIELD_CLASS_NAME = "org.apache.qpid.server.model.ManagedAttributeField";


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

        TypeElement annotationElement = elementUtils.getTypeElement(MANAGED_ATTRIBUTE_FIELD_CLASS_NAME);
        for (Element e : roundEnv.getElementsAnnotatedWith(annotationElement))
        {
            for(AnnotationMirror am : e.getAnnotationMirrors())
            {
                if(typeUtils.isSameType(am.getAnnotationType(), annotationElement.asType()))
                {
                    for(Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : am.getElementValues().entrySet())
                    {
                        String elementName = entry.getKey().getSimpleName().toString();
                        if(elementName.equals("beforeSet") || elementName.equals("afterSet"))
                        {
                            String methodName = entry.getValue().getValue().toString();
                            if(!"".equals(methodName))
                            {
                                TypeElement parent = (TypeElement) e.getEnclosingElement();
                                if(!containsMethod(parent, methodName))
                                {
                                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                                                             "Could not find method '"
                                                                             + methodName
                                                                             + "' which is defined as the "
                                                                             + elementName
                                                                             + " action", e);

                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean containsMethod(final TypeElement parent, final String methodName)
    {
        for(Element element : parent.getEnclosedElements())
        {
            if(element.getKind().equals(ElementKind.METHOD)
               && element.getSimpleName().toString().equals(methodName)
               && ((ExecutableElement)element).getParameters().isEmpty())
            {
                return true;
            }
        }
        return false;
    }
}
