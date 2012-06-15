/*
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
 */

%module cqpid
%include "std_string.i"
%include "../../swig_java_typemaps.i"

%begin %{
struct BYTE_BUFFER
{
    public :
       BYTE_BUFFER(): start(0), size(0) {}
       BYTE_BUFFER(void* p, long s) : start(p), size(s) {}
       void* getStart() const { return start; }
       long getSize() const { return size; }
    private:
       void* start;
       long  size;
};
%}

%extend qpid::messaging::Message {

void setContentAsByteBuffer(const BYTE_BUFFER buf)
{
    self->setContent(reinterpret_cast<char*>(buf.getStart()), buf.getSize());
}

const BYTE_BUFFER getContentAsByteBuffer() const
{
    return BYTE_BUFFER(static_cast<void*>(const_cast<char*>(self->getContentPtr())),self->getContentSize());
}

std::string toString()
{
    std::ostringstream toStr;
    toStr << "{" << std::endl;
    toStr << " Message-ID=" << self->getMessageId() << std::endl;
    toStr << " Correlation-ID=" << self->getCorrelationId() << std::endl;
    toStr << " Subject=" << self->getSubject() << std::endl;
    toStr << " Durability=" << (self->getDurable()? "true" : "false" ) << std::endl;
    toStr << " TTL=" << self->getTtl().getMilliseconds() << std::endl;
    toStr << " Redelivered=" << (self->getRedelivered()? "true" : "false" ) << std::endl;
    toStr << " Application-Properties=" << self->getProperties() << std::endl;
    toStr << "}" << std::endl;

    return toStr.str();
}

}
%rename(NativeConnection) qpid::messaging::Connection;
%rename(NativeSession) qpid::messaging::Session;
%rename(NativeSender) qpid::messaging::Sender;
%rename(NativeReceiver) qpid::messaging::Receiver;
%rename(NativeMessage) qpid::messaging::Message;

%include "../qpid.i"

