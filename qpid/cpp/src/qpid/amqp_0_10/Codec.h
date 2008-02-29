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
#include <boost/ref.hpp>

namespace qpid {
namespace amqp_0_10 {
/**
 * AMQP 0-10 encoding and decoding.
 */
class Codec {
  public:
    /** Encode to an output byte iterator */
    template <class OutIter>
    class Encode : public Serializer<Encode<OutIter> > {
      public:
        Encode(OutIter o) : out(o) {}

        using Serializer<Encode<OutIter> >::operator();

        template <class T>
        typename boost::enable_if<boost::is_integral<T>, Encode&>::type
        operator()(T x) {
            endianize(x);
            raw(&x, sizeof(x));
            return *this;
        }

        // FIXME aconway 2008-02-20: correct float encoading?
        template <class T>
        typename boost::enable_if<boost::is_float<T>, Encode&>::type
        operator()(const T& x) { raw(&x, sizeof(x)); return *this; }

        void raw(const void* p, size_t n) {
            std::copy((const char*)p, (const char*)p+n, out); 
        }
        
      private:
        OutIter out;
    };

    template <class InIter>
    class Decode : public Serializer<Decode<InIter> > {
      public:
        Decode(InIter i) : in(i) {}
        
        using Serializer<Decode<InIter> >::operator();
        
        template <class T>
        typename boost::enable_if<boost::is_integral<T>, Decode&>::type
        operator()(T& x) {
            raw(&x, sizeof(x));
            endianize(x);
            return *this;
        }

        template <class T>
        typename boost::enable_if<boost::is_float<T>, Decode&>::type
        operator()(T& x) { raw(&x, sizeof(x)); return *this; }

        template<class T, class SizeType>
        Decode& operator()(SerializableString<T,SizeType>& str) {
            SizeType n;
            (*this)(n);
            str.resize(n);
            std::for_each(str.begin(), str.end(), *this);
            return *this;
        }

        void raw(void *p, size_t n) {
            // FIXME aconway 2008-02-29: requires random access iterator,
            // does this optimize to memcpy? Is there a better way?
            std::copy(in, in+n, (char*)p);
            in += n;
        }

      private:
        InIter in;
    };

    
    class Size : public Serializer<Size> {
      public:
        Size() : size(0) {}

        operator size_t() const { return size; }

        using Serializer<Size>::operator();
        
        template <class T>
        typename boost::enable_if<boost::is_arithmetic<T>, Size&>::type
        operator()(const T&) { size += sizeof(T); return *this; }

        template<class T, class SizeType>
        Size& operator()(const SerializableString<T,SizeType>& str) {
            size += sizeof(SizeType) + str.size()*sizeof(T);
            return *this;
        }

        void raw(const void*, size_t n){ size += n; }

      private:
        size_t size;
    };

    template <class Out, class T>
    static void encode(Out o, const T& x) {
        Encode<Out> encode(o);
        encode(x);
    }

    template <class In, class T>
    static void decode(In i, T& x) {
        Decode<In> decode(i);
        decode(x);
    }

    template <class T>
    static size_t size(const T& x) {
        Size sz;
        sz(x);
        return sz;
    }

  private:
    template <class T> static inline void endianize(T& value) {
#ifdef BOOST_LITTLE_ENDIAN
        std::reverse((char*)&value, (char*)&value+sizeof(value));
#else
        (void)value;            // Avoid unused var warnings.
#endif
    }
    static inline void endianize(char&) {}
    static inline void endianize(uint8_t&) {}
    static inline void endianize(int8_t&) {}
};

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_CODEC_H*/
