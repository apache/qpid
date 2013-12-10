#ifndef QPID_BROKER_CREDIT_H
#define QPID_BROKER_CREDIT_H

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
#include <memory>
#include <ostream>

namespace qpid {
namespace broker {

class CreditBalance {
  public:
    CreditBalance();
    virtual ~CreditBalance();
    void clear();
    void grant(uint32_t value);
    virtual void consume(uint32_t value);
    virtual bool check(uint32_t required) const;
    virtual uint32_t remaining() const;
    uint32_t allocated() const;
    bool unlimited() const;
    static const uint32_t INFINITE_CREDIT;
  friend std::ostream& operator<<(std::ostream&, const CreditBalance&);
  private:
    uint32_t balance;
};

class CreditWindow : public CreditBalance {
  public:
    CreditWindow();
    bool check(uint32_t required) const;
    void consume(uint32_t value);
    void move(uint32_t value);
    uint32_t remaining() const;
    uint32_t consumed() const;
  friend std::ostream& operator<<(std::ostream&, const CreditWindow&);
  private:
    uint32_t used;
};

template<class T> struct CreditPair
{
    T messages;
    T bytes;
};

class Credit {
  public:
    Credit();
    void setWindowMode(bool);
    bool isWindowMode() const;
    void addByteCredit(uint32_t);
    void addMessageCredit(uint32_t);
    void consume(uint32_t messages, uint32_t bytes);
    void moveWindow(uint32_t messages, uint32_t bytes);
    bool check(uint32_t messages, uint32_t bytes) const;
    void cancel();
    operator bool() const;
    CreditPair<uint32_t> allocated() const;
    CreditPair<uint32_t> used() const;
  friend std::ostream& operator<<(std::ostream&, const Credit&);
  private:
    CreditPair<CreditBalance> balance;
    CreditPair<CreditWindow> window;
    bool windowing;
    CreditBalance& bytes();
    CreditBalance& messages();
    const CreditBalance& bytes() const;
    const CreditBalance& messages() const;
};

std::ostream& operator<<(std::ostream&, const Credit&);

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_CREDIT_H*/
