#ifndef _sys_Module_h
#define _sys_Module_h

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
#include <boost/noncopyable.hpp>
#include <iostream>
#include <QpidError.h>

namespace qpid {
namespace sys {
#if USE_APR
#include <apr_dso.h>
    typedef apr_dso_handle_t* dso_handle_t;
#else 
    typedef void* dso_handle_t;
#endif

    template <class T> class Module : private boost::noncopyable
    {
        typedef T* create_t();
        typedef void destroy_t(T*);
        
        dso_handle_t handle;
        destroy_t* destroy;
        T* ptr;

        void load(const std::string& name);
        void unload();
        void* getSymbol(const std::string& name);

    public:
        Module(const std::string& name);
        T* operator->(); 
        T* get(); 
        ~Module() throw();
    };

}
}

using namespace qpid::sys;

template <class T> Module<T>::Module(const std::string& module) : destroy(0), ptr(0) 
{
    load(module);
    //TODO: need a better strategy for symbol names to allow multiple
    //modules to be loaded without clashes...

    //Note: need the double cast to avoid errors in casting from void* to function pointer with -pedantic
    create_t* create = reinterpret_cast<create_t*>(reinterpret_cast<intptr_t>(getSymbol("create")));
    destroy = reinterpret_cast<destroy_t*>(reinterpret_cast<intptr_t>(getSymbol("destroy")));
    ptr = create();
}

template <class T> T* Module<T>::operator->() 
{ 
    return ptr; 
}

template <class T> T* Module<T>::get() 
{ 
    return ptr; 
}

template <class T> Module<T>::~Module() throw()
{
    try {
        if (handle && ptr) {
            destroy(ptr);
        }
        if (handle) unload();
    } catch (std::exception& e) {
        std::cout << "Error while destroying module: " << e.what() << std::endl;
    }
    destroy = 0;
    handle = 0;
    ptr = 0;
}

// APR ================================================================
#if USE_APR

#include <apr/APRBase.h>
#include <apr/APRPool.h>

template <class T> void Module<T>::load(const std::string& name)
{
    apr_pool_t* pool = APRPool::get();
    CHECK_APR_SUCCESS(apr_dso_load(&handle, name.c_str(), pool));
    APRPool::free(pool);
}

template <class T> void Module<T>::unload()
{
    CHECK_APR_SUCCESS(apr_dso_unload(handle));
}

template <class T> void* Module<T>::getSymbol(const std::string& name)
{
    apr_dso_handle_sym_t symbol;
    CHECK_APR_SUCCESS(apr_dso_sym(&symbol, handle, name.c_str()));
    return (void*) symbol;
}

// POSIX================================================================
#else 

#include <dlfcn.h>

template <class T> void Module<T>::load(const std::string& name)
{
    dlerror();
    handle = dlopen(name.c_str(), RTLD_NOW);
    const char* error = dlerror();
    if (error) {
        THROW_QPID_ERROR(INTERNAL_ERROR, error);
    }
}

template <class T> void Module<T>::unload()
{
    dlerror();
    dlclose(handle);
    const char* error = dlerror();
    if (error) {
        THROW_QPID_ERROR(INTERNAL_ERROR, error);
    }
}

template <class T> void* Module<T>::getSymbol(const std::string& name)
{
    dlerror();
    void* sym = dlsym(handle, name.c_str());
    const char* error = dlerror();
    if (error) {
        THROW_QPID_ERROR(INTERNAL_ERROR, error);
    }
    return sym;
}

#endif //if USE_APR

#endif //ifndef _sys_Module_h

