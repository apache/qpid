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
#include "qpid/amqp/SaslClient.h"
#include "qpid/amqp/Decoder.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp/Encoder.h"
#include "qpid/log/Statement.h"

using namespace qpid::amqp::sasl;

namespace qpid {
namespace amqp {

SaslClient::SaslClient(const std::string& id) : Sasl(id) {}
SaslClient::~SaslClient() {}
void SaslClient::init(const std::string& mechanism, const std::string* response, const std::string* hostname)
{
    void* frame = startFrame();

    void* token = encoder.startList32(&SASL_INIT);
    encoder.writeSymbol(mechanism);
    if (response) encoder.writeBinary(*response);
    else encoder.writeNull();
    if (hostname) encoder.writeString(*hostname);
    else encoder.writeNull();
    encoder.endList32(3, token);

    endFrame(frame);
    QPID_LOG_CAT(debug, protocol, id << " Sent SASL-INIT(" << mechanism << ", " << (response ? *response : "null") << ", " << (hostname ? *hostname : "null") << ")");
}
void SaslClient::response(const std::string* r)
{
    void* frame = startFrame();

    void* token = encoder.startList32(&SASL_RESPONSE);
    if (r) encoder.writeBinary(*r);
    else encoder.writeNull();
    encoder.endList32(1, token);

    endFrame(frame);
    QPID_LOG_CAT(debug, protocol, id << " Sent SASL-RESPONSE(" << (r ? *r : "null") << ")");
}


namespace {
const std::string SPACE(" ");
class SaslMechanismsReader : public Reader
{
  public:
    SaslMechanismsReader(SaslClient& c) : client(c), expected(0) {}
    void onSymbol(const CharSequence& mechanism, const Descriptor*)
    {
        if (expected) {
            mechanisms << mechanism.str() << SPACE;
        } else {
            client.mechanisms(mechanism.str());
        }
    }
    bool onStartArray(uint32_t count, const CharSequence&, const Constructor&, const Descriptor*)
    {
        expected = count;
        return true;
    }
    void onEndArray(uint32_t, const Descriptor*)
    {
        client.mechanisms(mechanisms.str());
    }
  private:
    SaslClient& client;
    uint32_t expected;
    std::stringstream mechanisms;
};
class SaslChallengeReader : public Reader
{
  public:
    SaslChallengeReader(SaslClient& c) : client(c) {}
    void onNull(const Descriptor*) { client.challenge(); }
    void onBinary(const CharSequence& c, const Descriptor*) { client.challenge(c.str()); }
  private:
    SaslClient& client;
};
class SaslOutcomeReader : public Reader
{
  public:
    SaslOutcomeReader(SaslClient& c, bool e) : client(c), expectExtraData(e) {}
    void onUByte(uint8_t c, const Descriptor*)
    {
        if (expectExtraData) code = c;
        else client.outcome(c);
    }
    void onBinary(const CharSequence& extra, const Descriptor*) { client.outcome(code, extra.str()); }
    void onNull(const Descriptor*) { client.outcome(code); }
  private:
    SaslClient& client;
    bool expectExtraData;
    uint8_t code;
};
}

bool SaslClient::onStartList(uint32_t count, const CharSequence& arguments, const CharSequence& /*full raw data*/, const Descriptor* descriptor)
{
    if (!descriptor) {
        QPID_LOG(error, "Expected described type in SASL negotiation but got no descriptor");
    } else if (descriptor->match(SASL_MECHANISMS_SYMBOL, SASL_MECHANISMS_CODE)) {
        QPID_LOG(trace, "Reading SASL-MECHANISMS");
        Decoder decoder(arguments.data, arguments.size);
        if (count != 1) QPID_LOG(error, "Invalid SASL-MECHANISMS frame; exactly one field expected, got " << count);
        SaslMechanismsReader reader(*this);
        decoder.read(reader);
    } else if (descriptor->match(SASL_CHALLENGE_SYMBOL, SASL_CHALLENGE_CODE)) {
        QPID_LOG(trace, "Reading SASL-CHALLENGE");
        Decoder decoder(arguments.data, arguments.size);
        if (count != 1) QPID_LOG(error, "Invalid SASL-CHALLENGE frame; exactly one field expected, got " << count);
        SaslChallengeReader reader(*this);
        decoder.read(reader);
    } else if (descriptor->match(SASL_OUTCOME_SYMBOL, SASL_OUTCOME_CODE)) {
        QPID_LOG(trace, "Reading SASL-OUTCOME");
        Decoder decoder(arguments.data, arguments.size);
        if (count == 1) {
            SaslOutcomeReader reader(*this, false);
            decoder.read(reader);
        } else if (count == 2) {
            SaslOutcomeReader reader(*this, true);
            decoder.read(reader);
        } else {
            QPID_LOG(error, "Invalid SASL-OUTCOME frame; got " << count << " fields");
        }
    } else {
        QPID_LOG(error, "Unexpected descriptor in SASL negotiation: " << *descriptor);
    }
    return false;
}


}} // namespace qpid::amqp
