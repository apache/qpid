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
package org.apache.qpid.tools;

/**
 * In the future this will be replaced by a Clock abstraction
 * that can utilize a realtime clock when running in RT Java.
 */

public class Clock
{
    private static Precision precision;
    private static long offset = -1;  // in nano secs

    public enum Precision
    {
        NANO_SECS, MILI_SECS;

        static Precision getPrecision(String str)
        {
            if ("mili".equalsIgnoreCase(str))
            {
                return MILI_SECS;
            }
            else
            {
                return NANO_SECS;
            }
        }
    };

    static
    {
        precision = Precision.getPrecision(System.getProperty("precision","mili"));
        //offset = Long.getLong("offset",-1);

        System.out.println("Using precision : " + precision + " and offset " + offset);
    }

    public static Precision getPrecision()
    {
        return precision;
    }

    public static long getTime()
    {
        if (precision == Precision.NANO_SECS)
        {
            if (offset == -1)
            {
                return System.nanoTime();
            }
            else
            {
                return System.nanoTime() + offset;
            }
        }
        else
        {
            if (offset == -1)
            {
                return System.currentTimeMillis();
            }
            else
            {
                return System.currentTimeMillis() + offset/convertToMiliSecs();
            }
        }
    }

    public static long convertToSecs()
    {
        if (precision == Precision.NANO_SECS)
        {
            return 1000000000;
        }
        else
        {
            return 1000;
        }
    }

    public static long convertToMiliSecs()
    {
        if (precision == Precision.NANO_SECS)
        {
            return 1000000;
        }
        else
        {
            return 1;
        }
    }
}
