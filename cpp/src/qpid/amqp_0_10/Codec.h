#ifndef QPID_AMQP_0_10_CODEC_H
#define QPID_AMQP_0_10_CODEC_H

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

#include "built_in_types.h"
#include "qpid/Serializer.h"
#include <boost/type_traits/is_integral.hpp>
#include <boost/type_traits/is_float.hpp>
#include <boost/type_traits/is_arithmetic.hpp>
#include <boost/detail/endian.hpp>
#include <boost/static_assert.hpp>

namespace qpid {
namespace amqp_0_10 {

#ifdef BOOST_LITTLE_ENDIAN
template <class T> void endianize(T& t) {
    char*p =reinterpret_cast<char*>(&t);
    std::reverse(p, p+sizeof(T));
}
#else
template <class T> void endianize(T&) {}
#endif

/**
 * AMQP 0-10 encoding and decoding.
 */
struct Codec {
    // FIXME aconway 2008-02-29: drop this wrapper, rename to
    // IteratorEncoder, IteratorDecoder?

    /** Encode to an output byte iterator */
    template <class OutIter>
    class Encoder : public serialize::Encoder<Encoder<OutIter> >
    {
      public:
        Encoder(OutIter o) : out(o) {}

        using serialize::Encoder<Encoder<OutIter> >::operator();

        // FIXME aconway 2008-03-10:  wrong encoding, need packing support
        Encoder& operator()(bool x) { *out++=x; return *this;} 

        Encoder& operator()(char x) { *out++=x; return *this; }
        Encoder& operator()(int8_t x) { *out++=x; return *this; }
        Encoder& operator()(uint8_t x) { *out++=x; return *this; }

        Encoder& operator()(int16_t x) { return endian(x); }
        Encoder& operator()(int32_t x) { return endian(x); }
        Encoder& operator()(int64_t x) { return endian(x); }

        Encoder& operator()(uint16_t x) { return endian(x); }
        Encoder& operator()(uint32_t x) { return endian(x); }
        Encoder& operator()(uint64_t x) { return endian(x); }

        Encoder& operator()(float x) { return endian(x); }
        Encoder& operator()(double x) { return endian(x); }


        template <class Iter> Encoder& operator()(Iter begin, Iter end) {
            std::for_each(begin, end, serialize::ref(*this));
            return *this;
        }

        void raw(const void* p, size_t n) {
            std::copy((const char*)p, (const char*)p+n, out);
            out += n;
        }

        OutIter pos() const { return out; }

      private:

        template <class T> Encoder& endian(T x) {
            endianize(x); raw(&x, sizeof(x)); return *this;
        }

        OutIter out;
    };

    template <class InIter>
    class Decoder : public serialize::Decoder<Decoder<InIter> > {
      public:
        Decoder(InIter i) : in(i) {}

        using serialize::Decoder<Decoder<InIter> >::operator();
        
        // FIXME aconway 2008-03-10:  wrong encoding, need packing support
        Decoder& operator()(bool& x) { x=*in++; return *this; }

        Decoder& operator()(char& x) { x=*in++; return *this; }
        Decoder& operator()(int8_t& x) { x=*in++; return *this; }
        Decoder& operator()(uint8_t& x) { x=*in++; return *this; }

        Decoder& operator()(int16_t& x) { return endian(x); }
        Decoder& operator()(int32_t& x) { return endian(x); }
        Decoder& operator()(int64_t& x) { return endian(x); }

        Decoder& operator()(uint16_t& x) { return endian(x); }
        Decoder& operator()(uint32_t& x) { return endian(x); }
        Decoder& operator()(uint64_t& x) { return endian(x); }

        Decoder& operator()(float& x) { return endian(x); }
        Decoder& operator()(double& x) { return endian(x); }

        template <class Iter> Decoder& operator()(Iter begin, Iter end) {
            std::for_each(begin, end, serialize::ref(*this));
            return *this;
        }

        void raw(void *p, size_t n) {
            std::copy(in, in+n, (char*)p);
            in += n;
        }

        InIter pos() const { return in; }

      private:

        template <class T> Decoder& endian(T& x) {
            raw(&x, sizeof(x)); endianize(x); return *this;
        }

        InIter in;
    };

    
    class Size : public serialize::Encoder<Size> {
      public:
        Size() : size(0) {}

        operator size_t() const { return size; }

        using serialize::Encoder<Size>::operator();

        // FIXME aconway 2008-03-10:  wrong encoding, need packing support
        Size& operator()(bool x)  { size += sizeof(x); return *this; }
        
        Size& operator()(char x)  { size += sizeof(x); return *this; }
        Size& operator()(int8_t x)  { size += sizeof(x); return *this; }
        Size& operator()(uint8_t x)  { size += sizeof(x); return *this; }

        Size& operator()(int16_t x)  { size += sizeof(x); return *this; }
        Size& operator()(int32_t x)  { size += sizeof(x); return *this; }
        Size& operator()(int64_t x)  { size += sizeof(x); return *this; }

        Size& operator()(uint16_t x)  { size += sizeof(x); return *this; }
        Size& operator()(uint32_t x)  { size += sizeof(x); return *this; }
        Size& operator()(uint64_t x)  { size += sizeof(x); return *this; }

        Size& operator()(float x)  { size += sizeof(x); return *this; }
        Size& operator()(double x)  { size += sizeof(x); return *this; }

        // FIXME aconway 2008-04-02: enable-if optimized (iter,iter) for
        // iter on fixed-size type.
        template <class Iter> Size& operator()(Iter begin, Iter end) {
            std::for_each(begin, end, serialize::ref(*this));
            return *this;
        }

        void raw(const void*, size_t n){ size += n; }

      private:
        size_t size;
    };

    // FIXME aconway 2008-03-11: rename to encoder(), decoder()
    template <class InIter> static Decoder<InIter> decode(const InIter &i) {
        return Decoder<InIter>(i);
    }

    template <class OutIter> static Encoder<OutIter> encode(OutIter i) {
        return Encoder<OutIter>(i);
    }

    template <class T> static size_t size(const T& x) { return Size()(x); }
};

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_CODEC_H*/
