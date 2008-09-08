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
using System.Collections.Generic;
using System.Text;
using Decoder = org.apache.qpid.transport.codec.Decoder;
using Encodable = org.apache.qpid.transport.codec.Encodable;
using Encoder = org.apache.qpid.transport.codec.Encoder;
namespace org.apache.qpid.transport
{
	/// <summary> 
	/// Struct
	/// </summary>

    public abstract class Struct : Encodable
    {
        public  static Struct create(int type)
        {
            return StructFactory.create(type);
        }

        bool dirty = true;

        public bool Dirty
        {
            get { return dirty; }
            set { dirty = value; }
        }

        public abstract int getStructType();

        public abstract int getSizeWidth();

        public abstract int getPackWidth();

        public int getEncodedType()
        {
            int type = getStructType();
            if (type < 0)
            {
                throw new Exception();
            }
            return type;
        }

        private bool isBit<C, T>(Field<C, T> f)
        {
            return Equals(f.Type, typeof(Boolean));
        }

        private bool packed()
        {
            return getPackWidth() > 0;
        }

        private bool encoded<C, T>(Field<C, T> f)
        {
            return !packed() || !isBit(f) && f.has(this);
        }

        private int getFlagWidth()
        {
            return (Fields.Count + 7) / 8;
        }

        private int getFlagCount()
        {
            return 8 * getPackWidth();
        }

        public abstract void read(Decoder dec);

        public abstract void write(Encoder enc);

        public abstract Dictionary<String, Object> Fields
        {
            get;
        }

        public String toString()
        {
            StringBuilder str = new StringBuilder();
            str.Append(GetType());
            str.Append("(");
            bool first = true;
            foreach (KeyValuePair<String, Object> me in Fields)
            {
                if (first)
                {
                    first = false;
                }
                else
                {
                    str.Append(", ");
                }
                str.Append(me.Key);
                str.Append("=");
                str.Append(me.Value);
            }
            str.Append(")");
            return str.ToString();
        }
    }
}