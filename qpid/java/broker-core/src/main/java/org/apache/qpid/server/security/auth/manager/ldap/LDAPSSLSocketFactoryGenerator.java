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
package org.apache.qpid.server.security.auth.manager.ldap;

import static org.apache.bcel.Constants.ACC_PRIVATE;
import static org.apache.bcel.Constants.ACC_PUBLIC;
import static org.apache.bcel.Constants.ACC_STATIC;
import static org.apache.bcel.Constants.ACC_SUPER;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldGen;
import org.apache.bcel.generic.InstructionConstants;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.Type;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

/**
 * This class provides a single method, {@link #createSubClass(String, SSLSocketFactory)}.  This creates a
 * sub-class of {@link AbstractLDAPSSLSocketFactory} and associates it with the {@link SSLSocketFactory} instance..
 * <p>
 * The sub-classes are <b>generated dynamically</b>.
 * </p>
 * <p>This approach is required in order to overcome a limitation in the javax.naming.directory API.  It offers
 * {@link SSLSocketFactory} customization only at the class level only (via the <code>java.naming.ldap.factory.socket</code>
 * directory context environment parameter). For this reason, a mechanism that can produce distinct
 * {@link AbstractLDAPSSLSocketFactory} classes each associated with a different SSLSocketFactory instance is required.
 * </p>
 * @see <a href="http://docs.oracle.com/javase/jndi/tutorial/ldap/security/ssl.html">Java LDAP SSL and Custom Sockets</a>
 */
public class LDAPSSLSocketFactoryGenerator
{
    /**
     * The name of field used to hold the delegate {@link SSLSocketFactory}. A field with
     * this name is created on each generated sub-class.
     */
    static final String SSL_SOCKET_FACTORY_FIELD = "_sslSocketFactory";

    /** Target package names used for the subclass - needs to exist */
    static final String TARGET_PACKAGE_NAME = LDAPSSLSocketFactoryGenerator.class.getPackage().getName();

    public static Class<? extends AbstractLDAPSSLSocketFactory> createSubClass(String simpleName, final SSLSocketFactory sslSocketFactory)
    {
        final String fqcn = TARGET_PACKAGE_NAME + "." + simpleName;
        final byte[] classBytes = createSubClassByteCode(fqcn);

        try
        {
            final ClassLoader classLoader = new LDAPSSLSocketFactoryAwareDelegatingClassloader(fqcn, classBytes, sslSocketFactory);
            Class<? extends AbstractLDAPSSLSocketFactory> clazz = (Class<? extends AbstractLDAPSSLSocketFactory>) classLoader.loadClass(fqcn);
            return clazz;
        }
        catch (ClassNotFoundException cnfe)
        {
            throw new IllegalArgumentException("Could not resolve dynamically generated class " + fqcn, cnfe);
        }
    }

    /**
     * Creates the LDAPSocketFactoryImpl class (subclass of {@link AbstractLDAPSSLSocketFactory}.
     * A static method #getDefaulta, a static field _sslContent and no-arg constructor are added
     * to the class.
     *
     * @param className
     *
     * @return byte code
     */
    private static byte[] createSubClassByteCode(final String className)
    {
        ClassGen classGen = new ClassGen(className,
                AbstractLDAPSSLSocketFactory.class.getName(),
                "<generated>",
                ACC_PUBLIC | ACC_SUPER,
                null);
        ConstantPoolGen constantPoolGen = classGen.getConstantPool();
        InstructionFactory factory = new InstructionFactory(classGen);

        createSslContextStaticField(classGen, constantPoolGen);
        createGetDefaultStaticMethod(classGen, constantPoolGen, factory);

        classGen.addEmptyConstructor(Constants.ACC_PROTECTED);

        JavaClass javaClass = classGen.getJavaClass();
        ByteArrayOutputStream out = null;
        try
        {
            out = new ByteArrayOutputStream();
            javaClass.dump(out);
            return out.toByteArray();
        }
        catch (IOException ioex)
        {
            throw new IllegalStateException("Could not write to a ByteArrayOutputStream - should not happen", ioex);
        }
        finally
        {
            closeSafely(out);
        }
    }

    /**
     * Creates a static field _sslContext of type {@link SSLSocketFactory}.
     *
     * @param classGen
     * @param constantPoolGen
     */
    private static void createSslContextStaticField(ClassGen classGen, ConstantPoolGen constantPoolGen)
    {
        FieldGen fieldGen = new FieldGen(ACC_PRIVATE | ACC_STATIC,
                                         Type.getType(SSLSocketFactory.class),
                                         SSL_SOCKET_FACTORY_FIELD,
                                         constantPoolGen);
        classGen.addField(fieldGen.getField());
    }

