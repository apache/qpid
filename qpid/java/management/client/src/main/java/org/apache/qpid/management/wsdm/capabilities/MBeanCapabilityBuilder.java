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

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;
import javax.xml.namespace.QName;

import org.apache.muse.core.Environment;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.wsdm.common.QManFault;

public class MBeanCapabilityBuilder implements IArtifactBuilder{
		
	private final static String GET_PROPERTY_NAMES_METHOD_COMMON_PART = "public QName[] getPropertyNames() { return ";
	private final static String GET_PROPERTY_NAMES_METHOD_WITH_ARRAY = GET_PROPERTY_NAMES_METHOD_COMMON_PART+" PROPERTIES;}";
	private final static String GET_PROPERTY_NAMES_METHOD_WITH_EMPTY_ARRAY = GET_PROPERTY_NAMES_METHOD_COMMON_PART+" new QName[0];}";
	
	/**
	 * Handler interface definining operation needed to be peformed (by a concrete
	 * implementor) when the "endAttributes" director callback happens.
	 * 
	 * @author Andrea Gazzarini
	 */
	interface EndAttributesHandler {
		
		/**
		 * Concrete implementor must define in this method what
		 * needs to be done when the corresponding director callback
		 * happens (@see {@link MBeanCapabilityBuilder#endAttributes()}
		 * 
		 * @throws BuilderException when a failure is raised inside the concrete implementation.
		 */
		void endAttributes() throws BuilderException;
	};
	
	/**
	 * This is the concrete implementation of the internal interface EndAttributesHandler
	 * that is activated when this builder detects the presence of at least one property on the 
	 * capability class.
	 */
	private final EndAttributesHandler _atLeastThereIsOneProperty = new EndAttributesHandler() {

		/**
		 * Creates the QName array instance member and the corresponding 
		 * accessor getPropertyNames().
		 * 
		 * @throws BuilderException when the member above cannot be added to the capability class.
		 */
		public void endAttributes() throws BuilderException
		{
			try 
			{
				_properties.deleteCharAt(_properties.length()-1);
				_properties.append("};");
				
				CtField properties = CtField.make(_properties.toString(), _capabilityClassDefinition);
				
				 _capabilityClassDefinition.addField(properties);
				
				CtMethod getPropertyNames = CtNewMethod.make(
						GET_PROPERTY_NAMES_METHOD_WITH_ARRAY,
						_capabilityClassDefinition);
				_capabilityClassDefinition.addMethod(getPropertyNames);
			} catch(Exception exception) 
			{ 
				throw new BuilderException(exception);
			}			
		}		
	};
	
	/**
	 * This is the concrete implementation of the internal interface EndAttributesHandler
	 * that is activated when this builder detects that there are no properties defined for 
	 * the capability class.
	 */
	private final EndAttributesHandler _noPropertyHasBeenDefined= new EndAttributesHandler() 
	{
		/**
		 * Creates the getPropertyNames() that simply returns an empty QName array.
		 * 
		 * @throws BuilderException when the member above cannot be added to the capability class.
		 */
		public void endAttributes() throws BuilderException
		{
			try 
			{
				CtMethod getPropertyNames = CtNewMethod.make(
						GET_PROPERTY_NAMES_METHOD_WITH_EMPTY_ARRAY,
						_capabilityClassDefinition);
				_capabilityClassDefinition.addMethod(getPropertyNames);
			} catch(Exception exception) 
			{ 
				throw new BuilderException(exception);
			}			
		}		
	};
	
	private StringBuilder _properties = new StringBuilder("private static final QName[] PROPERTIES = new QName[]{ ");
	private CtClass _capabilityClassDefinition;
	private Class<MBeanCapability> _capabilityClass;
	private EndAttributesHandler _endAttributeHandler = _noPropertyHasBeenDefined;
	
	/**
	 * Director callback. 
	 * Attrbute metadata notification. With this callback the director informs this builder that the 
	 * currently processed MBean has an attribute with the given metadata.
	 * This builder uses this information in order to add a property and the corresponding accessors
	 * to the capability class that is going to be built.
	 * 
	 *  @throws BuilderException bytecode manipulation / creation failure.
	 */
	public void onAttribute(MBeanAttributeInfo attribute) throws BuilderException 
	{
		String name = attribute.getName();
		String type = attribute.getType();
		type = (type.startsWith("[B")) ? " byte[] " : type;
		
		try 
		{
			addPropertyMemberInstance(type, name);

			String nameForAccessors = 
				Character.toUpperCase(name.charAt(0)) + 
				name.substring(1);
			
			if (attribute.isReadable()) 
			{
				generateGetter(type, nameForAccessors);
			}
			
			if (attribute.isWritable()) 
			{
				generateSetter(type, nameForAccessors);
			}		
		} catch(Exception exception)
		{
			throw new BuilderException(exception);
		}
	}

