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
package org.apache.qpid.management.wsdm.capabilities;

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ObjectName;
import javax.xml.namespace.QName;

import org.apache.muse.core.Environment;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.wsdm.common.QManFault;

public class MBeanCapabilityBuilder implements IArtifactBuilder{
		
	private StringBuilder _properties = new StringBuilder("private static final QName[] PROPERTIES = new QName[]{ ");
	private CtClass _capabilityClassDefinition;
	private Class<MBeanCapability> _capabilityClass;
	
	public void onAttribute(MBeanAttributeInfo attribute) throws BuilderException 
	{
		String plainName = attribute.getName();
		
		_properties.append("new QName(Names.NAMESPACE_URI, \"")
			.append(attribute.getName())
			.append("\", Names.PREFIX),");
		
		String type = attribute.getType();
		type = (type.startsWith("[B")) ? " byte[] " : type;
		
		StringBuilder buffer = new StringBuilder()
			.append("private ")
			.append(type)
			.append(' ')
			.append(attribute.getName())
			.append(';');
		try 
		{
			CtField field= CtField.make(buffer.toString(),_capabilityClassDefinition);
			_capabilityClassDefinition.addField(field);
			
			char firstLetter = Character.toUpperCase(plainName.charAt(0));
			String nameForAccessors = firstLetter + plainName.substring(1);
			
			if (attribute.isReadable()) 
			{
				buffer = new StringBuilder()
					.append("public ")
					.append(type)
					.append(' ')
					.append("get")
					.append(nameForAccessors)
					.append("() throws QManFault { return (").append(type).append(") getAttribute(\"")
					.append(plainName)
					.append("\"); }");
				
				CtMethod getter = CtNewMethod.make(buffer.toString(),_capabilityClassDefinition);
				_capabilityClassDefinition.addMethod(getter);
			}
			
			if (attribute.isWritable()) 
			{
				buffer = new StringBuilder()
				.append("public void ")
				.append("set")
				.append(nameForAccessors)
				.append("(")
				.append(type)
				.append(" newValue) throws QManFault {")
				.append(" setAttribute(\"")
				.append(plainName)
				.append("\", newValue); }");
				
				CtMethod setter = CtNewMethod.make(buffer.toString(),_capabilityClassDefinition);
				_capabilityClassDefinition.addMethod(setter);			
			}		
		} catch(Exception exception)
		{
			System.err.println(buffer);
			throw new BuilderException(exception);
		}
	}
	
	/**
	 * First callback : this method is called at the begin of the director process.
	 * It contains builder initialization code.
	 * 
	 * @throws BuilderException when the initialization fails.
	 */
	public void begin(ObjectName objectName) throws BuilderException 
	{
		String className = objectName.getKeyProperty(Names.CLASS);
		ClassPool pool = ClassPool.getDefault();
		pool.insertClassPath(new ClassClassPath(MBeanCapabilityBuilder.class));
		pool.importPackage(QName.class.getPackage().getName());
		pool.importPackage(ObjectName.class.getPackage().getName());
		pool.importPackage(QManFault.class.getPackage().getName());		
		pool.importPackage(Names.class.getPackage().getName());
		_capabilityClassDefinition = pool.makeClass("org.apache.qpid.management.wsdm.capabilities."+className);
		try 
		{
			_capabilityClassDefinition.setSuperclass(pool.get(MBeanCapability.class.getName()));
		} catch(Exception exception) 
		{
			throw new BuilderException(exception);
		} 
	}
	
	public void onOperation(MBeanOperationInfo operation) 
	{
		// TODO
	}

	public Class<MBeanCapability> getCapabilityClass() 
	{
		return _capabilityClass;
	}

	public void endAttributes() throws BuilderException
	{
		try 
		{
			_properties.deleteCharAt(_properties.length()-1);
			_properties.append("};");
				
			CtField properties = CtField.make(_properties.toString(), _capabilityClassDefinition);
			 _capabilityClassDefinition.addField(properties);
			
			CtMethod getPropertyNames = CtNewMethod.make(
					"public QName[] getPropertyNames() { return PROPERTIES;}",
					_capabilityClassDefinition);
			_capabilityClassDefinition.addMethod(getPropertyNames);
		} catch(Exception exception) 
		{ 
			System.err.println(_properties);
			throw new BuilderException(exception);
		}
	}

	@SuppressWarnings("unchecked")
	public void endOperations() throws BuilderException
	{
		try 
		{
			_capabilityClass = _capabilityClassDefinition.toClass();
		} catch (Exception exception) 
		{
			throw new BuilderException(exception);
		}
	}

	public void setEnvironment(Environment environment) 
	{
		// N.A. 
	}
}