    /**
     * Create a static method 'getDefault' returning {@link SocketFactory}
     * that creates a new instance of the sub-class and calls its no-argument
     * constructor, the newly created is returned to the caller.
     *
     * @param classGen
     * @param constantPoolGen
     * @param instructionFactory
     */
    private static void createGetDefaultStaticMethod(ClassGen classGen,
            ConstantPoolGen constantPoolGen, InstructionFactory instructionFactory)
    {
        InstructionList il = new InstructionList();

        String methodName = "getDefault";
        MethodGen mg = new MethodGen(ACC_STATIC | ACC_PUBLIC, // access flags
                            Type.getType(SSLSocketFactory.class),  // return type
                            new Type[0],   // argument types - no args
                            new String[0], // arg names - no args
                            methodName,
                            classGen.getClassName(),    // method, class
                            il,
                            constantPoolGen);

        il.append(instructionFactory.createNew(classGen.getClassName()));
        il.append(InstructionConstants.DUP);

        il.append(instructionFactory.createInvoke(classGen.getClassName(), "<init>", Type.VOID,
                                       new Type[] {},
                                       Constants.INVOKESPECIAL));

        il.append(InstructionConstants.ARETURN);

        mg.setMaxStack();
        classGen.addMethod(mg.getMethod());
        il.dispose();
    }

    private static void closeSafely(ByteArrayOutputStream out)
    {
        if (out != null)
        {
            try
            {
                out.close();
            }
            catch (IOException e)
            {
                // Ignore
            }
        }
    }

    private static void setSslSocketFactoryFieldByReflection(Class<? extends AbstractLDAPSSLSocketFactory> clazz, String fieldName, SSLSocketFactory sslSocketFactory)
    {
        String exceptionMessage = "Unexpected error setting generated static field "
                 + fieldName + "on generated class " + clazz.getName();
        try
        {
            Field declaredField = clazz.getDeclaredField(fieldName);
            boolean accessible = declaredField.isAccessible();
            try
            {
                declaredField.setAccessible(true);
                declaredField.set(null, sslSocketFactory);
            }
            finally
            {
                declaredField.setAccessible(accessible);
            }
        }
        catch (IllegalArgumentException e)
        {
            throw new ServerScopedRuntimeException(exceptionMessage, e);
        }
        catch (IllegalAccessException e)
        {
            throw new ServerScopedRuntimeException(exceptionMessage, e);
        }
        catch (NoSuchFieldException e)
        {
            throw new ServerScopedRuntimeException(exceptionMessage, e);
        }
        catch (SecurityException e)
        {
            throw new ServerScopedRuntimeException(exceptionMessage, e);
        }
    }

    static SSLSocketFactory getStaticFieldByReflection(Class<? extends AbstractLDAPSSLSocketFactory> clazz, String fieldName)
    {
        String exceptionMessage = "Unexpected error getting generated static field "
                + fieldName + "on generated class " + clazz.getName();

        Field declaredField;
        try
        {
            declaredField = clazz.getDeclaredField(fieldName);
            boolean accessible = declaredField.isAccessible();
            try
            {
                declaredField.setAccessible(true);
                return (SSLSocketFactory) declaredField.get(null);
            }
            finally
            {
                declaredField.setAccessible(accessible);
            }
        }
        catch (NoSuchFieldException e)
        {
            throw new ServerScopedRuntimeException(exceptionMessage, e);
        }
        catch (SecurityException e)
        {
            throw new ServerScopedRuntimeException(exceptionMessage, e);
        }
        catch (IllegalArgumentException e)
        {
            throw new ServerScopedRuntimeException(exceptionMessage, e);
        }
        catch (IllegalAccessException e)
        {
            throw new ServerScopedRuntimeException(exceptionMessage, e);
        }
    }

    private static final class LDAPSSLSocketFactoryAwareDelegatingClassloader extends ClassLoader
    {
        private final String _className;
        private final Class<? extends AbstractLDAPSSLSocketFactory> _clazz;

        private LDAPSSLSocketFactoryAwareDelegatingClassloader(String className,
                byte[] classBytes, SSLSocketFactory sslSocketFactory)
        {
            super(LDAPSSLSocketFactoryGenerator.class.getClassLoader());
            _className = className;
            _clazz = (Class<? extends AbstractLDAPSSLSocketFactory>) defineClass(className, classBytes, 0, classBytes.length);
            setSslSocketFactoryFieldByReflection(_clazz, SSL_SOCKET_FACTORY_FIELD, sslSocketFactory);
        }

        @Override
        protected Class<?> findClass(String fqcn) throws ClassNotFoundException
        {
            if (fqcn.equals(_className))
            {
                return _clazz;
            }
            else
            {
                return getParent().loadClass(fqcn);
            }
        }
    }
}
