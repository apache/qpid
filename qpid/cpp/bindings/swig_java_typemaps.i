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

/* Module code used by some of the wrapper classes */

%pragma(java) moduleimports=%{
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
%}

%pragma(java) modulecode=%{
  /** Checks if the buffer passed is a direct buffer
   *  This method could also convert a non direct buffer into a direct buffer.
   *  However the extra copying deafeats the purpose of the binding.
   */
  static protected java.nio.ByteBuffer isBufferDirect(java.nio.ByteBuffer buff)
  {
        if (buff.isDirect())
        {
            return buff;
        }
        else
        {
          throw new RuntimeException("The ByteBuffer passed is not allocated direct");
        }
  }

  /** We don't support setting maps into C++ atm, but adding here to get around swig **/
  static VaraintMapWrapper getVariantMap(final Map<String,Object> map)
  {
      return new VaraintMapWrapper();
  }

  static Map<String, Object> getJavaMap(final VaraintMapWrapper map)
  {
        return new Map<String, Object>()
        {
            @Override
            public int size()
            {
                return map.size();
            }

            @Override
            public boolean isEmpty()
            {
                return map.isEmpty();
            }

            @Override
            public boolean containsKey(Object key)
            {
                return map.containsKey((String)key);
            }

            @Override
            public boolean containsValue(Object value)
            {
                return map.containsValue(value);
            }

            @Override
            public Object get(Object key)
            {
                return map.get((String)key);
            }

            @Override
            public Object put(String key, Object value)
            {
                throw new UnsupportedOperationException("This map is read-only");
            }

            @Override
            public Object remove(Object key)
            {
                throw new UnsupportedOperationException("This map is read-only");
            }

            @Override
            public void putAll(Map<? extends String, ? extends Object> m)
            {
                throw new UnsupportedOperationException("This map is read-only");
            }

            @Override
            public void clear()
            {
                throw new UnsupportedOperationException("This map is read-only");
            }

            @Override
            public Set<String> keySet()
            {
                Set<String> keys = new HashSet<String>();
                for (String key:(String[])map.keys())
                {
                    keys.add(key);
                }

                return keys;
            }

            @Override
            public Collection<Object> values()
            {
                return Arrays.asList(map.values());
            }

            @Override
            public Set<Entry<String, Object>> entrySet()
            {
                Set<Entry<String, Object>> entries = new HashSet<Entry<String, Object>>();
                for (final String key: keySet())
                {
                    final Object value = map.get(key);
                    entries.add(new Entry<String, Object>()
                    {
                        @Override
                        public String getKey()
                        {
                            return key;
                        }

                        @Override
                        public Object getValue()
                        {
                            return value;
                        }

                        @Override
                        public Object setValue(Object value)
                        {
                            throw new UnsupportedOperationException("This set is read-only");
                        }

                    });
                }
                return entries;
            }

        };
  }
%}


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

%typemap(out) qpid::messaging::Message::BYTE_BUFFER {
  jresult = jenv->NewDirectByteBuffer($1.getStart(), $1.getSize());
}

%typemap(javaout) qpid::messaging::Message::BYTE_BUFFER {
    return $jnicall;
}

%typemap(jni) qpid::types::Variant::Map& "jobject"
%typemap(jtype) qpid::types::Variant::Map& "VaraintMapWrapper"
%typemap(jstype) qpid::types::Variant::Map& "java.util.Map"

%typemap(out) qpid::types::Variant::Map& {
  *(VaraintMapWrapper **)&jresult = new VaraintMapWrapper(jenv,$1);
}

%typemap(javaout) qpid::types::Variant::Map& {
    return $module.getJavaMap($jnicall);
}

%typemap(in) (qpid::types::Variant::Map&){
  $1 = new qpid::types::Variant::Map();
}

%typemap(javain) (qpid::types::Variant::Map&) "$module.getVariantMap($javainput)"

%typemap(in) uint8_t {
    $1 = (uint8_t)$input;
}

%typemap(out) uint8_t {
    $result = (jbyte)$1;
}

%typemap(javaout) uint8_t {
    return $jnicall;
}

%typemap(javain) uint8_t "$javainput"


%typemap(in) uint32_t {
    $1 = (uint32_t)$input;
}

%typemap(out) uint32_t {
    $result = (jint)$1;
}

%typemap(javaout) uint32_t {
    return $jnicall;
}

%typemap(javain) uint32_t "$javainput"

%typemap(in) uint64_t {
    $1 = (uint64_t)$input;
}

%typemap(out) uint64_t {
    $result = (jlong)$1;
}

%typemap(javaout) uint64_t {
    return $jnicall;
}

%typemap(javain) uint64_t "$javainput"

%typemap(jni) uint8_t "jbyte"
%typemap(jtype) uint8_t "byte"
%typemap(jstype) uint8_t "byte"

%typemap(jni) uint32_t "jint"
%typemap(jtype) uint32_t "int"
%typemap(jstype) uint32_t "int"

%typemap(jni) uint64_t "jlong"
%typemap(jtype) uint64_t "long"
%typemap(jstype) uint64_t "long"

