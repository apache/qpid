#ifndef QPID_BROKER_AMQP_EXCEPTION_H
#define QPID_BROKER_AMQP_EXCEPTION_H

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
#include <string>
#include <qpid/Exception.h>

namespace qpid {
namespace broker {
namespace amqp {
/**
 * Exception to signal various AMQP 1.0 defined conditions
 */
class Exception : public qpid::Exception
{
  public:
    Exception(const std::string& name, const std::string& description);
    virtual ~Exception() throw();
    const char* what() const throw();
    const char* symbol() const throw();
  private:
    std::string name;
    std::string description;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_EXCEPTION_H*/
