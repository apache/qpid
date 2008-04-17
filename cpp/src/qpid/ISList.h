#ifndef QPID_ISLIST_H
#define QPID_ISLIST_H

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
#include <boost/noncopyable.hpp>
#include "pointer_to_other.h"

namespace qpid {

template <class Pointer> struct Pointee {
    typedef typename Pointer::element_type type;
};

template <class T> struct Pointee<T*> {
    typedef T type;
};

template <class> class ISList;
template <class> class IList;

/** Base class for values (nodes) in an ISList.
 *@param raw or smart-pointer type to use for the "next" pointer.
 * Using a smart pointer like shared_ptr or intrusive_ptr 
 * will automate memory management.
 */
template <class Pointer> class ISListNode {
  public:
    typedef Pointer pointer; 
    typedef typename Pointee<Pointer>::type  NodeType;
    typedef typename pointer_to_other<Pointer, const NodeType>::type const_pointer;

    pointer getNext() { return next; }
    pointer * getNextPtr() { return & next; }
    const_pointer getNext() const { return next; }
    
  protected:
    ISListNode() : next() {}
    ISListNode(const ISListNode&) {} // Don't copy the next pointer.


  private:
    pointer next;
  friend class ISList<NodeType>;
};


/**
 * Intrusive singly-linked list.
 * 
 * Provides forward iterator, constant time insertion and constant
 * time pop_front (but not pop_back) so makes a good queue
 * implementation.
 *
 * Unlike standard containers insert(), push_front() and push_back()
 * take const pointer& rather than const value_type&.
 *
 * Iterators can be converted to pointers.
 *
 * Noncopyable - intrusively linked nodes cannot be shared.
 * 
 * @param Node value type for the list, must derive from ISListNode<T>.
 */
template <class Node> class ISList : private boost::noncopyable {
    template <class> class Iterator;
  public:
    typedef Node value_type;
    typedef typename Node::pointer pointer;
    typedef typename Node::const_pointer const_pointer;
    typedef value_type& reference;
    typedef const value_type& const_reference;
    typedef size_t size_type;
    typedef ptrdiff_t difference_type;
    typedef Iterator<value_type> iterator;
    typedef Iterator<const value_type> const_iterator;

    ISList() : first(pointer()), end_(&first) {}

    iterator begin() { return iterator(&first); }
    const_iterator begin() const { return const_iterator(&first); }
    iterator end() { return end_; }
    const_iterator end() const { return end_; }

    bool empty() const { return begin() == end(); }

    size_type size() const {
        int s = 0;
        for (const_iterator i=begin(); i != end(); ++i)
            ++s;
        return s;
    }
    
    void swap(ISList &x) { swap(first, x.first); swap(end_, x.end_); }

    /** Unlike standard containers, insert takes a const pointer&, not a
     * const value_type&. The value is not copied, only linked into the list.
     */
    iterator insert(iterator i, const pointer& p) {
        p->next = *(i.pptr);
        *(i.pptr) = p;
        if (i==end_) ++end_;
        return i;
    }
    
    void erase(iterator i) {
        if (&i->next == end_.pptr)
            end_ = i;
        *(i.pptr) = (**(i.pptr)).next;
    }

    void erase(iterator i, iterator j) { while(i != j) erase(i); }
    void clear() { while (!empty()) { erase(begin()); } }

    reference front() { return *begin(); }
    const_reference front() const { return *begin(); }
    void pop_front() { erase(begin()); }
    void push_front(pointer x) { insert(begin(), x); }

    void push_back(pointer x) { insert(end(), x); }

  private:
    template <class T>
    class Iterator : public boost::iterator_facade <
      Iterator<T>, T, boost::forward_traversal_tag>
    {
      public:
        Iterator() {};

        template <class U> Iterator(
            const Iterator<U>& i,
            typename boost::enable_if_convertible<U*, T*>::type* = 0
        ) : pptr(i.pptr) {}

        operator pointer() { return *pptr; }
        operator const_pointer() const { return *pptr; }
        pointer* pptr;
        
      private:
      friend class boost::iterator_core_access;

        Iterator(const pointer* pp) : pptr(const_cast<pointer*>(pp)) {};

        T& dereference() const { return **pptr; }
        void increment() { pptr = (**pptr).getNextPtr(); }
        bool equal(const Iterator& x) const { return pptr == x.pptr; }


      friend class ISList<Node>;
    };

  private:
    pointer first;
    iterator end_;
};

} // namespace qpid

#endif  /*!QPID_ISLIST_H*/
