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
 */

%wrapper %{

#include <stdarg.h>

    VALUE MapToRb(const qpid::types::Variant::Map*);
    VALUE ListToRb(const qpid::types::Variant::List*);
    void RbToMap(VALUE, qpid::types::Variant::Map*);
    void RbToList(VALUE, qpid::types::Variant::List*);

    qpid::types::Variant RbToVariant(VALUE value) {
        switch (TYPE(value)) {
        case T_FLOAT:   return qpid::types::Variant(NUM2DBL(value));
        case T_STRING:  return qpid::types::Variant(StringValuePtr(value));
        case T_FIXNUM:  return qpid::types::Variant((int64_t) FIX2LONG(value));
        case T_BIGNUM:  return qpid::types::Variant((int64_t) NUM2LL(value));
        case T_TRUE:    return qpid::types::Variant(true);
        case T_FALSE:   return qpid::types::Variant(false);
        case T_HASH: {
            qpid::types::Variant::Map map;
            RbToMap(value, &map);
            return qpid::types::Variant(map);
        }
        case T_ARRAY: {
            qpid::types::Variant::List list;
            RbToList(value, &list);
            return qpid::types::Variant(list);
        }
        default: return qpid::types::Variant();
        }
    }

    VALUE VariantToRb(const qpid::types::Variant* v) {
        VALUE result = Qnil;
        try {
            switch (v->getType()) {
            case qpid::types::VAR_VOID: {
                result = Qnil;
                break;
            }
            case qpid::types::VAR_BOOL : {
                result = v->asBool() ? Qtrue : Qfalse;
                break;
            }
            case qpid::types::VAR_UINT8 :
            case qpid::types::VAR_UINT16 :
            case qpid::types::VAR_UINT32 : {
                result = UINT2NUM(v->asUint32());
                break;
            }
            case qpid::types::VAR_UINT64 : {
                result = ULL2NUM(v->asUint64());
                break;
            }
            case qpid::types::VAR_INT8 : 
            case qpid::types::VAR_INT16 :
            case qpid::types::VAR_INT32 : {
                result = INT2NUM(v->asInt32());
                break;
            }
            case qpid::types::VAR_INT64 : {
                result = LL2NUM(v->asInt64());
                break;
            }
            case qpid::types::VAR_FLOAT : {
                result = rb_float_new((double) v->asFloat());
                break;
            }
            case qpid::types::VAR_DOUBLE : {
                result = rb_float_new(v->asDouble());
                break;
            }
            case qpid::types::VAR_STRING : {
                const std::string val(v->asString());
                result = rb_str_new(val.c_str(), val.size());
                break;
            }
            case qpid::types::VAR_MAP : {
                result = MapToRb(&(v->asMap()));
                break;
            }
            case qpid::types::VAR_LIST : {
                result = ListToRb(&(v->asList()));
                break;
            }
            case qpid::types::VAR_UUID : {
            }
            }
        } catch (qpid::types::Exception& ex) {
            static VALUE error = rb_define_class("Error", rb_eStandardError);
            rb_raise(error, "%s", ex.what());
        }

        return result;
    }

    VALUE MapToRb(const qpid::types::Variant::Map* map) {
        VALUE result = rb_hash_new();
        qpid::types::Variant::Map::const_iterator iter;
        for (iter = map->begin(); iter != map->end(); iter++) {
            const std::string key(iter->first);
            VALUE rbval = VariantToRb(&(iter->second));
            rb_hash_aset(result, rb_str_new(key.c_str(), key.size()), rbval);
        }
        return result;
    }

    VALUE ListToRb(const qpid::types::Variant::List* list) {
        VALUE result = rb_ary_new2(list->size());
        qpid::types::Variant::List::const_iterator iter;
        for (iter = list->begin(); iter != list->end(); iter++) {
            VALUE rbval = VariantToRb(&(*iter));
            rb_ary_push(result, rbval);
        }
        return result;
    }

    VALUE HashIter(VALUE data_ary, VALUE context) {
        VALUE key = rb_ary_entry(data_ary, 0);
        VALUE val = rb_ary_entry(data_ary, 1);
        qpid::types::Variant::Map* map((qpid::types::Variant::Map*) context);
        (*map)[std::string(StringValuePtr(key))] = RbToVariant(val);
        return data_ary;
    }

    VALUE AryIter(VALUE data, VALUE context) {
        qpid::types::Variant::List* list((qpid::types::Variant::List*) context);
        list->push_back(RbToVariant(data));
        return data;
    }

    void RbToMap(VALUE hash, qpid::types::Variant::Map* map) {
        map->clear();
        rb_iterate(rb_each, hash, (VALUE(*)(ANYARGS))HashIter, (VALUE) map);
    }

    void RbToList(VALUE ary, qpid::types::Variant::List* list) {
        list->clear();
        rb_iterate(rb_each, ary, (VALUE(*)(ANYARGS))AryIter, (VALUE) list);
    }
%}

%typemap (in) void *
{
    $1 = (void *) $input;
}

%typemap (out) void *
{
    $result = (VALUE) $1;
}

