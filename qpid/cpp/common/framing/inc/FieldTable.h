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

#ifndef _FieldTable_
#define _FieldTable_

namespace qpid {
namespace framing {

    class NamedValue;
    class Value;
    class Buffer;

    class FieldTable
    {
	std::vector<NamedValue*> values;
	NamedValue* find(const std::string& name) const;

	Value* getValue(const std::string& name) const;
	void setValue(const std::string& name, Value* value);

    public:
	~FieldTable();
	u_int32_t size() const;
	int count() const;
	void setString(const std::string& name, const std::string& value);
	void setInt(const std::string& name, int value);
	void setTimestamp(const std::string& name, u_int64_t value);
	void setTable(const std::string& name, const FieldTable& value);
	//void setDecimal(string& name, xxx& value);
        std::string getString(const std::string& name);
	int getInt(const std::string& name);
	u_int64_t getTimestamp(const std::string& name);
	void getTable(const std::string& name, FieldTable& value);
	//void getDecimal(string& name, xxx& value);

	void encode(Buffer& buffer) const;
	void decode(Buffer& buffer);

	friend std::ostream& operator<<(std::ostream& out, const FieldTable& body);
    };

    class FieldNotFoundException{};
    class UnknownFieldName : public FieldNotFoundException{};
    class IncorrectFieldType : public FieldNotFoundException{};
}
}


#endif
