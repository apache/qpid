#ifndef QPID_AMQP_0_10_FRAME_H
#define QPID_AMQP_0_10_FRAME_H

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

#include "qpid/amqp_0_10/built_in_types.h"
#include <boost/shared_array.hpp>

namespace qpid {
namespace amqp_0_10 {

class FrameHeader {
  public:
    enum Flags { FIRST_SEGMENT=8, LAST_SEGMENT=4, FIRST_FRAME=2, LAST_FRAME=1 };
    enum ShortFlags { FS=8, LS=4, FF=2, LF=1 };


    FrameHeader(uint8_t flags_=0, SegmentType type_=SegmentType(), uint8_t track_=0, uint16_t channel_=0)
        : flags(flags_), type(type_), size_(), track(track_), channel(channel_)
    {}

    uint8_t getFlags() const { return flags; }
    SegmentType getType() const { return type; }
    uint16_t getSize() const { return size_; }
    uint8_t getTrack() const { return track; }
    uint16_t getChannel() const { return channel; }

    void setFlags(uint8_t flags_) { flags=flags_; }
    void setType(SegmentType type_)  { type=type_; }
    void setTrack(uint8_t track_) { track=track_; }
    void setChannel(uint8_t channel_) { channel=channel_; }

    Flags testFlags(uint8_t f) const { return Flags(flags & f); }
    void raiseFlags(uint8_t f) { flags |= f; }
    void clearFlags(uint8_t f) { flags &= ~f; }

    uint16_t size() const { return size_; }
    bool empty() const { return size()==0; }

    bool operator==(const FrameHeader& x)
    { return memcmp(this, &x, sizeof(this)); }

    template <class S> void serialize(S& s);

  protected:
    uint8_t flags;
    SegmentType type;
    uint16_t size_;
    uint8_t track;
    uint16_t channel;

  private:
};

class Frame : public FrameHeader {
  public:
    Frame(uint8_t flags_=0, SegmentType type_=SegmentType(), uint8_t track_=0,
          uint16_t channel_=0)
        : FrameHeader(flags_, type_, track_, channel_), ref() {}

    Frame(const FrameHeader& header) : FrameHeader(header), ref() {}

    Frame(const FrameHeader& header, size_t s)
        : FrameHeader(header), ref() { resize(s); }

    Frame(const FrameHeader& header, const char* a, const char* b,
          bool copyFlag=true) : FrameHeader(header), ref() {
        if (copyFlag) copy(a,b); else refer(a,b);
    }
    
    Frame(const FrameHeader& header, const char* p, std::size_t s,
          bool copyFlag=true) : FrameHeader(header), ref() {
        if (copyFlag) copy(p,p+s); else refer(p,p+s);
    }
    

    /** Allocate a buffer of at least size bytes */
    void resize(uint16_t size);

    /** Make the frame refer to byte range [begin,end)
     * Range is NOT COPIED, it must be valid for lifetime of Frame.
     */
    void refer(const char* begin, const char* end);

    /** Allocate a buffer and copy range begin/end */
    void copy(const char* begin, const char* end);

    char* begin() { assert(!ref); return data.get(); }
    const char* begin() const { return ref ? ref : data.get(); }
    char* end() { return begin() + size(); }
    const char* end() const { return begin() + size(); }

    void clear() { data.reset();  ref = 0; size_= 0; }
    
    template <class S> void serialize(S& s);
    template <class S> void encode(S& s) const;
    template <class S> void decode(S& s);

  private:
    boost::shared_array<char> data;
    const char* ref;
};

inline void Frame::refer(const char* a, const char* b) {
    data.reset();
    ref = a;
    size_ = b-a;
}

inline void Frame::copy(const char* a, const char* b) {
    resize(b-a);
    std::copy(a, b, begin());
}

inline void Frame::resize(uint16_t s) {
    if (s > size() || ref) {
        ref = 0;
        data.reset(new char[s]);
    }
    size_=s;
}

template <class S> void FrameHeader::serialize(S& s) {
    uint8_t pad8=0;
    uint32_t pad32=0;
    s(flags)(type)(size_)(pad8)(track)(channel)(pad32);
}

template <class S> void Frame::serialize(S& s) { s.split(*this); }

template <class S> void Frame::encode(S& s) const {
    s(static_cast<const FrameHeader&>(*this));
    s.raw(begin(), size());
}

template <class S> void Frame::decode(S& s) {
    try {
        uint16_t oldSize = size_;
        s(static_cast<FrameHeader&>(*this));
        std::swap(oldSize, size_);
        resize(oldSize);
        s.raw(begin(), size());
    } catch (...) {
        clear();
        throw;
    }
}

}} // namespace qpid::amqp_0_10

#endif  /*!QPID_AMQP_0_10_FRAME_H*/

