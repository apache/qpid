#include "Bounds.h"

#include "qpid/log/Statement.h"
#include "qpid/sys/Waitable.h"

namespace qpid {
namespace client {

using sys::Waitable;

Bounds::Bounds(size_t maxSize) : max(maxSize), current(0) {}

bool Bounds::expand(size_t sizeRequired, bool block) {
    if (!max) return true;
    Waitable::ScopedLock l(lock);
    current += sizeRequired;
    if (block) {
        Waitable::ScopedWait w(lock);
        while (current > max) 
            lock.wait();
    }
    return current <= max;
}

void Bounds::reduce(size_t size) {
    if (!max || size == 0) return;
    Waitable::ScopedLock l(lock);
    if (current == 0) return;
    current -= std::min(size, current);
    if (current < max && lock.hasWaiters()) {
        assert(lock.hasWaiters() == 1);
        lock.notify();
    }
}

size_t Bounds::getCurrentSize() {
    Waitable::ScopedLock l(lock);
    return current;
}

std::ostream& operator<<(std::ostream& out, const Bounds& bounds) {
    out << "current=" << bounds.current << ", max=" << bounds.max << " [" << &bounds << "]";
    return out;
}

void Bounds::setException(const sys::ExceptionHolder& e) {
    Waitable::ScopedLock l(lock);    
    lock.setException(e);
    lock.waitWaiters();         // Wait for waiting threads to exit.
}

}} // namespace qpid::client
