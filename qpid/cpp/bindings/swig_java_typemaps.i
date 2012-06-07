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

/* ======================== C/C+ Header files ======================== */
%begin %{
#include "qpid/types/Variant.h"
#include "jni.h"
#include <iostream>
%}
/* =================================================================== */


/* ======================== swig file includes ======================= */
%include "swig_java_cpp_helper.i"
%include "swig_java_helper.i"
/* =================================================================== */


/*
 * ========================== Type Maps =============================
 * Defines the mapping between the various C++ and Java types
 * ===================================================================
 */


/*
 * -------------------------------------------------------------------
 * The "jni" specfies the jni C types to be used in jni code (c++)
 * The "jtpye" specifies the type mapping between,
 *     the jni native methods and proxy classes (java code).
 * The "jstype" specifies the types exposed in the proxy classes.
 * -------------------------------------------------------------------VaraintMapWrapper.h~
*/
%typemap(jni) qpid::messaging::Message::BYTE_BUFFER "jobject"
%typemap(jtype) qpid::messaging::Message::BYTE_BUFFER "java.nio.ByteBuffer"
%typemap(jstype) qpid::messaging::Message::BYTE_BUFFER "java.nio.ByteBuffer"

%typemap(jni) qpid::types::Variant::Map& "jobject"
%typemap(jtype) qpid::types::Variant::Map& "ReadOnlyVariantMapWrapper"
%typemap(jstype) qpid::types::Variant::Map& "java.util.Map"

%typemap(jni) const qpid::types::Variant& "jobject"
%typemap(jtype) const qpid::types::Variant& "Object"
%typemap(jstype) const qpid::types::Variant& "Object"

%typemap(jni) uint8_t "jbyte"
%typemap(jtype) uint8_t "byte"
%typemap(jstype) uint8_t "byte"

%typemap(jni) uint32_t "jint"
%typemap(jtype) uint32_t "int"
%typemap(jstype) uint32_t "int"

%typemap(jni) uint64_t "jlong"
%typemap(jtype) uint64_t "long"
%typemap(jstype) uint64_t "long"

/* -- qpid::messaging::Message::BYTE_BUFFER -- */
%typemap(in) (qpid::messaging::Message::BYTE_BUFFER) {
  void* start = jenv->GetDirectBufferAddress($input);
  long size = (long)(jenv->GetDirectBufferCapacity($input));
  $1 = qpid::messaging::Message::BYTE_BUFFER(start,size);
}

%typemap(javain) (qpid::messaging::Message::BYTE_BUFFER) "$module.isBufferDirect($javainput)"

%typemap(out) qpid::messaging::Message::BYTE_BUFFER {
  jresult = jenv->NewDirectByteBuffer($1.getStart(), $1.getSize());
}

%typemap(javaout) qpid::messaging::Message::BYTE_BUFFER {
    return $jnicall;
}

/* -- qpid::types::Variant::Map& -- */
%typemap(in) (qpid::types::Variant::Map&){
  $1 = new qpid::types::Variant::Map();
}

%typemap(javain) (qpid::types::Variant::Map&) "$module.getVariantMap($javainput)"

%typemap(out) qpid::types::Variant::Map& {
  *(ReadOnlyVariantMapWrapper **)&jresult = new ReadOnlyVariantMapWrapper(jenv,*$1);
}

%typemap(javaout) qpid::types::Variant::Map& {
    return $module.getJavaMap($jnicall);
}

/* -- qpid::types::Variant& -- */
%typemap(in) (const qpid::types::Variant&) {
  qpid::types::Variant v = convertJavaObjectToVariant(jenv,$input);
  if (v)
  {
      $1 = new qpid::types::Variant(v);
  }
  else
  {
      // There will be an exception on the java side,
      // thrown by convertJavaObjectToVariant method.
      return;
  }
}

%typemap(javain) (const qpid::types::Variant&) "$javainput"


/* -- qpid::types::uint8_t -- */
%typemap(in) uint8_t {
    $1 = (uint8_t)$input;
}

%typemap(javain) uint8_t "$javainput"

%typemap(out) uint8_t {
    $result = (jbyte)$1;
}

%typemap(javaout) uint8_t {
    return $jnicall;
}

/* -- qpid::types::uint32_t -- */
%typemap(in) uint32_t {
    $1 = (uint32_t)$input;
}

%typemap(javain) uint32_t "$javainput"

%typemap(out) uint32_t {
    $result = (jint)$1;
}

%typemap(javaout) uint32_t {
    return $jnicall;
}

/* -- qpid::types::uint64_t -- */
%typemap(in) uint64_t {
    $1 = (uint64_t)$input;
}

%typemap(javain) uint64_t "$javainput"

%typemap(out) uint64_t {
    $result = (jlong)$1;
}

%typemap(javaout) uint64_t {
    return $jnicall;
}
