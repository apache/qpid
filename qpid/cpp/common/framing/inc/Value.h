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
#include <iostream>
#include <vector>
#include "amqp_types.h"
#include "FieldTable.h"

#ifndef _Value_
#define _Value_

namespace qpid {
namespace framing {

    class Buffer;

    class Value{
    public:
	inline virtual ~Value(){}
	virtual u_int32_t size() const = 0;
	virtual char getType() const = 0;
	virtual void encode(Buffer& buffer) = 0;
	virtual void decode(Buffer& buffer) = 0;
    };

    class StringValue : public virtual Value{
	string value;
	
    public:
	inline StringValue(const string& v) : value(v){}
        inline StringValue(){}
	inline string getValue(){ return value; }
	~StringValue(){}
	inline virtual u_int32_t size() const { return 4 + value.length(); }
	inline virtual char getType() const { return 'S'; }
	virtual void encode(Buffer& buffer);
	virtual void decode(Buffer& buffer);
    };

    class IntegerValue : public virtual Value{
	int value;
    public:
	inline IntegerValue(int v) : value(v){}
	inline IntegerValue(){}
	inline int getValue(){ return value; }
	~IntegerValue(){}
	inline virtual u_int32_t size() const { return 4; }
	inline virtual char getType() const { return 'I'; }
	virtual void encode(Buffer& buffer);
	virtual void decode(Buffer& buffer);
    };

    class TimeValue : public virtual Value{
	u_int64_t value;
    public:
	inline TimeValue(int v) : value(v){}
	inline TimeValue(){}
	inline u_int64_t getValue(){ return value; }
	~TimeValue(){}
	inline virtual u_int32_t size() const { return 8; }
	inline virtual char getType() const { return 'T'; }
	virtual void encode(Buffer& buffer);
	virtual void decode(Buffer& buffer);
    };

    class DecimalValue : public virtual Value{
	u_int8_t decimals;
	u_int32_t value;
    public:
	inline DecimalValue(int v) : value(v){}
	inline DecimalValue(){}
	~DecimalValue(){}
	inline virtual u_int32_t size() const { return 5; }
	inline virtual char getType() const { return 'D'; }
	virtual void encode(Buffer& buffer);
	virtual void decode(Buffer& buffer);
    };

    class FieldTableValue : public virtual Value{
	FieldTable value;
    public:
	inline FieldTableValue(const FieldTable& v) : value(v){}
	inline FieldTableValue(){}
	inline FieldTable getValue(){ return value; }
	~FieldTableValue(){}
	inline virtual u_int32_t size() const { return 4 + value.size(); }
	inline virtual char getType() const { return 'F'; }
	virtual void encode(Buffer& buffer);
	virtual void decode(Buffer& buffer);
    };
}
}


#endif
