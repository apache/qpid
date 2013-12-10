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

package org.apache.qpid.amqp_1_0.codec;

public class DoubleWriter extends FixedEightWriter<Double>
{
    private static final byte FORMAT_CODE = (byte) 0x82;


    @Override
    byte getFormatCode()
    {
        return FORMAT_CODE;
    }

    @Override
    long convertValueToLong(Double value)
    {
        return Double.doubleToLongBits(value.doubleValue());
    }

    private static Factory<Double> FACTORY = new Factory<Double>()
                                            {

                                                public ValueWriter<Double> newInstance(Registry registry)
                                                {
                                                    return new DoubleWriter();
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(Double.class, FACTORY);
    }
}