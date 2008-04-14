#ifndef QPID_ILIST_H
#define QPID_ILIST_H

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

#include "ISList.h"

namespace qpid {

template <class> class IList;

/** Base class for values (nodes) in an IList.
 *@param raw or smart-pointer type to use for the "next" pointer.
 * Using a smart pointer like shared_ptr or intrusive_ptr 
 * will automate memory management.
 */
template <class Pointer> class IListNode {
  public:
    typedef Pointer pointer; 
    typedef typename Pointee<Pointer>::type  NodeType;
    typedef typename pointer_to_other<Pointer, const NodeType>::type const_pointer;
    
  protected:
    IListNode() : prev() {}
    IListNode(const IListNode&) {} // Don't copy next/prev pointers

    pointer getNext() { return next; }
    const_pointer getNext() const { return next; }
    pointer getPrev() { return prev; }
    const_pointer getPrev() const { return prev; }

  private:
    pointer prev, next;
  friend class IList<NodeType>;
};


/**
 * Intrusive doubly-linked list.
 * 
 * Provides bidirectional iterator and constant time insertion
 * at front and back.
 *
 * The list itself performs no memory management. Use a smart pointer
 * as the pointer type (e.g. intrusive_ptr, shared_ptr) for automated
 * memory management.
 *
 * Unlike standard containers insert(), push_front() and push_back()
 * take const pointer& rather than const value_type&.
 *
 * Iterators can be converted to the pointer type.
 *
 * Noncopyable - intrusively linked nodes cannot be shared between
 * lists. Does provide swap()
 * 
 * @param Node value type for the list, must derive from ISListNode<>.
 */
template<class Node> class IList {
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

    IList() : begin_(), last_() {}

    iterator begin() { return begin_; }
    const_iterator begin() const { return begin_; }
    iterator end() { return 0; }
    const_iterator end() const { return 0; }

    bool empty() const { return begin() == end(); }

    size_type size() const {
        int s = 0;
        for (const_iterator i=begin(); i != end(); ++i)
            ++s;
        return s;
    }
    
    void swap(IList &x) { swap(begin_, x.begin_); swap(last_, x.last_); }

    iterator insert(iterator i, const pointer& p) {
        if (empty()) {
            begin_ = last_ = p;
            insert(0, p, 0);
        }
        else if (i) {
            insert(i->prev, p, i);
            if (i == begin_) --begin_;
        }
        else {
            insert(last_, p, 0) ;
            last_ = p;
        }
        return p;
    }

    void erase(iterator i) {
        if (begin_ == last_)
            begin_ = last_ = 0;
        else {
            if (i == begin_) ++begin_;
            else i->prev->next = i->next;
            if (i == last_) --last_;
            else i->next->prev = i->prev;
        }
        i->prev = i->next = pointer(0);
    }

    void erase(iterator i, iterator j) { while(i != j) erase(i); }
    void clear() { while (!empty()) { erase(begin()); } }

    reference front() { return *begin(); }
    const_reference front() const { return *begin(); }
    void push_front(const pointer& p) { insert(begin(), p); }
    void pop_front() { erase(begin()); }

    /** Iterator to the last element in the list. */
    iterator last() { return last_; }
    const_iterator last() const { return last_; }

    reference back() { return *last(); }
    const_reference back() const { return *last(); }
    void push_back(const pointer& p) { insert(end(), p); }
    void pop_back() { erase(last()); }

  private:
    void insert(pointer a, pointer b, pointer c) {
        b->prev = a;
        if (a) a->next = b;
        b->next = c;
        if (c) c->prev = b;
    }
    
    template <class T>
    class Iterator : public boost::iterator_facade<
      Iterator<T>, T, boost::bidirectional_traversal_tag>
    {
      public:
        Iterator() : ptr() {};

        template <class U> Iterator(
            const Iterator<U>& i,
            typename boost::enable_if_convertible<U*, T*>::type* = 0
        ) : ptr(i.ptr) {}

        operator pointer() { return ptr; }
        operator const_pointer() const { return ptr; }
        
      private:
      friend class boost::iterator_core_access;

        Iterator(const_pointer p) : ptr(const_cast<pointer>(p)) {};

        T& dereference() const { return *ptr; }
        void increment() { ptr = ptr->next; }
        void decrement() { ptr = ptr->prev; }
        bool equal(const Iterator& x) const { return ptr == x.ptr; }

        pointer ptr;

      friend class IList<Node>;
    };

    iterator begin_, last_;
};

} // namespace qpid

#endif  /*!QPID_ILIST_H*/
