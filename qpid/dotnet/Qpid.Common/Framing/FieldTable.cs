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
using System.Collections;
using System.Text;
using Qpid.Buffer;
using Qpid.Collections;
using Qpid.Messaging;

namespace Qpid.Framing
{
    ///
    /// From the protocol document:
    /// field-table      = short-integer *field-value-pair
    /// field-value-pair = field-name field-value
    /// field-name       = short-string
    /// field-value      = 'S' long-string
    /// 'I' long-integer
    /// 'D' decimal-value
    /// 'T' long-integer
    /// decimal-value    = decimals long-integer
    /// decimals         = OCTET 
    public class FieldTable : IFieldTable
    {
        IDictionary _hash = new LinkedHashtable();
        
        private uint _encodedSize = 0;

        public FieldTable()
        {
        }
        
        /**
         * Construct a new field table.
         * @param buffer the buffer from which to read data. The length byte must be read already
         * @param length the length of the field table. Must be > 0.
         * @throws AMQFrameDecodingException if there is an error decoding the table
         */
        public FieldTable(ByteBuffer buffer, uint length)
        {            
            _encodedSize = length;
            int sizeRead = 0;
            while (sizeRead < _encodedSize)
            {
                int sizeRemaining = buffer.remaining();
                string key = EncodingUtils.ReadShortString(buffer);
                // TODO: use proper charset decoder
                char type = (char)buffer.get();
                object value;
                switch (type)
                {
                    case 'S':
                        value = EncodingUtils.ReadLongString(buffer);
                        break;
                    case 'I':
                        value = buffer.GetUnsignedInt();
                        break;
                    default:
                        throw new AMQFrameDecodingException("Unsupported field table type: '" + type + "' charcode" + (int)type);
                }
                sizeRead += (sizeRemaining - buffer.remaining());
                
                _hash.Add(key, value);
            }
        }

        public uint EncodedSize
        {
            get
            {
                return _encodedSize;
            }
        }

        public int Count
        {
            get { return _hash.Count; }
        }

        public object this[string key]
        {
            get
            {
                CheckKey(key);
                return _hash[key];
            }

            set
            {
                CheckKey(key);
                CheckValue(value);


                object oldValue = _hash[key];
                if (oldValue != null)
                {
                    AdjustEncodingSizeWhenRemoving(key, oldValue);
                } 

                _hash[key] = value;
                AdjustEncodingSizeWhenAdding(key, value);
            }
        }

        public void WriteToBuffer(ByteBuffer buffer)
        {
            // Write out the total length, which we have kept up to date as data is added.
            buffer.put(_encodedSize);
            WritePayload(buffer);            
        }

        private void WritePayload(ByteBuffer buffer)
        {
            foreach (DictionaryEntry lde in _hash)
            {
                string key = (string) lde.Key;
                EncodingUtils.WriteShortStringBytes(buffer, key);
                object value = lde.Value;
                if (value is byte[])
                {
                    buffer.put((byte) 'S');
                    EncodingUtils.WriteLongstr(buffer, (byte[]) value);
                }
                else if (value is string)
                {
                    // TODO: look at using proper charset encoder
                    buffer.put((byte) 'S');
                    EncodingUtils.WriteLongStringBytes(buffer, (string) value);
                }
                else if (value is uint)
                {
                    // TODO: look at using proper charset encoder
                    buffer.put((byte) 'I');
                    buffer.put((uint) value);
                }
                else
                {
                    // Should never get here.
                    throw new InvalidOperationException("Unsupported type in FieldTable: " + value.GetType());
                }
            }
        }

        public byte[] GetDataAsBytes()
        {
            ByteBuffer buffer = ByteBuffer.allocate((int)_encodedSize);
            WritePayload(buffer);
            byte[] result = new byte[_encodedSize];
            buffer.flip();
            buffer.get(result);
            //buffer.Release();
            return result;
        }

        /// <summary>
        /// Adds all the items from one field table in this one. Will overwrite any items in the current table
        /// with the same key.
        /// </summary>
        /// <param name="ft">the source field table</param>
        public void AddAll(IFieldTable ft)
        {
            foreach (DictionaryEntry dictionaryEntry in ft)
            {
                this[(string)dictionaryEntry.Key] = dictionaryEntry.Value;
            }
        }

        private void CheckKey(object key)
        {
            if (key == null)
            {
                throw new ArgumentException("All keys must be Strings - was passed: null");
            }
            else if (!(key is string))
            {
                throw new ArgumentException("All keys must be Strings - was passed: " + key.GetType());
            }
        }

        private void CheckValue(object value)
        {
            if (!(value is string || value is uint || value is int || value is long))
            {
                throw new ArgumentException("All values must be type string or int or long or uint, was passed: " +
                                            value.GetType());
            }
        }

        void AdjustEncodingSizeWhenAdding(object key, object value)
        {
            _encodedSize += EncodingUtils.EncodedShortStringLength((string) key);
            // the extra byte if for the type indicator what is written out
            if (value is string)
            {
                _encodedSize += 1 + EncodingUtils.EncodedLongStringLength((string) value);
            }
            else if (value is int || value is uint || value is long)
            {
                _encodedSize += 1 + 4;
            }            
            else
            {
                // Should never get here since was already checked
                throw new Exception("Unsupported value type: " + value.GetType());
            }
        }

        private void AdjustEncodingSizeWhenRemoving(object key, object value)
        {
            _encodedSize -= EncodingUtils.EncodedShortStringLength((string) key);
            if (value != null)
            {
                if (value is string)
                {
                    _encodedSize -= 1 + EncodingUtils.EncodedLongStringLength((string) value);
                }
                else if (value is int || value is uint || value is long)
                {
                    _encodedSize -= 5;
                }
                else
                {
                    // Should never get here 
                    throw new Exception("Illegal value type: " + value.GetType());
                }
            }
        }

        public IEnumerator GetEnumerator()
        {
            return _hash.GetEnumerator();
        }

        public bool Contains(string s)
        {
            return _hash.Contains(s);
        }

        public void Clear()
        {
            _hash.Clear();
            _encodedSize = 0;
        }

        public void Remove(string key)
        {
            object value = _hash[key];
            if (value != null)
            {
                AdjustEncodingSizeWhenRemoving(key, value);
            } 
            _hash.Remove(key);
        }

        public IDictionary AsDictionary()
        {
            return _hash;
        }

        public override string ToString()
        {
            StringBuilder sb = new StringBuilder("FieldTable{");

            bool first = true;
            foreach (DictionaryEntry entry in _hash)
            {
                if (first)
                {
                    first = !first;
                }
                else
                {
                    sb.Append(", ");
                }
                sb.Append(entry.Key).Append(" => ").Append(entry.Value);
            }

            sb.Append("}");
            return sb.ToString();
        }
    }
}