%typemap (in) uint8_t
{
  $1 = NUM2UINT ($input);
}

%typemap (out) uint8_t
{
  $result = UINT2NUM((uint8_t) $1);
}

%typemap (in) int8_t
{
  $1 = NUM2INT ($input);
}

%typemap (out) int8_t
{
  $result = INT2NUM((int8_t) $1);
}

%typemap (in) uint16_t
{
    $1 = NUM2UINT ($input);
}

%typemap (out) uint16_t
{
    $result = UINT2NUM((uint16_t) $1);
}

%typemap (in) uint32_t
{
    if (TYPE($input) == T_BIGNUM)
        $1 = NUM2UINT($input);
    else
        $1 = FIX2UINT($input);
}

%typemap (out) uint32_t
{
    $result = UINT2NUM((uint32_t) $1);
}

%typemap (in) int32_t
{
    if (TYPE($input) == T_BIGNUM)
        $1 = NUM2INT($input);
    else
        $1 = FIX2INT($input);
}

%typemap (out) int32_t
{
    $result = INT2NUM((int32_t) $1);
}

%typemap (typecheck, precedence=SWIG_TYPECHECK_INTEGER) uint32_t {
   $1 = FIXNUM_P($input);
}

%typemap (in) uint64_t
{
    if (TYPE($input) == T_BIGNUM)
        $1 = NUM2ULL($input);
    else
        $1 = (uint64_t) FIX2ULONG($input);
}

%typemap (out) uint64_t
{
    $result = ULL2NUM((uint64_t) $1);
}

%typemap (in) int64_t
{
    if (TYPE($input) == T_BIGNUM)
        $1 = NUM2LL($input);
    else
        $1 = (int64_t) FIX2LONG($input);
}

%typemap (out) int64_t
{
    $result = LL2NUM((int64_t) $1);
}

/*
 * Variant types: C++ --> Ruby
 */
%typemap(out) qpid::types::Variant::Map {
    $result = MapToRb(&$1);
}

%typemap(out) qpid::types::Variant::Map& {
    $result = MapToRb($1);
}

%typemap(out) qpid::types::Variant::List {
    $result = ListToRb(&$1);
}

%typemap(out) qpid::types::Variant::List& {
    $result = ListToRb($1);
}

%typemap(out) qpid::types::Variant& {
    $result = VariantToRb($1);
}


/*
 * Variant types: Ruby --> C++
 */
%typemap(in) qpid::types::Variant& {
    $1 = new qpid::types::Variant(RbToVariant($input));
}

%typemap(in) qpid::types::Variant::Map& {
    $1 = new qpid::types::Variant::Map();
    RbToMap($input, $1);
}

%typemap(in) qpid::types::Variant::List& {
    $1 = new qpid::types::Variant::List();
    RbToList($input, $1);
}

%typemap(in) const qpid::types::Variant::Map const & {
    $1 = new qpid::types::Variant::Map();
    RbToMap($input, $1);
}

%typemap(in) const qpid::types::Variant::List const & {
    $1 = new qpid::types::Variant::List();
    RbToList($input, $1);
}

%typemap(freearg) qpid::types::Variant& {
    delete $1;
}

%typemap(freearg) qpid::types::Variant::Map& {
    delete $1;
}

%typemap(freearg) qpid::types::Variant::List& {
    delete $1;
}


/*
 * Variant types: typecheck maps
 */
%typemap(typecheck) qpid::types::Variant::Map& {
    $1 = (TYPE($input) == T_HASH) ? 1 : 0;
}

%typemap(typecheck) qpid::types::Variant::List& {
    $1 = (TYPE($input) == T_ARRAY) ? 1 : 0;
}

%typemap(typecheck) qpid::types::Variant& {
    $1 = (TYPE($input) == T_FLOAT  ||
          TYPE($input) == T_STRING ||
          TYPE($input) == T_FIXNUM ||
          TYPE($input) == T_BIGNUM ||
          TYPE($input) == T_TRUE   ||
          TYPE($input) == T_FALSE) ? 1 : 0;
}

%typemap(typecheck) qpid::types::Variant::Map const & {
    $1 = (TYPE($input) == T_HASH) ? 1 : 0;
}

%typemap(typecheck) qpid::types::Variant::List const & {
    $1 = (TYPE($input) == T_ARRAY) ? 1 : 0;
}

%typemap(typecheck) const qpid::types::Variant const & {
    $1 = (TYPE($input) == T_FLOAT  ||
          TYPE($input) == T_STRING ||
          TYPE($input) == T_FIXNUM ||
          TYPE($input) == T_BIGNUM ||
          TYPE($input) == T_TRUE   ||
          TYPE($input) == T_FALSE) ? 1 : 0;
}

%typemap(typecheck) bool {
    $1 = (TYPE($input) == T_TRUE ||
          TYPE($input) == T_FALSE) ? 1 : 0;
}



%typemap (typecheck, precedence=SWIG_TYPECHECK_INTEGER) uint64_t {
   $1 = FIXNUM_P($input);
}

