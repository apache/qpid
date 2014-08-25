/*
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
 */
package org.apache.qpid.disttest.client.property;

import java.util.Arrays;

/**
 * Provides support for numeric generators with lower and upper boundaries.
 */
public abstract class NumericGeneratedPropertySupport extends GeneratedPropertySupport
{
    public static final Class<?>[] SUPPORTED_TYPES = { double.class, float.class, int.class, long.class, short.class,
            byte.class };

    private String _type;
    private double _upper;
    private double _lower;


    public NumericGeneratedPropertySupport()
    {
        super();
        _type = SUPPORTED_TYPES[0].getName();
        _upper = Double.MAX_VALUE;
        _lower = 0.0;
    }

    public synchronized String getType()
    {
        return _type;
    }

    public synchronized double getUpper()
    {
        return _upper;
    }

    public synchronized double getLower()
    {
        return _lower;
    }

    public synchronized void setUpper(double upper)
    {
        _upper = upper;
    }

    public synchronized void setLower(double lower)
    {
        _lower = lower;
    }

    public synchronized void setType(String type)
    {
        _type = toClass(type).getName();
    }

    protected Class<?> toClass(String type)
    {
        Class<?> t = null;
        for (int i = 0; i < SUPPORTED_TYPES.length; i++)
        {
            if (SUPPORTED_TYPES[i].getName().equals(type))
            {
                t = SUPPORTED_TYPES[i];
                break;
            }
        }
        if (t == null)
        {
            throw new IllegalArgumentException("Type " + type + " is not supported: "
                    + Arrays.toString(SUPPORTED_TYPES));
        }
        return t;
    }

    @Override
    public Object nextValue()
    {
        double result = nextDouble();
        return doubleToNumber(result, toClass(_type));
    }

    protected Number doubleToNumber(double value, Class<?> targetType)
    {
        Number result = null;
        if (targetType == double.class)
        {
            result = value;
        }
        else if (targetType == float.class)
        {
            result = (float) value;
        }
        else if (targetType == int.class)
        {
            result = (int) value;
        }
        else if (targetType == long.class)
        {
            result = (long) value;
        }
        else if (targetType == short.class)
        {
            result = (short) value;
        }
        else if (targetType == byte.class)
        {
            result = (byte) value;
        }
        else
        {
            throw new IllegalArgumentException("Type " + targetType + " is not supported: "
                    + Arrays.toString(SUPPORTED_TYPES));
        }
        return result;
    }

    protected abstract double nextDouble();

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = super.hashCode();
        long temp;
        temp = Double.doubleToLongBits(_lower);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + ((_type == null) ? 0 : _type.hashCode());
        temp = Double.doubleToLongBits(_upper);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || !(obj instanceof NumericGeneratedPropertySupport))
        {
            return false;
        }
        NumericGeneratedPropertySupport other = (NumericGeneratedPropertySupport) obj;
        if (Double.doubleToLongBits(_lower) != Double.doubleToLongBits(other._lower)
                || Double.doubleToLongBits(_upper) != Double.doubleToLongBits(other._upper))
        {
            return false;
        }
        if (_type == null && other._type != null)
        {
            return false;
        }
        else if (!_type.equals(other._type))
        {
            return false;
        }
        return true;
    }

}
