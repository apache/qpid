#ifndef QPID_FRAMING_BUFFERTYPES_H
#define QPID_FRAMING_BUFFERTYPES_H

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

/**@file
 * Using templates with framing::Buffer is difficultg becase of the many
 * different put/get function names. Here we define a set of types
 * corresponding the basic types of Buffer but presenting a uniform
 * encode/decode/encodedSize interface.
 *
 * It also provides some convenience templates for the common case of
 * encoding a single encodable value as a string, e.g.
 *
 * LongString ls("hello");
 * std::string encoded = encodeStr(ls);
 * LongString ls2 = decodeStr<LongString>(encoded);
 * LongString ls3;
 * decodeStr(encoded, ls3);
 */

namespace qpid {
namespace framing {

// Templates to help define types
template <class ValueType> struct BufferTypeTraits {
    typedef void (Buffer::*Put)(const ValueType&);
    typedef void (Buffer::*Get)(ValueType&);
};

template <class ValueType,
          typename BufferTypeTraits<ValueType>::Put PutFn,
          typename BufferTypeTraits<ValueType>::Get GetFn>
struct EncodeDecodeTemplate {
    EncodeDecodeTemplate(const ValueType& s) : value(s) {}
    operator ValueType() const { return value; }

    ValueType value;
    void encode(framing::Buffer& buf) const { (buf.*PutFn)(value); }
    void decode(framing::Buffer& buf) { (buf.*GetFn)(value); }
};

template <size_t Size,
          typename BufferTypeTraits<std::string>::Put PutFn,
          typename BufferTypeTraits<std::string>::Get GetFn
          >
struct StringTypeTemplate : public EncodeDecodeTemplate<std::string, PutFn, GetFn> {
    typedef EncodeDecodeTemplate<std::string, PutFn, GetFn> Base;
    StringTypeTemplate(const std::string& s) : Base(s) {}
    size_t encodedSize() const { return Size + Base::value.size(); }
};


// Convenience tempates for encoding/decoding values to/from a std::string.

/** Encode value as a string. */
template <class T> std::string encodeStr(const T& value) {
    std::string encoded(value.encodedSize(), '\0');
    framing::Buffer buffer(&encoded[0], encoded.size());
    value.encode(buffer);
    return encoded;
}

/** Decode value from a string. */
template <class T> void decodeStr(const std::string& encoded, T& value) {
    framing::Buffer b(const_cast<char*>(&encoded[0]), encoded.size());
    value.decode(b);
}

/** Decode value from a string. */
template <class T> T decodeStr(const std::string& encoded) {
    T value;
    decodeStr(encoded, value);
    return value;
}

// The types

typedef StringTypeTemplate<4, &Buffer::putLongString, &Buffer::getLongString> LongString;
typedef StringTypeTemplate<2, &Buffer::putMediumString, &Buffer::getMediumString> MediumString;
typedef StringTypeTemplate<1, &Buffer::putShortString, &Buffer::getShortString> ShortString;

// TODO aconway 2013-07-26: Add integer types.

}} // namespace qpid::framing

#endif  /*!QPID_FRAMING_BUFFERTYPES_H*/
