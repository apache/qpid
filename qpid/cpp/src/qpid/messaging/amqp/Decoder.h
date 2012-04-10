#ifndef QPID_MESSAGING_AMQP_DECODER_H
#define QPID_MESSAGING_AMQP_DECODER_H

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
#include "qpid/sys/IntegerTypes.h"
#include <string>
extern "C" {
#include "proton/codec.h"
}

namespace qpid {
namespace messaging {
struct MessageImpl;
namespace amqp {

class DecoderBase
{
  public:
    virtual ~DecoderBase();
    virtual void onNull();
    virtual void onBool(bool v);
    virtual void onUbyte(uint8_t v);
    virtual void onByte(int8_t v);
    virtual void onUshort(uint16_t v);
    virtual void onShort(int16_t v);
    virtual void onUint(uint32_t v);
    virtual void onInt(int32_t v);
    virtual void onFloat(float v);
    virtual void onUlong(uint64_t v);
    virtual void onLong(int64_t v);
    virtual void onDouble(double v);
    virtual void onBinary(size_t size, char *bytes);
    virtual void onUtf8(size_t size, char *utf8);
    virtual void onSymbol(size_t size, char *str);
    virtual void startDescriptor();
    virtual void stopDescriptor();
    virtual void startArray(size_t count, uint8_t code);
    virtual void stopArray(size_t count, uint8_t code);
    virtual void startList(size_t count);
    virtual void stopList(size_t count);
    virtual void startMap(size_t count);
    virtual void stopMap(size_t count);
  protected:
    bool inDescriptor;
};

/**
 *
 */
class Decoder : public DecoderBase
{
  public:
    virtual ~Decoder();
    ssize_t decode(const char*, size_t);
};

class ContextSensitiveDecoder : public Decoder
{
  public:
    ContextSensitiveDecoder();
    virtual ~ContextSensitiveDecoder();
  protected:
    bool described;
    ssize_t descriptorLevel;
    ssize_t valueLevel;

    void setDecoder(DecoderBase*);
    virtual DecoderBase* getDecoder();
    virtual void startNested();
    virtual void stopNested();
    virtual void onValue();
  private:
    DecoderBase* decoder;
    void onNull();
    void onBool(bool v);
    void onUbyte(uint8_t v);
    void onByte(int8_t v);
    void onUshort(uint16_t v);
    void onShort(int16_t v);
    void onUint(uint32_t v);
    void onInt(int32_t v);
    void onFloat(float v);
    void onUlong(uint64_t v);
    void onLong(int64_t v);
    void onDouble(double v);
    void onBinary(size_t size, char *bytes);
    void onUtf8(size_t size, char *utf8);
    void onSymbol(size_t size, char *str);
    void startDescriptor();
    void stopDescriptor();
    void startArray(size_t count, uint8_t code);
    void stopArray(size_t count, uint8_t code);
    void startList(size_t count);
    void stopList(size_t count);
    void startMap(size_t count);
    void stopMap(size_t count);
};

class DescribedValueDecoder : public ContextSensitiveDecoder
{
  public:
    DescribedValueDecoder(uint64_t descriptorCode, const std::string& descriptorSymbol);
    virtual ~DescribedValueDecoder();
  protected:
    virtual DecoderBase* getDecoder();
  private:
    class DescriptorDecoder : public DecoderBase
    {
      public:
        DescriptorDecoder(DescribedValueDecoder&);
        void onSymbol(size_t size, char *str);
        void onUlong(uint64_t v);
      private:
        DescribedValueDecoder& parent;
    };

    uint64_t descriptorCode;
    std::string descriptorSymbol;
    DescriptorDecoder descriptorDecoder;
    bool matched;
};

class MessageDataDecoder : public DescribedValueDecoder
{
  public:
    MessageDataDecoder(qpid::messaging::MessageImpl& msg);
  private:
    qpid::messaging::MessageImpl& msg;
    void onBinary(size_t size, char *bytes);
};

}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_DECODER_H*/
