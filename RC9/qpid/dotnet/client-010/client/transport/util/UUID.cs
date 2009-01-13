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

using System;

namespace org.apache.qpid.transport.util
{
    public class UUID
    {
        private long _mostSigBits;

        private long _leastSigBits;


        public UUID(long mostSigBits, long leastSigBits)
        {
            _mostSigBits = mostSigBits;
            _leastSigBits = leastSigBits;
        }

        public long MostSignificantBits
        {
            get { return _mostSigBits; }
            set { _mostSigBits = value; }
        }

        public long LeastSignificantBits
        {
            get { return _leastSigBits; }
            set { _leastSigBits = value; }
        }

        private UUID(byte[] r)
        {
            MostSignificantBits = 0;
            LeastSignificantBits = 0;
            for (int i = 0; i < 8; i++)
                MostSignificantBits = (MostSignificantBits << 8) | (r[i] & 0xff);
            for (int i = 8; i < 16; i++)
                LeastSignificantBits = (LeastSignificantBits << 8) | (r[i] & 0xff); 
        }

        public static UUID randomUUID()
        {
            byte[] randomBytes = new byte[16];
            Random random = new Random();
            random.NextBytes(randomBytes);
            randomBytes[6] &= 0x0f;  
            randomBytes[6] |= 0x40; 
            randomBytes[8] &= 0x3f; 
            randomBytes[8] |= 0x80;           
            return new UUID(randomBytes);
        }

        public new String ToString()
        {
            return (digits(_mostSigBits >> 32, 8) + "-" +
                    digits(_mostSigBits >> 16, 4) + "-" +
                    digits(_mostSigBits, 4) + "-" +
                    digits(_leastSigBits >> 48, 4) + "-" +
                    digits(_leastSigBits, 12));
        }

        private static String digits(long val, int digits)
        {
            long hi = 1L << (digits * 4);
            return Convert.ToString((hi | (val & (hi - 1))), 16);
        }
    }
}
