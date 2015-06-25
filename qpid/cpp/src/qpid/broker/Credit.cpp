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
#include "qpid/broker/Credit.h"

namespace qpid {
namespace broker {

const uint32_t CreditBalance::INFINITE_CREDIT(0xFFFFFFFF);
CreditBalance::CreditBalance() : balance(0) {}
CreditBalance::~CreditBalance() {}
void CreditBalance::clear() { balance = 0; }
void CreditBalance::grant(uint32_t value)
{
    if (balance != INFINITE_CREDIT) {
        if (value == INFINITE_CREDIT) {
            balance = INFINITE_CREDIT;
        } else if (INFINITE_CREDIT - balance > value) {
            balance += value;
        } else {
            balance = INFINITE_CREDIT - 1;
        }
    }
}
void CreditBalance::consume(uint32_t value) { if (!unlimited()) balance -= value; }
bool CreditBalance::check(uint32_t required) const { return balance >= required; }
uint32_t CreditBalance::remaining() const { return balance; }
uint32_t CreditBalance::allocated() const { return balance; }
bool CreditBalance::unlimited() const { return balance == INFINITE_CREDIT; }

CreditWindow::CreditWindow() : used(0) {}
bool CreditWindow::check(uint32_t required) const { return CreditBalance::check(used + required); }
void CreditWindow::consume(uint32_t value) { if (!unlimited()) used += value; }
void CreditWindow::move(uint32_t value) { if (!unlimited()) used -= value; }
uint32_t CreditWindow::remaining() const { return allocated() - used; }
uint32_t CreditWindow::consumed() const { return used; }

Credit::Credit() : windowing(true) {}
void Credit::setWindowMode(bool b) { windowing = b; }
bool Credit::isWindowMode() const { return windowing; }
void Credit::addByteCredit(uint32_t value)
{
    bytes().grant(value);
}
void Credit::addMessageCredit(uint32_t value)
{
    messages().grant(value);
}
void Credit::cancel()
{
    messages().clear();
    bytes().clear();
}
void Credit::moveWindow(uint32_t m, uint32_t b)
{
    if (windowing) {
        window.messages.move(m);
        window.bytes.move(b);
    }
}
void Credit::consume(uint32_t m, uint32_t b)
{
    messages().consume(m);
    bytes().consume(b);
}
bool Credit::check(uint32_t m, uint32_t b) const
{
    return messages().check(m) && bytes().check(b);
}
CreditPair<uint32_t> Credit::used() const
{
    CreditPair<uint32_t> result;
    if (windowing) {
        result.messages = window.messages.consumed();
        result.bytes = window.bytes.consumed();
    } else {
        result.messages = 0;
        result.bytes = 0;
    }
    return result;
}
CreditPair<uint32_t> Credit::allocated() const
{
    CreditPair<uint32_t> result;
    result.messages = messages().allocated();
    result.bytes = bytes().allocated();
    return result;
}
Credit::operator bool() const
{
    return check(1,1);
}
CreditBalance& Credit::messages()
{
    if (windowing) return window.messages;
    else return balance.messages;
}
CreditBalance& Credit::bytes()
{
    if (windowing) return window.bytes;
    else return balance.bytes;
}
const CreditBalance& Credit::messages() const
{
    if (windowing) return window.messages;
    else return balance.messages;
}
const CreditBalance& Credit::bytes() const
{
    if (windowing) return window.bytes;
    else return balance.bytes;
}
std::ostream& operator<<(std::ostream& out, const CreditBalance& b)
{
    if (b.unlimited()) return out << "unlimited";
    else return out << b.balance;
}
std::ostream& operator<<(std::ostream& out, const CreditWindow& w)
{
    if (w.unlimited()) return out << ((CreditBalance) w);
    else return out << w.remaining() << " (from window of " << w.allocated() << ")";
}
template <class T>
std::ostream& operator<<(std::ostream& out, const CreditPair<T>& pair)
{
    return out << "messages: " << pair.messages << " bytes: " << pair.bytes;
}
std::ostream& operator<<(std::ostream& out, const Credit& c)
{
    if (c.windowing) return out << c.window;
    else return out << c.balance;
}

}} // namespace qpid::broker
