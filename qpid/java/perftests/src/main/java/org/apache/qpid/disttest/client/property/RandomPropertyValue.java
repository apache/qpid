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
 * Generates random property values in a given lower and upper boundaries.
 */
public class RandomPropertyValue extends NumericGeneratedPropertySupport
{
    public static final String DEF_VALUE = "random";

    public RandomPropertyValue()
    {
        super();
    }

    @Override
    protected double nextDouble()
    {
        double lower = getLower();
        double upper = getUpper();
        return lower + Math.random() * (upper - lower);
    }

    @Override
    public String getDefinition()
    {
        return DEF_VALUE;
    }

}
