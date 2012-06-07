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

/*
 * ===================== Java Helper Methods ==========================
 * Defines helper methods in support of typemaps.
 * These methods are placed in the respective module.
 * ===================================================================
 */

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

  static protected void checkVariantMapKey(Object key)
  {
        if (key == null)
        {
            throw new NullPointerException("Key cannot be null");
        }
        if (! (key instanceof String))
        {
            throw new ClassCastException("Key should be of type java.lang.String");
        }
  }

  static long getVariantMap(final Map<String,Object> m)
  {
      WriteOnlyVariantMapWrapper map = new WriteOnlyVariantMapWrapper();
      for (String key: m.keySet())
      {
          map.put(key,m.get(key));
      }
      return WriteOnlyVariantMapWrapper.getCPtr(map);
  }

  static Map<String, Object> getJavaMap(final ReadOnlyVariantMapWrapper map)
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
                checkVariantMapKey(key);
                return map.containsKey((String)key);
            }

            @Override
            public boolean containsValue(Object value)
            {
                throw new UnsupportedOperationException("Unsupported at the native layer for efficiency");
            }

            @Override
            public Object get(Object key)
            {
                checkVariantMapKey(key);
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
                throw new UnsupportedOperationException("Unsupported at the native layer for efficiency");
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
