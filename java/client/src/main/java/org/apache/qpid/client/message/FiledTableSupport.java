package org.apache.qpid.client.message;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;

public class FiledTableSupport
{
  public static FieldTable convertToFieldTable(Map<String,?> props)
  {
      FieldTable ft = new FieldTable();
      if (props != null)
      {
          for (String key : props.keySet())
          {
              ft.setObject(key, props.get(key));
          }
      }
      return ft;
  }

  public static Map<String,Object> convertToMap(FieldTable ft)
  {
     Map<String,Object> map = new HashMap<String,Object>();
     for (AMQShortString key: ft.keySet() )
     {
         map.put(key.asString(), ft.getObject(key));
     }

     return map;
  }
}
