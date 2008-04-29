#include "Bounds.h"

#include "qpid/log/Statement.h"

namespace qpid {
namespace client {

using sys::Monitor;

Bounds::Bounds(size_t maxSize) : max(maxSize), current(0) {}

bool Bounds::expand(size_t sizeRequired, bool block) 
{
    if (max) {
        Monitor::ScopedLock l(lock);
        current += sizeRequired;
        if (block) {
            while (current > max) {
                QPID_LOG(debug, "Waiting for bounds: " << *this); 
                lock.wait();
            }
            QPID_LOG(debug, "Bounds ok: " << *this);
        }
        return current <= max;
    } else {
        return true;
    }
}

void Bounds::reduce(size_t size)
{
    if (!max || size == 0) return;
    Monitor::ScopedLock l(lock);
    if (current == 0) return;
    bool needNotify = current > max;
    current -= std::min(size, current);
    if (needNotify && current < max) {
        //todo: notify one at a time, but ensure that all threads are
        //eventually notified
        lock.notifyAll();
    }
}

size_t Bounds::getCurrentSize()
{
    Monitor::ScopedLock l(lock);
    return current;
}

std::ostream& operator<<(std::ostream& out, const Bounds& bounds) {
    out << "current=" << bounds.current << ", max=" << bounds.max << " [" << &bounds << "]";
    return out;
}

}} // namespace qpid::client
