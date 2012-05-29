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

/* These 3 typemaps tell SWIG what JNI and Java types to use */
%typemap(jni) qpid::messaging::Message::BYTE_BUFFER "jobject"
%typemap(jtype) qpid::messaging::Message::BYTE_BUFFER "java.nio.ByteBuffer"
%typemap(jstype) qpid::messaging::Message::BYTE_BUFFER "java.nio.ByteBuffer"

%typemap(in) (qpid::messaging::Message::BYTE_BUFFER) {
  void* start = jenv->GetDirectBufferAddress($input);
  long size = (long)(jenv->GetDirectBufferCapacity($input));
  $1 = qpid::messaging::Message::BYTE_BUFFER(start,size);
}

%typemap(javain) (qpid::messaging::Message::BYTE_BUFFER) "$module.isBufferDirect($javainput)"

%pragma(java) modulecode=%{
  /** Checks if the buffer passed is a direct buffer 
   *  This method could also convert a non direct buffer into a direct buffer.
   *  However the extra copying deafeats the purpose of the binding.
   */
  static protected java.nio.ByteBuffer isBufferDirect(java.nio.ByteBuffer buff) {
    if (buff.isDirect())
    {
        return buff;  
    }
    else
    {
      throw new RuntimeException("The ByteBuffer passed is not allocated direct");
    }
  }
%}

%typemap(out) qpid::messaging::Message::BYTE_BUFFER {
  jresult = jenv->NewDirectByteBuffer($1.getStart(), $1.getSize()); 
}

%typemap(javaout) qpid::messaging::Message::BYTE_BUFFER {
    return $jnicall;
}

%typemap(jni) qpid::types::Variant::Map& "jobject"
%typemap(jtype) qpid::types::Variant::Map& "jmap"
%typemap(jstype) qpid::types::Variant::Map& "jmap"

%typemap(out) qpid::types::Variant::Map& {
  *(jmap **)&jresult = new jmap(jenv,$1);
}

%typemap(javaout) qpid::types::Variant::Map& {
    return $jnicall;
}

%typemap(in) (qpid::types::Variant::Map&){
  $1 = NULL;
}

%pragma(java) modulecode=%{
  /** temp hack to get the code compiling.
   *  We are currently not using any of the methods
   *  That require us to set a map.
   */
  static protected jmap convertToJMap(Object map) {
    return null;
  }
%}

%typemap(javain) (qpid::types::Variant::Map&) "$module.convertToJMap($javainput)"

