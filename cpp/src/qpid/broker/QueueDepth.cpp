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
#include "QueueDepth.h"

namespace qpid {
namespace broker {

QueueDepth::QueueDepth() {}
QueueDepth::QueueDepth(uint32_t c, uint64_t s) : count(c), size(s) {}
QueueDepth& QueueDepth::operator+=(const QueueDepth& other)
{
    if (count.valid) count.value += other.count.value;
    if (size.valid) size.value += other.size.value;
    return *this;
}
QueueDepth& QueueDepth::operator-=(const QueueDepth& other)
{
    if (count.valid) count.value -= other.count.value;
    if (size.valid) size.value -= other.size.value;
    return *this;
}
bool QueueDepth::operator==(const QueueDepth& other) const
{
    //only compare values, not validity an invalid value is always 0;
    //this means that an invalid value will match an empty queue
    //depth, which is fine
    return (count.value == other.count.value)
        && (size.value == other.size.value);
}
bool QueueDepth::operator!=(const QueueDepth& other) const
{
    return !(*this == other);
}
bool QueueDepth::operator<(const QueueDepth& other) const
{
    if (count.valid && size.valid)
        return count.value < other.count.value || size.value < other.size.value;
    else if (count.valid)
        return count.value < other.count.value;
    else
        return size.value < other.size.value;
}
bool QueueDepth::operator>(const QueueDepth& other) const
{
    if (count.valid && size.valid)
        return count.value > other.count.value || size.value > other.size.value;
    else if (count.valid)
        return count.value > other.count.value;
    else
        return size.value > other.size.value;
}
bool QueueDepth::hasCount() const { return count.valid; }
uint32_t QueueDepth::getCount() const { return count.value; }
void QueueDepth::setCount(uint32_t c) { count.value = c; count.valid = true; }
bool QueueDepth::hasSize() const { return size.valid; }
uint64_t QueueDepth::getSize() const { return size.value; }
void QueueDepth::setSize(uint64_t c) { size.value = c; size.valid = true; }

namespace{
    template <typename T> QueueDepth::Optional<T> add(const QueueDepth::Optional<T>& a, const QueueDepth::Optional<T>& b)
    {
        QueueDepth::Optional<T> result;
        if (a.valid && b.valid) {
            result.valid = true;
            result.value = a.value + b.value;
        }
        return result;
    }
    template <typename T> QueueDepth::Optional<T> subtract(const QueueDepth::Optional<T>& a, const QueueDepth::Optional<T>& b)
    {
        QueueDepth::Optional<T> result;
        if (a.valid && b.valid) {
            result.valid = true;
            result.value = a.value - b.value;
        }
        return result;
    }
}
QueueDepth operator-(const QueueDepth& a, const QueueDepth& b)
{
    QueueDepth result;
    result.count = subtract(a.count, b.count);
    result.size = subtract(a.size, b.size);
    return result;
}

QueueDepth operator+(const QueueDepth& a, const QueueDepth& b)
{
    QueueDepth result;
    result.count = add(a.count, b.count);
    result.size = add(a.size, b.size);
    return result;

}

std::ostream& operator<<(std::ostream& o, const QueueDepth& d)
{
    if (d.hasCount()) o << "count: " << d.getCount();
    if (d.hasSize()) {
        if (d.hasCount()) o << ", ";
        o << "size: " << d.getSize();
    }
    return o;
}

QueueDepth::operator bool() const { return hasCount() || hasSize(); }


}} // namespace qpid::broker
