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
