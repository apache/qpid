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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;


@SupportedAnnotationTypes({AttributeAnnotationValidator.MANAGED_ATTRIBUTE_CLASS_NAME,
                           AttributeAnnotationValidator.DERIVED_ATTRIBUTE_CLASS_NAME,
                           AttributeAnnotationValidator.MANAGED_STATISTIC_CLASS_NAME})
public class AttributeAnnotationValidator extends AbstractProcessor
{

    public static final String MANAGED_ATTRIBUTE_CLASS_NAME = "org.apache.qpid.server.model.ManagedAttribute";
    public static final String DERIVED_ATTRIBUTE_CLASS_NAME = "org.apache.qpid.server.model.DerivedAttribute";

    public static final String MANAGED_STATISTIC_CLASS_NAME = "org.apache.qpid.server.model.ManagedStatistic";



    private static final Set<TypeKind> VALID_PRIMITIVE_TYPES = new HashSet<>(Arrays.asList(TypeKind.BOOLEAN,
                                                                                           TypeKind.BYTE,
                                                                                           TypeKind.CHAR,
                                                                                           TypeKind.DOUBLE,
                                                                                           TypeKind.FLOAT,
                                                                                           TypeKind.INT,
                                                                                           TypeKind.LONG,
                                                                                           TypeKind.SHORT));

    @Override
    public SourceVersion getSupportedSourceVersion()
    {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv)
    {

        processAttributes(roundEnv, MANAGED_ATTRIBUTE_CLASS_NAME);
        processAttributes(roundEnv, DERIVED_ATTRIBUTE_CLASS_NAME);

        processStatistics(roundEnv, MANAGED_STATISTIC_CLASS_NAME);

        return false;
    }

    public void processAttributes(final RoundEnvironment roundEnv,
                                  String elementName)
    {

        Elements elementUtils = processingEnv.getElementUtils();
        TypeElement annotationElement = elementUtils.getTypeElement(elementName);

        for (Element e : roundEnv.getElementsAnnotatedWith(annotationElement))
        {
            checkAnnotationIsOnMethodInInterface(annotationElement, e);

            ExecutableElement methodElement = (ExecutableElement) e;

            checkInterfaceExtendsConfiguredObject(annotationElement, methodElement);
            checkMethodTakesNoArgs(annotationElement, methodElement);
            checkMethodName(annotationElement, methodElement);
            checkMethodReturnType(annotationElement, methodElement);

            checkTypeAgreesWithName(annotationElement, methodElement);
        }
    }

    public void processStatistics(final RoundEnvironment roundEnv,
                                  String elementName)
    {

        Elements elementUtils = processingEnv.getElementUtils();
        TypeElement annotationElement = elementUtils.getTypeElement(elementName);

        for (Element e : roundEnv.getElementsAnnotatedWith(annotationElement))
        {
            checkAnnotationIsOnMethodInInterface(annotationElement, e);

            ExecutableElement methodElement = (ExecutableElement) e;

            checkInterfaceExtendsConfiguredObject(annotationElement, methodElement);
            checkMethodTakesNoArgs(annotationElement, methodElement);
            checkMethodName(annotationElement, methodElement);
            checkTypeAgreesWithName(annotationElement, methodElement);
            checkMethodReturnTypeIsNumber(annotationElement, methodElement);

        }
    }

