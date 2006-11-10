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
package org.apache.qpid.management.jmx;

import org.apache.qpid.AMQException;
import org.apache.qpid.schema.cml.CmlDocument;
import org.apache.qpid.schema.cml.FieldType;
import org.apache.qpid.schema.cml.MethodType;
import org.apache.qpid.schema.cml.SchemaReplyType;

import javax.management.modelmbean.DescriptorSupport;
import javax.management.openmbean.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores all OpenMBeanInfo instances.
 * <p/>
 * Builds MBeanInfo instances from the CML schema (which is parsed by XMLBeans) and
 * stores these indexed by CML class name.
 * <p/>
 * When constructing a DynamicMBean this registry is consulted for the MBeanInfo.
 *
 */
public class MBeanInfoRegistry
{
    private Map<String, OpenMBeanInfoSupport> _cmlClass2OpenMBeanInfoMap = new HashMap<String, OpenMBeanInfoSupport>();

    private Map<String, AMQMBeanInfo> _cmlClass2AMQMBeanInfoMap = new HashMap<String, AMQMBeanInfo>();

    public MBeanInfoRegistry(CmlDocument cmlDocument) throws AMQException
    {
        initialise(cmlDocument);
    }

    private void initialise(CmlDocument cmlDocument) throws AMQException
    {
        CmlDocument.Cml cml = cmlDocument.getCml();
        SchemaReplyType schema = cml.getSchemaReply();
        for (org.apache.qpid.schema.cml.ClassType c : schema.getClass1List())
        {
            OpenMBeanAttributeInfo[] attributes = createAttributeInfos(c.getFieldList());
            OpenMBeanOperationInfo[] operations = createOperationInfos(c.getMethodList());
            String className = c.getName();
            OpenMBeanInfoSupport support = new OpenMBeanInfoSupport(className, null, attributes,
                                                                    null, operations, null);
            // we need to store the extra information separately since we cannot subclass
            // OpenMBeanInfoSupport. Doing so means we need to have an AMQMBeanInfo class on each client
            // which defeats the point of OpenMBeans. The extra info is only used by the CMLBean implementation
            // to assist with runtime value lookups.
            AMQMBeanInfo extra = new AMQMBeanInfo(attributes);
            _cmlClass2OpenMBeanInfoMap.put(className, support);
            _cmlClass2AMQMBeanInfoMap.put(className, extra);
        }
    }

    public OpenMBeanInfoSupport getOpenMBeanInfo(String cmlType)
    {
        return _cmlClass2OpenMBeanInfoMap.get(cmlType);
    }

    public AMQMBeanInfo getAMQMBeanInfo(String cmlType)
    {
        return _cmlClass2AMQMBeanInfoMap.get(cmlType);
    }

    private OpenMBeanAttributeInfo[] createAttributeInfos(List<FieldType> fields)
            throws AMQException
    {
        OpenMBeanAttributeInfo[] attributes = new OpenMBeanAttributeInfo[fields.size() + 1];

        // we up the parent attribute which is always present
        try
        {
            DescriptorSupport descriptor = new DescriptorSupport(new String[]{"hidden=true"});
            attributes[attributes.length - 1] = new OpenMBeanAttributeInfoSupport(CMLMBean.PARENT_ATTRIBUTE,
                                                                                  "Parent", SimpleType.OBJECTNAME,
                                                                                  true, false, false);
                                                                                  //descriptor); JDK 1.6 only
        }
        catch (Exception e)
        {
            // should never happen
            throw new AMQException("Unable to create Parent attribute", e);
        }
        // add all the type-specific attributes
        for (int i = 0; i < attributes.length - 1; i++)
        {
            FieldType field = fields.get(i);
            OpenType openType = getOpenType(field.getType(), field.getModify());
            String description = field.getLabel();
            attributes[i] = new OpenMBeanAttributeInfoSupport(field.getName(),
                                                              description != null ? description:"No description",
                                                              openType,
                                                              true,
                                                              field.getModify(),
                                                              openType == SimpleType.BOOLEAN);
        }

        return attributes;
    }

    private static OpenType getOpenType(FieldType.Type.Enum type, boolean isArray)
            throws UnsupportedCMLTypeException, AMQException
    {
        SimpleType simpleType;
        boolean primitive;
        switch (type.intValue())
        {
            // the constants are not public (bug in xmlbeans) so we cannot use
            // the constants that are defined
            // TODO: raise defect with xmlbeans projects
            case 1:
                simpleType = SimpleType.BOOLEAN;
                primitive = true;
                break;
            case 2:
                simpleType = SimpleType.STRING;
                primitive = false;
                break;
            case 3:
                simpleType = SimpleType.INTEGER;
                primitive = true;
                break;
            case 4:
                simpleType = SimpleType.OBJECTNAME;
                primitive = false;
                break;
            case 5:
                simpleType = SimpleType.DATE;
                primitive = false;
                break;
            default:
                throw new UnsupportedCMLTypeException(type.toString());
        }
        if (isArray)
        {
            try
            {
                //return new ArrayType(simpleType, primitive);
                return new ArrayType(1, simpleType);
            }
            catch (OpenDataException e)
            {
                throw new AMQException("Error constructing array type: " + e, e);
            }
        }
        else
        {
            return simpleType;
        }
    }

    private OpenMBeanOperationInfo[] createOperationInfos(List<MethodType> methods)
            throws AMQException
    {
        OpenMBeanOperationInfo[] methodInfos = new OpenMBeanOperationInfo[methods.size()];
        for (int i = 0; i < methodInfos.length; i++)
        {
            MethodType methodType = methods.get(i);
            OpenMBeanParameterInfo[] parameters = createParameterInfos(methodType.getFieldList());
            methodInfos[i] = new OpenMBeanOperationInfoSupport(methodType.getName(), "No description",
                                                               parameters, SimpleType.VOID,
                                                               OpenMBeanOperationInfoSupport.ACTION);
        }
        return methodInfos;
    }

    private OpenMBeanParameterInfo[] createParameterInfos(List<FieldType> parameters)
            throws AMQException
    {
        OpenMBeanParameterInfo[] paramInfos = new OpenMBeanParameterInfo[parameters.size()];
        for (int i = 0; i < paramInfos.length; i++)
        {
            FieldType field = parameters.get(i);
            String description = field.getLabel();
            OpenType openType = getOpenType(field.getType(), field.getModify());
            paramInfos[i] = new OpenMBeanParameterInfoSupport(field.getName(),
                                                              description==null?"No description":description,
                                                              openType);
        }
        return paramInfos;
    }
}

