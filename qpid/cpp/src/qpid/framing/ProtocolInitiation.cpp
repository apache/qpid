/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "qpid/framing/ProtocolInitiation.h"

qpid::framing::ProtocolInitiation::ProtocolInitiation(){}

qpid::framing::ProtocolInitiation::ProtocolInitiation(u_int8_t _major, u_int8_t _minor) : version(_major, _minor) {}

qpid::framing::ProtocolInitiation::ProtocolInitiation(const qpid::framing::ProtocolVersion& p) : version(p) {}

qpid::framing::ProtocolInitiation::~ProtocolInitiation(){}

void qpid::framing::ProtocolInitiation::encode(Buffer& buffer){
    buffer.putOctet('A');
    buffer.putOctet('M');
    buffer.putOctet('Q');
    buffer.putOctet('P');
    buffer.putOctet(1);//class
    buffer.putOctet(1);//instance
    buffer.putOctet(version.major_);
    buffer.putOctet(version.minor_);    
}

bool qpid::framing::ProtocolInitiation::decode(Buffer& buffer){
    if(buffer.available() >= 8){
	buffer.getOctet();//A
	buffer.getOctet();//M
	buffer.getOctet();//Q
	buffer.getOctet();//P
	buffer.getOctet();//class
	buffer.getOctet();//instance
	version.major_ = buffer.getOctet();
	version.minor_ = buffer.getOctet();
	return true;
    }else{
	return false;
    }
}

//TODO: this should prbably be generated from the spec at some point to keep the version numbers up to date
