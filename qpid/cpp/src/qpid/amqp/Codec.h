#ifndef QPID_AMQP_CODEC_H
#define QPID_AMQP_CODEC_H

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
namespace qpid {
namespace amqp {

/**
 *
 */
class Codec
{
  public:



  private:

    struct Constructor
    {
        uint8_t code;
        Descriptor descriptor;
        bool isDescribed;
    };

    Constructor readConstructor(Decoder decoder, Reader reader)
    {
        Constructor result;
        result.code = decoder.readCode();
        if (code == DESCRIPTOR) {
            result.isDescribed = true;
            result.descriptor = decoder.readDescriptor();
            result.code = decoder.readCode();
        } else {
            result.isDescribed = false;
        }
        return result;
    }
};

Codec::Descriptor Codec::Decoder::readDescriptor()
{
    uint8_t code = decoder.readCode();
    switch(code) {
      case SYMBOL8:
        return Descriptor(readSequence8());
      case SYMBOL32:
        return Descriptor(readSequence32());
      case ULONG:
        return Descriptor(readULong());
      case ULONG_SMALL:
        return Descriptor((uint64_t) readUByte());
      case ULONG_ZERO:
        return Descriptor((uint64_t) 0);
      default:
        throw qpid::Exception("Expected descriptor of type ulong or symbol; found " << code);
    }
}

Codec::Descriptor::Descriptor(uint64_t id) : value.id(id), type(NUMERIC) {}
Codec::Descriptor::Descriptor(const CharSequence& symbol) : value.symbol(symbol), type(SYMBOLIC) {}
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_CODEC_H*/
