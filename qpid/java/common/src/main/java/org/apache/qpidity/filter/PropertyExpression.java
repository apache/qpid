/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpidity.filter;

import org.slf4j.LoggerFactory;
import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.QpidException;

import javax.jms.Message;
import java.lang.reflect.Method;

/**
 * Represents a property  expression
 */
public class PropertyExpression implements Expression
{
    private static final org.slf4j.Logger _logger = LoggerFactory.getLogger(PropertyExpression.class);

    private Method _getter;

    public PropertyExpression(String name)
    {
        Class clazz = Message.class;
        try
        {
            _getter = clazz.getMethod("get" + name, null);
        }
        catch (NoSuchMethodException e)
        {
            PropertyExpression._logger.warn("Cannot compare property: " + name, e);
        }
    }

    public Object evaluate(Message message) throws QpidException
    {
        Object result = null;
        if( _getter != null )
        {
            try
            {
                result = _getter.invoke(message, null);
            }
            catch (Exception e)
            {
                throw new QpidException("cannot evaluate property ", ErrorCode.UNDEFINED, e);
            }
        }
        return result;
    }

    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return _getter.toString();
    }

    /**
     * @see Object#hashCode()
     */
    public int hashCode()
    {
        return _getter.hashCode();
    }

    /**
     * @see Object#equals(Object)
     */
    public boolean equals(Object o)
    {
        if ((o == null) || !this.getClass().equals(o.getClass()))
        {
            return false;
        }
        return _getter.equals(((PropertyExpression) o)._getter);
    }

}
