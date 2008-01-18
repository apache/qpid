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

#include <qpid/RefCounted.h>
#include <boost/iterator/iterator_facade.hpp>
#include <boost/cast.hpp>
#include <assert.h>

namespace qpid {

template <class T, int N> class IList;

/**
 * Base class providing links for a node in an IList.
 * 
 * Derived classes can inheirt multiple IListNode bases provided each
 * has a unique value for N. This allows objects to be linked in
 * multiple independent lists. For example:
 *
 * @code
 * class Foo : public IListNode<0>, public IListNode<1> {};
 * IList<Foo, 0> - uses the 0 links
 * IList<Foo, 1> - uses the 1 links
 *@endcode
 *
 *@param T The derived class.
 *@param N ID for multiple inheritance.
 */
template<class T, int N=0> class IListNode  : public virtual RefCounted {
    intrusive_ptr<IListNode> prev;
    intrusive_ptr<IListNode> next;
    void insert(IListNode*);    
    void erase();
    virtual T* self() const {   // Virutal so anchor can hide.
        return boost::polymorphic_downcast<T*>(const_cast<IListNode*>(this));
    }
  friend class IList<T,N>;
  public:
    IListNode(IListNode* p=0) : prev(p), next(p) {}
    T* getNext() { return next ? next->self() : 0; }
    T* getPrev() { return prev ? prev->self() : 0; }
    const T* getNext() const { return next ? next->self() : 0; }
    const T* getPrev() const { return prev ? prev->self() : 0; }
};

/**
 * Intrusive doubly linked list of reference counted nodes.
 * 
 *@param T must inherit from IListNode<N>
 *@param N Identifies which links to use. @see IListNode.
 */
template <class T, int N=0> class IList {
  private:
    typedef IListNode<T,N> Node;

    template <class R>
    class Iterator :
        public boost::iterator_facade<Iterator<R>,  R, boost::bidirectional_traversal_tag>
    {
        Node* ptr;                 // Iterators do not own their pointees.
        void increment() { assert(ptr); ptr = ptr->next.get(); }
        void decrement() { assert(ptr); ptr = ptr->prev.get(); }
        R& dereference() const { assert(ptr); return *boost::polymorphic_downcast<R*>(ptr); }
        bool equal(const Iterator<R>& x) const { return ptr == x.ptr; }

        Node* cast(const Node* p) { return const_cast<Node*>(p); } 
        explicit Iterator(const Node* p=0) : ptr(cast(p)) {}
        explicit Iterator(const T* p=0) : ptr(cast(p)) {}

      friend class boost::iterator_core_access;
      friend class IList;

      public:
        template <class S> Iterator(const Iterator<S>& x) : ptr(x.ptr) {}
    };

  public:
    IList() {}
    ~IList() { clear(); }
    typedef size_t size_type;
    typedef T value_type;
    /// pointer type is an intrusive_ptr
    typedef intrusive_ptr<T> pointer;
    /// pointer type is an intrusive_ptr
    typedef intrusive_ptr<const T> const_pointer;
    typedef value_type& reference;
    typedef const value_type& const_reference;
    typedef Iterator<value_type> iterator;
    typedef Iterator<const value_type> const_iterator;

    iterator begin() { return iterator(anchor.next.get()); }
    iterator end() { return iterator(&anchor); }
    const_iterator begin() const { return const_iterator(anchor.next.get()); }
    const_iterator end() const { return const_iterator(&anchor); }
    /// Iterator to the last element or end() if empty
    iterator last() { return iterator(anchor.prev.get()); }
    /// Iterator to the last element or end() if empty
    const_iterator last() const { return const_iterator(anchor.prev.get()); }

    /// Note: takes a non-const reference, unlike standard containers.
    void insert(iterator pos, reference x) { x.Node::insert(pos.ptr); }
    void erase(iterator pos) { pos.ptr->erase(); }
    void swap(IList &x) { anchor.swap(x.anchor); }

    reference back() { assert(!empty()); return *last(); }
    const_reference back() const { assert(!empty()); return *last(); }
    void pop_back() { assert(!empty()); erase(last()); }
    /// Note: takes a non-const reference, unlike standard containers.
    void push_back(reference x) { insert(end(), x); }

    reference front() { assert(!empty()); return *begin(); }
    const_reference front() const { assert(!empty()); return *begin(); }
    void pop_front() { assert(!empty()); erase(begin()); }
    /// Note: takes a non-const reference, unlike standard containers.
    void push_front(reference x) { insert(begin(), x); }

    bool empty() const { return begin() == end(); }
    void clear() { while (!empty()) { pop_front(); } }

    size_type size() const {
        size_type s=0;
        for (const_iterator i = begin(); i != end(); ++i) ++s;
        return s;
    }

    iterator erase(iterator from, iterator to) {
        while(from != to) erase(from);
    }

  private:
    // The list is circular and always contains an anchor node.
    // end() points to the anchor, anchor.next is the first
    // iterator, anchor.prev is the last iterator.

    struct Anchor : public Node {
        Anchor() : Node(this) {}
        // Suppress refcounting for the anchor node.
        void release() const {}
        // Hide anchor from public get functions.
        T* self() const { return 0; }
    } anchor;          
};

template <class T, int N>
void IListNode<T,N>::insert(IListNode* node) {
    assert(!next && !prev);     // Not already in a list.
    assert(node);
    assert(node->next && node->prev);
    next=node;
    prev=node->prev;
    prev->next = this;
    next->prev = this;
}

template <class T, int N>
void IListNode<T,N>::erase() {
    assert(prev && next); 
    intrusive_ptr<IListNode> save(this); // Protect till exit.
    prev->next = next;
    next->prev = prev;
    prev = next = 0;
}

} // namespace qpid

#endif  /*!QPID_ILIST_H*/
