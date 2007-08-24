package org.apache.qpidity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.qpidity.ToyBroker.Message;

public class ToyExchange
{
  final static String DIRECT = "amq.direct";
  final static String TOPIC = "amq.topic";
  
  private Map<String,List<LinkedBlockingQueue<Message>>> directEx = new HashMap<String,List<LinkedBlockingQueue<Message>>>();
  private Map<String,List<LinkedBlockingQueue<Message>>> topicEx = new HashMap<String,List<LinkedBlockingQueue<Message>>>(); 
  private Map<String,LinkedBlockingQueue<Message>> queues = new HashMap<String,LinkedBlockingQueue<Message>>(); 
  
  public void createQueue(String name)
  {
      queues.put(name, new LinkedBlockingQueue<Message>());
  }
  
  public LinkedBlockingQueue<Message> getQueue(String name)
  {
      return queues.get(name);
  }
  
  public void bindQueue(String type,String binding,String queueName)
  {
      LinkedBlockingQueue<Message> queue = queues.get(queueName);
      binding = normalizeKey(binding);
      if(DIRECT.equals(type))
      {
          
          if (directEx.containsKey(binding))
          {
              List<LinkedBlockingQueue<Message>> list = directEx.get(binding);
              list.add(queue);
          }
          else
          {
              List<LinkedBlockingQueue<Message>> list = new LinkedList<LinkedBlockingQueue<Message>>();
              list.add(queue);
              directEx.put(binding,list);
          }
      }
      else
      {
          if (topicEx.containsKey(binding))
          {
              List<LinkedBlockingQueue<Message>> list = topicEx.get(binding);
              list.add(queue);
          }
          else
          {
              List<LinkedBlockingQueue<Message>> list = new LinkedList<LinkedBlockingQueue<Message>>();
              list.add(queue);
              topicEx.put(binding,list);
          }
      }
  }
  
  public boolean route(String dest,String routingKey,Message msg)
  {
      List<LinkedBlockingQueue<Message>> queues;
      if(DIRECT.equals(dest))
      {
          queues = directEx.get(routingKey);          
      }
      else
      {
          queues = matchWildCard(routingKey);
      }
      if(queues != null && queues.size()>0)
      {
          System.out.println("Message stored in " + queues.size() + " queues");
          storeMessage(msg,queues);
          return true;
      }
      else
      {
          System.out.println("Message unroutable " + msg);
          return false;
      }
  }
  
  private String normalizeKey(String routingKey)
  {
      if(routingKey.indexOf(".*")>1)
      {
          return routingKey.substring(0,routingKey.indexOf(".*"));
      }
      else
      {
          return routingKey;
      }
  }
  
  private List<LinkedBlockingQueue<Message>> matchWildCard(String routingKey)
  {        
      List<LinkedBlockingQueue<Message>> selected = new ArrayList<LinkedBlockingQueue<Message>>();
      
      for(String key: topicEx.keySet())
      {
          Pattern p = Pattern.compile(key);
          Matcher m = p.matcher(routingKey);
          if (m.find())
          {
              for(LinkedBlockingQueue<Message> queue : topicEx.get(key))
              {
                    selected.add(queue);
              }
          }
      }
      
      return selected;      
  }

  private void storeMessage(Message msg,List<LinkedBlockingQueue<Message>> selected)
  {
      for(LinkedBlockingQueue<Message> queue : selected)
      {
          queue.offer(msg);
      }
  }
  
}
