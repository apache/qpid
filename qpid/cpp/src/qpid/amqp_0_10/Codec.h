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
struct Codec
{
    template <class T>
    static inline void endianize(T& value) {

#ifdef BOOST_LITTLE_ENDIAN
        std::reverse((char*)&value, (char*)&value+sizeof(value));
#else
        (void)value;            // Avoid unused var warnings.
#endif
    }
    static inline void endianize(char&) {}
    static inline void endianize(uint8_t&) {}
    static inline void endianize(int8_t&) {}


    template <class Out> struct Encode : public ConstSerializer<Encode<Out> > {
        Out out;

        Encode(Out o) : out(o) {}

        using ConstSerializer<Encode<Out> >::operator();
        using ConstSerializer<Encode<Out> >::raw;

        template <class T> 
        typename boost::enable_if<boost::is_integral<T>, Encode&>::type
        operator()(const T& x) { T xx(x); endianize(xx); return raw(xx); }

        // FIXME aconway 2008-02-20: correct float encoading
        template <class T>
        typename boost::enable_if<boost::is_float<T>, Encode&>::type
        operator()(const T& x) { return raw(x); }


        template<class T, class SizeType>
        Encode& operator()(const CodableString<T,SizeType>& str) {
            (*this)(SizeType(str.size()));
            std::for_each(str.begin(), str.end(), *this);
            return *this;
        }

      private:
      friend class ConstSerializer<Encode<Out> >;

        Encode& raw(const void* vp, size_t s) {
            char* p = (char*) vp;
            std::copy(p, p+s, out);
            return *this;
        }

        Encode& byte(char x) { out++ = x; return *this; }
    };

    template <class In> struct Decode : public Serializer<Decode<In> > {
        In in;
        Decode(In i) : in(i) {}

        using Serializer<Decode<In> >::operator();
        using Serializer<Decode<In> >::raw;

        template <class T>
        typename boost::enable_if<boost::is_integral<T>, Decode&>::type
        operator()(T& x) {
            raw(&x, sizeof(x));
            endianize(x);
            return *this;
        }

        template <class T>
        typename boost::enable_if<boost::is_float<T>, Decode&>::type
        operator()(T& x) { return raw(&x, sizeof(x)); }

        template<class T, class SizeType>
        Decode& operator()(CodableString<T,SizeType>& str) {
            SizeType n;
            (*this)(n);
            str.resize(n);
            std::for_each(str.begin(), str.end(), *this);
            return *this;
        }

      private:
      friend class Serializer<Decode<In> >;
        
        Decode& raw(void* vp, size_t s) {
            char* p=(char*)vp;
            std::copy(in, in+s, p);
            return *this;
        }

        Decode& byte(char& x) { x = *in++; return *this; }        
    };

    struct Size : public ConstSerializer<Size> {
        Size() : size(0) {}
        size_t size;
        operator size_t() const { return size; }

        using ConstSerializer<Size>::operator();
        using ConstSerializer<Size>::raw;
        
        template <class T>
        typename boost::enable_if<boost::is_arithmetic<T>, Size&>::type
        operator()(const T&) { size += sizeof(T); return *this; }

        template <class T, size_t N>
        Size& operator()(const boost::array<T,N>&) {
            size += sizeof(boost::array<T,N>);
            return *this;
        }

        template<class T, class SizeType>
        Size& operator()(const CodableString<T,SizeType>& str) {
            size += sizeof(SizeType) + str.size()*sizeof(T);
            return *this;
        }


      private:
      friend class ConstSerializer<Size>;

        Size& raw(void*, size_t s) { size += s; return *this; }
        
        Size& byte(char) { ++size; return *this; }        
    };

    template <class Out, class T>
    static void encode(Out o, const T& x) {
        Encode<Out>encode(o);
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
};

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_CODEC_H*/
