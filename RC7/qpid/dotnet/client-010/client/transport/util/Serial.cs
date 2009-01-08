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
namespace org.apache.qpid.transport.util
{
    /// <summary> 
    /// This class provides basic serial number comparisons as defined in
    /// RFC 1982.
    /// </summary>
    public class Serial
    {
        ///
        /// 
        ///Compares two numbers using serial arithmetic.
        ///
        /// param s1 the first serial number
        /// param s2 the second serial number
        ///
        /// return a negative integer, zero, or a positive integer as the
        /// first argument is less than, equal to, or greater than the
        ///  second
        ///
        public static int compare(int s1, int s2)
        {
            return s1 - s2;
        }

        public static bool lt(int s1, int s2)
        {
            return compare(s1, s2) < 0;
        }

        public static bool le(int s1, int s2)
        {
            return compare(s1, s2) <= 0;
        }

        public static bool gt(int s1, int s2)
        {
            return compare(s1, s2) > 0;
        }

        public static bool ge(int s1, int s2)
        {
            return compare(s1, s2) >= 0;
        }

        public static bool eq(int s1, int s2)
        {
            return s1 == s2;
        }

        public static int min(int s1, int s2)
        {
            if (lt(s1, s2))
            {
                return s1;
            }
            else
            {
                return s2;
            }
        }

        public static int max(int s1, int s2)
        {
            if (gt(s1, s2))
            {
                return s1;
            }
            else
            {
                return s2;
            }
        }
    }
}