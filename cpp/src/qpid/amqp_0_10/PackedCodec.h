#ifndef QPID_AMQP_0_10_PACKEDCODEC_H
#define QPID_AMQP_0_10_PACKEDCODEC_H

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

#include "Codec.h"

namespace qpid {
namespace amqp_0_10 {

/**
 * Packed encoding/decoding. Process or ignore fields based on
 * packing bits.
 */
struct PackedCodec {
    template <class Bits, class InnerDecoder>
    class Decoder :
        public DecoderBase<PackedCodec::Decoder<Bits, InnerDecoder> >
    {
      public:
        Decoder(Bits b, const InnerDecoder& d)
            : bits(b), decode(const_cast<InnerDecoder&>(d)) {}

        // Decode if pack bit is set.
        template<class T> Decoder& operator()(T& t) {
            if (bits & 1)
                decode(t);
            else
                t = T();        // FIXME aconway 2008-04-02: see below
            bits >>= 1;
            return *this;
        }
        Bits bits;
        InnerDecoder& decode;
    };

    template <class Bits, class InnerDecoder>
    static Decoder<Bits, InnerDecoder> decode(Bits b, const InnerDecoder& d) {
        return Decoder<Bits, InnerDecoder>(b,d);
    }

    // FIXME aconway 2008-04-02: Incorrect packed semantics.
    // Current implementation is:
    // - decode value if packed bit set, else default initialize value.
    // - encode always encode values
    // - size count all values.
    // Correct implementation:
    // - optional value of type T is mapped to boost::optional<T>
    // - decode value if packed bit set, else value=boost::none
    // - PackedCodec::Encoder collect packing bits (1 unless == boost::none)
    //   Codec::Encoder optional skip if none
    // - size: count only non-none values.
    // Note we don't encode/decodde the pack bits themselves here, that
    // happens in the Holder. Holders handle size, count & pack attributes.
};


}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_PACKEDCODEC_H*/