	/**
	 * First callback : this method is called at the begin of the director process.
	 * Contains builder initialization code.
	 * 
	 * @param objectName the name of the target JMX entity of this capability.
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
		pool.importPackage(Result.class.getPackage().getName());
	
		_capabilityClassDefinition = pool.makeClass("org.apache.qpid.management.wsdm.capabilities."+className);
		try 
		{
			_capabilityClassDefinition.setSuperclass(pool.get(MBeanCapability.class.getName()));
		} catch(Exception exception) 
		{
			throw new BuilderException(exception);
		} 
	}
	
	/**
	 * Director callback. 
	 * Operation metadata notification. With this callback the director informs this builder that the 
	 * currently processed MBean has an operation with the given metadata.
	 * This builder uses this information in order to add a method to the capability class that is 
	 * going to be built.
	 * 
	 * For example, let's suppose that an operation like that is detected on the MBean :
	 * 
	 * public void purge(int request)
	 * 
	 * then the capability will be enrichied with the following method :
	 * 
	 * public void purge(int request) throws QManFault {
	 * 	invoke(
	 * 		"purge",
	 * 		new Object[]{request},
	 * 		new String[]{int.class.getName()});
	 * }
	 * 
	 *  @throws BuilderException bytecode manipulation / creation failure.
	 */
	public void onOperation(MBeanOperationInfo operation)  throws BuilderException
	{
		try 
		{
			StringBuilder method = new StringBuilder()
				.append("public Result ")
				.append(operation.getName())
				.append("( ");
			
			for (MBeanParameterInfo parameter: operation.getSignature())
			{
				method
					.append(parameter.getType())
					.append(' ')
					.append(parameter.getName())
					.append(',');
			}
			
			method.deleteCharAt(method.length()-1);
			method.append(") throws QManFault { return invoke(")
				.append("\"").append(operation.getName()).append("\"")
				.append(", new Object[]{ ");
			
			for (MBeanParameterInfo parameter: operation.getSignature())
			{
				method.append(parameter.getName())
					.append(',');
			}
			
			method.deleteCharAt(method.length()-1);			
			method.append("}, new String[]{ ");
			
			for (MBeanParameterInfo parameter: operation.getSignature())
			{
				method
					.append("\"")
					.append(parameter.getType())
					.append("\",");
			}
			method.deleteCharAt(method.length()-1);			
			method.append("}); }");
			
			CtMethod definition = CtNewMethod.make(method.toString(),_capabilityClassDefinition);
			_capabilityClassDefinition.addMethod(definition);			
		} catch(Exception exception)
		{
			throw new BuilderException(exception);
		}
	}

	/**
	 * Returns the capability class (the product of this builder). 
	 * 
	 * @return the capability class (the product of this builder).
	 */
	Class<MBeanCapability> getCapabilityClass() 
	{
		return _capabilityClass;
	}

	/**
	 * Determines what needs to be done when all attributes 
	 * metadata has been notified to this builder.
	 * Capability class must have an array member with all defined 
	 * properties and a getter method that returns it.
	 * In this method those two members are declared (obviously only 
	 * if this capability has at least one property).
	 * 
	 * @throws BuilderException when something fails during this phase.
	 */
	public void endAttributes() throws BuilderException
	{
		_endAttributeHandler.endAttributes();
	}

	/**
	 * Director callback. 
	 * This method is notified when all operations metadata has been 
	 * notified to this builder.
	 * This is the place where the capability class is created, defined and loaded by the JVM.
	 * 
	 *  @throws BuilderException issues on this method are basically class loading related.
	 */
	@SuppressWarnings("unchecked")
	public void endOperations() throws BuilderException
	{
		try 
		{
			_capabilityClass = _capabilityClassDefinition.toClass(
					QManAdapterCapability.class.getClassLoader(),
					QManAdapterCapability.class.getProtectionDomain());
		} catch (Exception exception) 
		{
			throw new BuilderException(exception);
		}
	}

	/**
	 * Injects the module environment on this builder.
	 * 
	 * @param environment the module environment.
	 */
	public void setEnvironment(Environment environment) 
	{
		// N.A. 
	}
	
	/**
	 * Generates the get accessor method for the given property.
	 *  
	 * @param type the type of the property.
	 * @param name the name of the property.
	 * @throws CannotCompileException compilation failure while adding the new feature.
	 */
	private void generateGetter(String type, String name) throws CannotCompileException
	{
		StringBuilder buffer = new StringBuilder()
			.append("public ")
			.append(type)
			.append(' ')
			.append("get")
			.append(name)
			.append("() throws QManFault { return (").append(type).append(") getAttribute(\"")
			.append(name)
			.append("\"); }");
	
		CtMethod getter = CtNewMethod.make(buffer.toString(),_capabilityClassDefinition);
		_capabilityClassDefinition.addMethod(getter);		
		
		appendToPropertiesArray(name);
	}

	/**
	 * Generates the set accessor method for the given property.
	 *  
	 * @param type the type of the property.
	 * @param name the name of the property.
	 * @throws CannotCompileException compilation failure while adding the new feature.
	 */
	private void generateSetter(String type, String name) throws CannotCompileException
	{
		StringBuilder buffer = new StringBuilder()
			.append("public void ")
			.append("set")
			.append(name)
			.append("(")
			.append(type)
			.append(" newValue) throws QManFault {")
			.append(" setAttribute(\"")
			.append(name)
			.append("\", newValue); }");
		
		CtMethod setter = CtNewMethod.make(buffer.toString(),_capabilityClassDefinition);
		_capabilityClassDefinition.addMethod(setter);					
	}
	
	/**
	 * Appends the given attribute name to the properties array declared as an
	 * instance member of the capability class.
	 * 
	 * @param attributeName the name of the attribute.
	 */
	private void appendToPropertiesArray(String attributeName)
	{
		_properties.append("new QName(Names.NAMESPACE_URI, \"")
		.append(attributeName)
		.append("\", Names.PREFIX),");		

		_endAttributeHandler = _atLeastThereIsOneProperty;
	}
	
	/** 
	 * Adds a new property member instance to the capability class.
	 * 
	 * @param type the type of the property.
	 * @param name the name of the property.
	 * @throws CannotCompileException  when the property cannot be added.
	 */
	private void addPropertyMemberInstance(String type, String name) throws CannotCompileException
	{
		StringBuilder buffer = new StringBuilder()
			.append("private ")
			.append(type)
			.append(' ')
			.append(name)
			.append(';');

		CtField field= CtField.make(buffer.toString(),_capabilityClassDefinition);
		_capabilityClassDefinition.addField(field);		
	}	
}