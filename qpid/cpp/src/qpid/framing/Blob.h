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

#include <boost/static_assert.hpp>
#include <boost/aligned_storage.hpp>
#include <boost/checked_delete.hpp>
#include <boost/utility/typed_in_place_factory.hpp>
#include <boost/type_traits/is_base_and_derived.hpp>
#include <boost/utility/enable_if.hpp>
#include <boost/version.hpp>

#include <new>

#include <assert.h>


namespace qpid {
namespace framing {

using boost::in_place;          
using boost::typed_in_place_factory_base;

/** 0-arg typed_in_place_factory, missing in pre-1.35 boost. */
#if (BOOST_VERSION < 103500)
template <class T>
struct typed_in_place_factory0 : public typed_in_place_factory_base {
    typedef T value_type ; 
    void apply ( void* address ) const { new (address) T(); }
};

/** 0-arg in_place<T>() function, missing from boost. */
template<class T>
typed_in_place_factory0<T> in_place() { return typed_in_place_factory0<T>(); }
#endif

template <class T, class R=void>
struct EnableInPlace
    : public boost::enable_if<boost::is_base_and_derived<
                                  typed_in_place_factory_base, T>,
                              R>
{};
       
template <class T, class R=void>
struct DisableInPlace
    : public boost::disable_if<boost::is_base_and_derived<
                                   typed_in_place_factory_base, T>,
                               R>
{};
       
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
 * A Blob is a chunk of memory which can contain a single object at
 * a time-arbitrary type, provided sizeof(T)<=blob.size(). Using Blobs
 * ensures proper construction and destruction of its contents,
 * and proper copying between Blobs, but nothing else.
 * 
 * In particular you must ensure that the Blob is big enough for its
 * contents and must know the type of object in the Blob to cast get().
 *
 * If BaseType is specified then only an object that can be
 * static_cast to BaseType may be stored in the Blob.
 */
template <size_t Size, class BaseType=void>
class Blob
{
    boost::aligned_storage<Size> store;
    BaseType* basePtr;
    
    void (*destroy)(void*);
    void (*copy)(void*, const void*);

    template <class T>void setType() {
        BOOST_STATIC_ASSERT(sizeof(T) <= Size);
        destroy=&BlobHelper<T>::destroy;
        copy=&BlobHelper<T>::copy;
        // Base pointer may be offeset from store.address()
        basePtr = reinterpret_cast<T*>(store.address());
    }
        
    void initialize() {
        destroy=&BlobHelper<void>::destroy;
        copy=&BlobHelper<void>::copy;
        basePtr=0;
    }

    template<class Factory>
    typename EnableInPlace<Factory>::type apply(const Factory& factory)
    {
        typedef typename Factory::value_type T;
        assert(empty());
        factory.apply(store.address());
        setType<T>();
    }

    void assign(const Blob& b) {
        assert(empty());
        if (b.empty()) return;
        b.copy(this->store.address(), b.store.address());
        copy = b.copy;
        destroy = b.destroy;
        basePtr = reinterpret_cast<BaseType*>(
            ((char*)this)+ ((const char*)(b.basePtr) - (const char*)(&b)));
    }

  public:
    /** Construct an empty Blob. */
    Blob() { initialize(); }

    /** Copy a Blob. */
    Blob(const Blob& b) { initialize(); assign(b); }

    /** Construct from in_place constructor. */
    template<class InPlace>
    Blob(const InPlace & expr, typename EnableInPlace<InPlace>::type* =0) {
        initialize(); apply(expr);
    }

    /** Construct by copying an object constructor. */
    template<class T>
    Blob(const T & t, typename DisableInPlace<T>::type* =0) {
        initialize(); apply(in_place<T>(t));
    }

    ~Blob() { clear(); }

    /** Assign from another Blob. */
    Blob& operator=(const Blob& b) {
        clear();
        assign(b);
        return *this;
    }

    /** Assign from an in_place constructor expression. */
    template<class InPlace>
    typename EnableInPlace<InPlace,Blob&>::type operator=(const InPlace& expr) {
        clear(); apply(expr); return *this;
    }

    /** Assign from an object of type T. */
    template <class T>
    typename DisableInPlace<T, Blob&>::type operator=(const T& x) {
        clear(); apply(in_place<T>(x)); return *this;
    }

    /** Get pointer to Blob contents, returns 0 if empty. */
    BaseType* get() { return  basePtr; }

    /** Get pointer to Blob contents, returns 0 if empty. */
    const BaseType* get() const { return basePtr; }

    /** Destroy the object in the Blob making it empty. */
    void clear() {
        void (*oldDestroy)(void*) = destroy; 
        initialize();
        oldDestroy(store.address());
    }

    bool empty() const { return destroy==BlobHelper<void>::destroy; }
    
    static size_t size() { return Size; }
};

}} // namespace qpid::framing


#endif  /*!QPID_FRAMING_BLOB_H*/
