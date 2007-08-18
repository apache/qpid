#ifndef QPID_FRAMING_BLOB_H
#define QPID_FRAMING_BLOB_H

/*
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

#include <boost/aligned_storage.hpp>
#include <boost/checked_delete.hpp>
#include <boost/utility/typed_in_place_factory.hpp>

#include <new>

#include <assert.h>


namespace boost {

/**
 * 0-arg typed_in_place_factory constructor and in_place() override.
 * 
 * Boost doesn't provide the 0 arg version since it assumes
 * in_place_factory will be used when there is no default ctor.
 */
template <class T>
class typed_in_place_factory0 : public typed_in_place_factory_base {
  public:
    typedef T value_type ; 
    void apply ( void* address ) const { new (address) T(); }
};

template<class T>
typed_in_place_factory0<T> in_place() { return typed_in_place_factory0<T>(); }

} // namespace boost


namespace qpid {
namespace framing {

using boost::in_place;

template <class T> struct BlobHelper {
    static void destroy(void* ptr) { static_cast<T*>(ptr)->~T(); }
    static void copy(void* dest, const void* src) {
        new (dest) T(*static_cast<const T*>(src));
    }
};

template <> struct BlobHelper<void> {
    static void destroy(void*);
    static void copy(void* to, const void* from);
};

/**
 * A "blob" is a chunk of memory which can contain a single object at
 * a time arbitrary type, provided sizeof(T)<=blob.size(). Blob
 * ensures proper construction and destruction of its contents,
 * and proper copying between Blobs, but nothing else.
 * 
 * In particular the user must ensure the blob is big enough for its
 * contents and must know the type of object in the blob to cast get().
 *
 * Objects can be allocated directly in place using
 * construct(in_place<T>(...))  or copied using operator=.
 * Constructing a new object in the blob destroys the old one.
 */
template <size_t Size>
class Blob
{
    boost::aligned_storage<Size> store;
    void (*destroy)(void*);
    void (*copy)(void*, const void*);

    template <class T> void setType() {
        destroy=&BlobHelper<T>::destroy;
        copy=&BlobHelper<T>::copy;
    }
    
    template<class TypedInPlaceFactory>
    void construct (const TypedInPlaceFactory& factory,
                    const boost::typed_in_place_factory_base* )
    {
        assert(empty());
        typedef typename TypedInPlaceFactory::value_type T;
        assert(sizeof(T) <= Size);
        factory.apply(store.address());
        setType<T>();
    }

    void assign(const Blob& b) {
        assert(empty());
        b.copy(this->get(), b.get());
        copy = b.copy;
        destroy = b.destroy;
    }
    
  public:
    /** Construct an empty blob. */
    Blob() { setType<void>(); }

    /** Copy a blob. */
    Blob(const Blob& b) { setType<void>(); assign(b); }

    /** @see construct() */
    template<class Expr>
    Blob( const Expr & expr ) { setType<void>(); construct(expr,&expr); }

    ~Blob() { clear(); }

    /** Assign a blob */
    Blob& operator=(const Blob& b) {
        clear();
        assign(b);
        return *this;
    }

    /** Construcct an object in the blob. Destroyes the previous object.
     *@param expr an expresion of the form: in_place<T>(x,y,z)
     * will construct an object using the constructor T(x,y,z)
     */
    template<class Expr> void
    construct(const Expr& expr) { clear(); construct(expr,&expr); }

    /** Copy construct an instance of T into the Blob. */
    template<class T>
    Blob& operator=(const T& x) { clear(); construct(in_place<T>(x)); return *this; }
    
    /** Get pointer to blob contents. Caller must know how to cast it. */
    void* get() { return store.address(); }

    /** Get const pointer to blob contents */
    const void* get() const { return empty() ? 0 : store.address(); }
    
    /** Destroy the object in the blob making it empty. */
    void clear() {
        void (*oldDestroy)(void*) = destroy; 
        setType<void>();
        oldDestroy(store.address());
    }

    bool empty() const { return destroy == BlobHelper<void>::destroy; }
    
    static size_t size() { return Size; }
};


}} // namespace qpid::framing



#endif  /*!QPID_FRAMING_BLOB_H*/
