#ifndef QPID_BOUNDEDITERATOR_H
#define QPID_BOUNDEDITERATOR_H

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
#include <boost/iterator/iterator_adaptor.hpp>
#include "qpid/Exception.h"

namespace qpid {

struct OutOfBoundsException : public Exception {};
    

/**
 * Iterator wrapper that throws an exception if iterated beyound the bounds.
 */
template <class Iter>
class BoundedIterator :
        public boost::iterator_adaptor<BoundedIterator<Iter>, Iter>
{
  public:
    BoundedIterator(const Iter& begin, const Iter& end_) :
        BoundedIterator::iterator_adaptor_(begin), end(end_) {}

    operator Iter() const { return this->base_reference(); }
    
  private:
    typename boost::iterator_reference<Iter>::type dereference() const {
        if (this->base_reference() == end) throw OutOfBoundsException();
        return *(this->base_reference());
    }
    
    void increment() {
        if (this->base_reference() == end) throw OutOfBoundsException();
        ++this->base_reference();
    }

    /** Advance requires Iter to be comparable */
    void advance(typename boost::iterator_difference<Iter>::type n) {
        this->base_reference() += n;
        if (this->base_reference() > end) throw OutOfBoundsException();
    }
    
    Iter end;
    friend class boost::iterator_core_access;
};
} // namespace qpid

#endif  /*!QPID_BOUNDEDITERATOR_H*/
