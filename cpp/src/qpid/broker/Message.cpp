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

#include "Message.h"
#include "ExchangeRegistry.h"
#include "qpid/framing/frame_functors.h"
#include "qpid/framing/BasicPublishBody.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/SendContent.h"
#include "qpid/framing/SequenceNumber.h"
#include "qpid/framing/TypeFilter.h"

using namespace qpid::broker;
using namespace qpid::framing;
using std::string;

TransferAdapter Message::TRANSFER;
PublishAdapter Message::PUBLISH;

Message::Message(const SequenceNumber& id) : frames(id), persistenceId(0), redelivered(false), publisher(0), store(0), adapter(0) {}

std::string Message::getRoutingKey() const
{
    return getAdapter().getRoutingKey(frames);
}

std::string Message::getExchangeName() const 
{
    return getAdapter().getExchange(frames);
}

const boost::shared_ptr<Exchange> Message::getExchange(ExchangeRegistry& registry) const
{
    if (!exchange) {
        exchange = registry.get(getExchangeName());
    } 
    return exchange;
}

bool Message::isImmediate() const
{
    return getAdapter().isImmediate(frames);
}

const FieldTable* Message::getApplicationHeaders() const
{
    return getAdapter().getApplicationHeaders(frames);
}

bool Message::isPersistent()
{
    return getAdapter().isPersistent(frames);
}

uint32_t Message::getRequiredCredit() const
{
    //add up payload for all header and content frames in the frameset
    SumBodySize sum;
    frames.map_if(sum, TypeFilter(METHOD_BODY, HEADER_BODY));
    return sum.getSize();
}

void Message::encode(framing::Buffer& buffer) const
{
    //encode method and header frames
    EncodeFrame f1(buffer);
    frames.map_if(f1, TypeFilter(METHOD_BODY, HEADER_BODY));

    //then encode the payload of each content frame
    EncodeBody f2(buffer);
    frames.map_if(f2, TypeFilter(CONTENT_BODY));
}

uint32_t Message::encodedSize() const
{
    return encodedHeaderSize() + encodedContentSize();
}

uint32_t Message::encodedContentSize() const
{
    return  frames.getContentSize();
}

uint32_t Message::encodedHeaderSize() const
{
    //add up the size for all method and header frames in the frameset
    SumFrameSize sum;
    frames.map_if(sum, TypeFilter(METHOD_BODY, HEADER_BODY));
    return sum.getSize();
}

void Message::decodeHeader(framing::Buffer& buffer)
{
    AMQFrame method;
    method.decode(buffer);
    frames.append(method);

    AMQFrame header;
    header.decode(buffer);
    frames.append(header);
}

void Message::decodeContent(framing::Buffer& buffer)
{
    //get the data as a string and set that as the content
    //body on a frame then add that frame to the frameset
    AMQFrame frame;
    frame.setBody(AMQContentBody());
    frame.castBody<AMQContentBody>()->decode(buffer, buffer.available());
    frames.append(frame);
}

void Message::releaseContent(MessageStore* _store)
{
    store = _store;
    if (!getPersistenceId()) {
        store->stage(*this);
    }
    //remove any content frames from the frameset
    frames.remove(TypeFilter(CONTENT_BODY));
}

void Message::sendContent(framing::FrameHandler& out, uint16_t channel, uint16_t maxFrameSize)
{
    if (isContentReleased()) {
        //load content from store in chunks of maxContentSize
        uint16_t maxContentSize = maxFrameSize - AMQFrame::frameOverhead();
        uint64_t expectedSize(frames.getHeaders()->getContentLength());//TODO: how do we know how much data to load?
        for (uint64_t offset = 0; offset < expectedSize; offset += maxContentSize)
        {            
            uint64_t remaining = expectedSize - offset;
            AMQFrame frame(channel, AMQContentBody());
            string& data = frame.castBody<AMQContentBody>()->getData();

            store->loadContent(*this, data, offset,
                               remaining > maxContentSize ? maxContentSize : remaining);
            out.handle(frame);
        }

    } else {
        SendContent f(out, channel, maxFrameSize);
        frames.map_if(f, TypeFilter(CONTENT_BODY));
    }
}

void Message::sendHeader(framing::FrameHandler& out, uint16_t channel, uint16_t /*maxFrameSize*/)
{
    Relay f(out, channel);
    frames.map_if(f, TypeFilter(HEADER_BODY));    
}

MessageAdapter& Message::getAdapter() const
{
    if (!adapter) {
        if (frames.isA<BasicPublishBody>()) {
            adapter = &PUBLISH;
        } else if(frames.isA<MessageTransferBody>()) {
            adapter = &TRANSFER;
        } else {
            const AMQMethodBody* method = frames.getMethod();
            if (!method) throw Exception("Can't adapt message with no method");
            else throw Exception(QPID_MSG("Can't adapt message based on " << *method));
        }
    }
    return *adapter;
}

uint64_t Message::contentSize() const
{
    return frames.getContentSize();
}

bool Message::isContentLoaded() const
{
    return contentSize() > 0;
}