    private void checkMethodReturnTypeIsNumber(final TypeElement annotationElement,
                                               final ExecutableElement methodElement)
    {
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();

        TypeMirror numberType = elementUtils.getTypeElement("java.lang.Number").asType();
        if(!typeUtils.isAssignable(methodElement.getReturnType(),numberType))
        {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                                  "@"
                                  + annotationElement.getSimpleName()
                                  + " return type does not extend Number: "
                                  + methodElement.getReturnType().toString(),
                                  methodElement
                                 );
        }
    }

    public void checkTypeAgreesWithName(final TypeElement annotationElement, final ExecutableElement methodElement)
    {
        Types typeUtils = processingEnv.getTypeUtils();

        String methodName = methodElement.getSimpleName().toString();

        if((methodName.startsWith("is") || methodName.startsWith("has"))
            && !(methodElement.getReturnType().getKind() == TypeKind.BOOLEAN
                 || typeUtils.isSameType(typeUtils.boxedClass(typeUtils.getPrimitiveType(TypeKind.BOOLEAN)).asType(), methodElement.getReturnType())))
        {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                                  "@"
                                  + annotationElement.getSimpleName()
                                  + " return type is not boolean or Boolean: "
                                  + methodElement.getReturnType().toString(),
                                  methodElement
                                 );
        }
    }

    public void checkMethodReturnType(final TypeElement annotationElement, final ExecutableElement methodElement)
    {
        if (!isValidType(methodElement.getReturnType()))
        {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                                  "@"
                                  + annotationElement.getSimpleName()
                                  + " cannot be applied to methods with return type "
                                  + methodElement.getReturnType().toString(),
                                  methodElement
                                 );
        }
    }

    public void checkMethodName(final TypeElement annotationElement, final ExecutableElement methodElement)
    {
        String methodName = methodElement.getSimpleName().toString();

        if (methodName.length() < 3
            || (methodName.length() < 4 && !methodName.startsWith("is"))
            || !(methodName.startsWith("is") || methodName.startsWith("get") || methodName.startsWith("has")))
        {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                                  "@"
                                  + annotationElement.getSimpleName()
                                  + " can only be applied to methods which of the form getXXX(), isXXX() or hasXXX()",
                                  methodElement
                                 );
        }
    }

    public void checkMethodTakesNoArgs(final TypeElement annotationElement, final ExecutableElement methodElement)
    {
        if (!methodElement.getParameters().isEmpty())
        {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                                  "@"
                                  + annotationElement.getSimpleName()
                                  + " can only be applied to methods which take no parameters",
                                  methodElement
                                 );
        }
    }

    public void checkInterfaceExtendsConfiguredObject(final TypeElement annotationElement, final Element e)
    {
        Types typeUtils = processingEnv.getTypeUtils();
        TypeMirror configuredObjectType = getErasure("org.apache.qpid.server.model.ConfiguredObject");
        TypeElement parent = (TypeElement) e.getEnclosingElement();


        if (!typeUtils.isAssignable(typeUtils.erasure(parent.asType()), configuredObjectType))
        {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                                  "@"
                                  + annotationElement.getSimpleName()
                                  + " can only be applied to methods within an interface which extends "
                                  + configuredObjectType.toString()
                                  + " which does not apply to "
                                  + parent.asType().toString(),
                                  e);
        }
    }

    public void checkAnnotationIsOnMethodInInterface(final TypeElement annotationElement, final Element e)
    {
        if (e.getKind() != ElementKind.METHOD || e.getEnclosingElement().getKind() != ElementKind.INTERFACE)
        {
            processingEnv.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                                  "@"
                                  + annotationElement.getSimpleName()
                                  + " can only be applied to methods within an interface",
                                  e
                                 );
        }
    }

    private boolean isValidType(final TypeMirror type)
    {
        Types typeUtils = processingEnv.getTypeUtils();
        Elements elementUtils = processingEnv.getElementUtils();
        Element typeElement = typeUtils.asElement(type);

        if (VALID_PRIMITIVE_TYPES.contains(type.getKind()))
        {
            return true;
        }
        for(TypeKind primitive : VALID_PRIMITIVE_TYPES)
        {
            if(typeUtils.isSameType(type, typeUtils.boxedClass(typeUtils.getPrimitiveType(primitive)).asType()))
            {
                return true;
            }
        }
        if(typeElement.getKind()==ElementKind.ENUM)
        {
            return true;
        }

        String className = "org.apache.qpid.server.model.ConfiguredObject";
        TypeMirror configuredObjectType = getErasure(className);

        if(typeUtils.isAssignable(typeUtils.erasure(type), configuredObjectType))
        {
            return true;
        }


        if(typeUtils.isSameType(type,elementUtils.getTypeElement("java.lang.Object").asType()))
        {
            return true;
        }


        if(typeUtils.isSameType(type, elementUtils.getTypeElement("java.lang.String").asType()))
        {
            return true;
        }


        if(typeUtils.isSameType(type,elementUtils.getTypeElement("java.util.UUID").asType()))
        {
            return true;
        }

        TypeMirror erasedType = typeUtils.erasure(type);
        if(typeUtils.isSameType(erasedType, getErasure("java.util.List"))
                || typeUtils.isSameType(erasedType, getErasure("java.util.Set"))
                || typeUtils.isSameType(erasedType, getErasure("java.util.Collection")))
        {


            for(TypeMirror paramType : ((DeclaredType)type).getTypeArguments())
            {

                if(!isValidType(paramType))
                {
                    return false;
                }
            }
            return true;
        }

        if(typeUtils.isSameType(erasedType, getErasure("java.util.Map")))
        {
            List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
            if (args.size() != 2)
            {
                throw new IllegalArgumentException("Map types " + type + " must have exactly two type arguments");
            }
            return isValidType(args.get(0)) && (isValidType(args.get(1)) || typeUtils.isSameType(args.get(1), getErasure("java.lang.Object")));
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
