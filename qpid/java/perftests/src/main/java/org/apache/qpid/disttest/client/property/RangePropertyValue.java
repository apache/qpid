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

/**
 * Generates property values from a range with given lower and upper boundaries.
 */
public class RangePropertyValue extends NumericGeneratedPropertySupport
{
    public static final String DEF_VALUE = "range";
    private double _step;
    private double _currentValue;
    private boolean _cyclic;

    public RangePropertyValue()
    {
        super();
        _cyclic = true;
        _currentValue = 0.0;
        _step = 1.0;
    }

    public synchronized double getStep()
    {
        return _step;
    }

    public synchronized boolean isCyclic()
    {
        return _cyclic;
    }

    public synchronized void setCyclic(boolean cyclic)
    {
        _cyclic = cyclic;
    }

    public synchronized void setStep(double step)
    {
        _step = step;
    }

    @Override
    public synchronized double nextDouble()
    {
        double result = 0.0;
        double lower = getLower();
        double upper = getUpper();
        if (_currentValue < lower)
        {
            _currentValue = lower;
        }
        else if (_currentValue > upper)
        {
            if (_cyclic)
            {
                _currentValue = lower;
            }
            else
            {
                _currentValue = upper;
            }
        }
        result = _currentValue;
        _currentValue += _step;
        return result;
    }

    @Override
    public String getDefinition()
    {
        return DEF_VALUE;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = super.hashCode();
        long temp;
        temp = Double.doubleToLongBits(_currentValue);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + (_cyclic ? 1231 : 1237);
        temp = Double.doubleToLongBits(_step);
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
        if (!(obj instanceof RangePropertyValue))
        {
            return false;
        }
        if (!super.equals(obj))
        {
            return false;
        }
        RangePropertyValue other = (RangePropertyValue) obj;
        if (Double.doubleToLongBits(_currentValue) != Double.doubleToLongBits(other._currentValue)
                || Double.doubleToLongBits(_step) != Double.doubleToLongBits(other._step) || _cyclic != other._cyclic)
        {
            return false;
        }

        return true;
    }
}
