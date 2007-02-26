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
using Qpid.Buffer;

namespace Qpid.Codec
{
    public abstract class CumulativeProtocolDecoder : IProtocolDecoder
    {
        ByteBuffer _remaining;

        /// <summary>
        /// Creates a new instance with the 4096 bytes initial capacity of
        /// cumulative buffer.
        /// </summary>
        protected CumulativeProtocolDecoder()
        {
            _remaining = ByteBuffer.Allocate(4096);
            _remaining.IsAutoExpand = true;
        }

        /// <summary>
        /// Cumulates content of <tt>in</tt> into internal buffer and forwards
        /// decoding request to {@link #doDecode(IoSession, ByteBuffer, ProtocolDecoderOutput)}.
        /// <tt>doDecode()</tt> is invoked repeatedly until it returns <tt>false</tt>
        /// and the cumulative buffer is compacted after decoding ends.
        /// </summary>
        /// <exception cref="Exception">
        /// if your <tt>doDecode()</tt> returned <tt>true</tt> not consuming the cumulative buffer.
        /// </exception>
        public void Decode(ByteBuffer input, IProtocolDecoderOutput output)
        {
            if (_remaining.Position != 0) // If there were remaining undecoded bytes
            {
                DecodeRemainingAndInput(input, output);
            }
            else
            {
                DecodeInput(input, output);
            }
        }

        private void DecodeInput(ByteBuffer input, IProtocolDecoderOutput output)
        {
            // Just decode the input buffer and remember any remaining undecoded bytes.
            try
            {
                DecodeAll(input, output);
            }
            finally
            {
                if (input.HasRemaining)
                {
                    _remaining.Put(input);
                }
            }
        }

        private void DecodeRemainingAndInput(ByteBuffer input, IProtocolDecoderOutput output)
        {
            // Concatenate input buffer with left-over bytes.
            _remaining.Put(input);
            _remaining.Flip();

            try
            {
                DecodeAll(_remaining, output);
            }
            finally
            {
                _remaining.Compact();
            }
        }

        private void DecodeAll(ByteBuffer buf, IProtocolDecoderOutput output)
        {
            for (;;)
            {
                int oldPos = buf.Position;
                bool decoded = DoDecode(buf, output);
                if (decoded)
                {                        
                    if (buf.Position == oldPos)
                    {
                        throw new Exception(
                            "doDecode() can't return true when buffer is not consumed.");
                    }

                    if (!buf.HasRemaining)
                    {
                        break;
                    }
                }
                else
                {
                    break;
                }
            }
        }

        /// <summary>
        /// Implement this method to consume the specified cumulative buffer and
        /// decode its content into message(s). 
        /// </summary>
        /// <param name="input">the cumulative buffer</param>
        /// <param name="output">decoder output</param>
        /// <returns>
        /// <tt>true</tt> if and only if there's more to decode in the buffer
        /// and you want to have <tt>doDecode</tt> method invoked again.
        /// Return <tt>false</tt> if remaining data is not enough to decode,
        /// then this method will be invoked again when more data is cumulated.
        /// </returns>
        /// <exception cref="Exception">If cannot decode</exception>
        protected abstract bool DoDecode(ByteBuffer input, IProtocolDecoderOutput output);

        public void Dispose()
        {
            _remaining = null;
        }
    }
}
