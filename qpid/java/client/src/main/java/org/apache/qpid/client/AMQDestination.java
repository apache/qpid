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
package org.apache.qpid.client;

import java.net.URISyntaxException;
import java.util.Map;

import javax.jms.Destination;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import org.apache.qpid.client.messaging.address.AddressHelper;
import org.apache.qpid.client.messaging.address.Link;
import org.apache.qpid.client.messaging.address.Node;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;
import org.apache.qpid.url.URLHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AMQDestination implements Destination, Referenceable
{
    private static final Logger _logger = LoggerFactory.getLogger(AMQDestination.class);
    
    protected AMQShortString _exchangeName;

    protected AMQShortString _exchangeClass;

    protected boolean _isDurable;

    protected boolean _isExclusive;

    protected boolean _isAutoDelete;

    private boolean _browseOnly;
    
    private boolean _isAddressResolved;

    private AMQShortString _queueName;

    private AMQShortString _routingKey;

    private AMQShortString[] _bindingKeys;

    private String _url;
    private AMQShortString _urlAsShortString;

    private boolean _checkedForQueueBinding;

    private boolean _exchangeExistsChecked;

    public static final int QUEUE_TYPE = 1;
    public static final int TOPIC_TYPE = 2;
    public static final int UNKNOWN_TYPE = 3;
    
    // ----- Fields required to support new address syntax -------
    
    public enum DestSyntax {        
      BURL,ADDR;
      
      public static DestSyntax getSyntaxType(String s)
      {
          if (("BURL").equals(s))
          {
              return BURL;
          }
          else if (("ADDR").equals(s))
          {
              return ADDR;
          }
          else
          {
              throw new IllegalArgumentException("Invalid Destination Syntax Type" +
                                                 " should be one of {BURL|ADDR}");
          }
      }
    } 
    
    public enum AddressOption { 
      ALWAYS, NEVER, SENDER, RECEIVER; 
        
      public static AddressOption getOption(String str)
      {
          if ("always".equals(str)) return ALWAYS;
          else if ("never".equals(str)) return NEVER;
          else if ("sender".equals(str)) return SENDER;
          else if ("receiver".equals(str)) return RECEIVER;
          else throw new IllegalArgumentException(str + " is not an allowed value");
      }
    }
    
    protected final static DestSyntax defaultDestSyntax;
    
    protected DestSyntax _destSyntax = DestSyntax.ADDR;

    protected AddressHelper _addrHelper;
    protected Address _address;
    protected int _addressType = AMQDestination.UNKNOWN_TYPE;
    protected String _name;
    protected String _subject;
    protected AddressOption _create = AddressOption.NEVER;
    protected AddressOption _assert = AddressOption.NEVER;
    protected AddressOption _delete = AddressOption.NEVER; 

    protected Node _targetNode;
    protected Node _sourceNode;
    protected Link _targetLink;
    protected Link _link;    
        
    // ----- / Fields required to support new address syntax -------
    
    static
    {
        defaultDestSyntax = DestSyntax.getSyntaxType(
                     System.getProperty(ClientProperties.DEST_SYNTAX,
                                        DestSyntax.ADDR.toString()));
        
        
    }
    
    public static DestSyntax getDefaultDestSyntax()
    {
        return defaultDestSyntax;
    }

    protected AMQDestination(Address address) throws Exception
    {
        this._address = address;
        getInfoFromAddress();
        _destSyntax = DestSyntax.ADDR;
        _logger.debug("Based on " + address + " the selected destination syntax is " + _destSyntax);
    }
    
    public  static DestSyntax getDestType(String str)
    {
        if (str.startsWith("BURL:") || 
                (!str.startsWith("ADDR:") && defaultDestSyntax == DestSyntax.BURL))
        {
            return DestSyntax.BURL;
        }
        else
        {
            return DestSyntax.ADDR;
        }
    }
    
    public static String stripSyntaxPrefix(String str)
    {
        if (str.startsWith("BURL:") || str.startsWith("ADDR:"))
        {
            return str.substring(5,str.length());
        }
        else
        {
            return str;
        }
    }
    
    protected AMQDestination(String str) throws URISyntaxException
    {
        _destSyntax = getDestType(str);
        str = stripSyntaxPrefix(str);
        if (_destSyntax == DestSyntax.BURL)
        {    
            getInfoFromBindingURL(new AMQBindingURL(str));            
        }
        else
        {
            this._address = createAddressFromString(str);
            try
            {
                getInfoFromAddress();
            }
            catch(Exception e)
            {
                URISyntaxException ex = new URISyntaxException(str,"Error parsing address");
                ex.initCause(e);
                throw ex;
            }
        }
        _logger.debug("Based on " + str + " the selected destination syntax is " + _destSyntax);
    }
    
    //retained for legacy support
    protected AMQDestination(BindingURL binding)
    {
        getInfoFromBindingURL(binding);
        _destSyntax = DestSyntax.BURL;
        _logger.debug("Based on " + binding + " the selected destination syntax is " + _destSyntax);
    }

    protected void getInfoFromBindingURL(BindingURL binding)
    {
        _exchangeName = binding.getExchangeName();
        _exchangeClass = binding.getExchangeClass();

        _isExclusive = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_EXCLUSIVE));
        _isAutoDelete = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_AUTODELETE));
        _isDurable = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_DURABLE));
        _browseOnly = Boolean.parseBoolean(binding.getOption(BindingURL.OPTION_BROWSE));
        _queueName = binding.getQueueName() == null ? null : binding.getQueueName();
        _routingKey = binding.getRoutingKey() == null ? null : binding.getRoutingKey();
        _bindingKeys = binding.getBindingKeys() == null || binding.getBindingKeys().length == 0 ? new AMQShortString[0] : binding.getBindingKeys();
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, AMQShortString queueName)
    {
        this(exchangeName, exchangeClass, routingKey, false, false, queueName, null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, AMQShortString queueName, AMQShortString[] bindingKeys)
    {
        this(exchangeName, exchangeClass, routingKey, false, false, queueName,bindingKeys);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString destinationName)
    {
        this(exchangeName, exchangeClass, destinationName, false, false, null,null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
            boolean isAutoDelete, AMQShortString queueName)
    {
        this(exchangeName, exchangeClass, routingKey, isExclusive, isAutoDelete, queueName, false,null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
                             boolean isAutoDelete, AMQShortString queueName,AMQShortString[] bindingKeys)
    {
        this(exchangeName, exchangeClass, routingKey, isExclusive, isAutoDelete, queueName, false,bindingKeys);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
            boolean isAutoDelete, AMQShortString queueName, boolean isDurable){
        this (exchangeName, exchangeClass, routingKey, isExclusive,isAutoDelete,queueName,isDurable,null);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
                             boolean isAutoDelete, AMQShortString queueName, boolean isDurable,AMQShortString[] bindingKeys)
    {
        this (exchangeName, exchangeClass, routingKey, isExclusive,isAutoDelete,queueName,isDurable,bindingKeys, false);
    }

    protected AMQDestination(AMQShortString exchangeName, AMQShortString exchangeClass, AMQShortString routingKey, boolean isExclusive,
                             boolean isAutoDelete, AMQShortString queueName, boolean isDurable,AMQShortString[] bindingKeys, boolean browseOnly)
    {
        if ( (ExchangeDefaults.DIRECT_EXCHANGE_CLASS.equals(exchangeClass) || 
              ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(exchangeClass))  
              && routingKey == null)
        {
            throw new IllegalArgumentException("routing/binding key  must not be null");
        }
        if (exchangeName == null)
        {
            throw new IllegalArgumentException("Exchange name must not be null");
        }
        if (exchangeClass == null)
        {
            throw new IllegalArgumentException("Exchange class must not be null");
        }
        _exchangeName = exchangeName;
        _exchangeClass = exchangeClass;
        _routingKey = routingKey;
        _isExclusive = isExclusive;
        _isAutoDelete = isAutoDelete;
        _queueName = queueName;
        _isDurable = isDurable;
        _bindingKeys = bindingKeys == null || bindingKeys.length == 0 ? new AMQShortString[0] : bindingKeys;
        _destSyntax = DestSyntax.BURL;
        _browseOnly = browseOnly;
        
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Based on " + toString() + " the selected destination syntax is " + _destSyntax);
        }
    }

    public DestSyntax getDestSyntax() 
    {
        return _destSyntax;
    }
    
    protected void setDestSyntax(DestSyntax syntax)
    {
        _destSyntax = syntax;
    }
    
    public AMQShortString getEncodedName()
    {
        if(_urlAsShortString == null)
        {
            if (_url == null)
            {
                toURL();
            }
            _urlAsShortString = new AMQShortString(_url);
        }
        return _urlAsShortString;
    }

    public boolean isDurable()
    {
        return _isDurable;
    }

    public AMQShortString getExchangeName()
    {
        return _exchangeName;
    }

    public AMQShortString getExchangeClass()
    {
        return _exchangeClass;
    }

    public boolean isTopic()
    {
        return ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(_exchangeClass);
    }

    public boolean isQueue()
    {
        return ExchangeDefaults.DIRECT_EXCHANGE_CLASS.equals(_exchangeClass);
    }

    public String getQueueName()
    {
        return _queueName == null ? null : _queueName.toString();
    }

    public AMQShortString getAMQQueueName()
    {
        return _queueName;
    }

    public void setQueueName(AMQShortString queueName)
    {

        _queueName = queueName;
        // calculated URL now out of date
        _url = null;
        _urlAsShortString = null;
    }

    public AMQShortString getRoutingKey()
    {
        return _routingKey;
    }

    public AMQShortString[] getBindingKeys()
    {
        if (_bindingKeys != null && _bindingKeys.length > 0)
        {
            return _bindingKeys;
        }
        else
        {
            // catering to the common use case where the
            //routingKey is the same as the bindingKey.
            return new AMQShortString[]{_routingKey};
        }
    }

    public boolean isExclusive()
    {
        return _isExclusive;
    }
    
    public boolean isAutoDelete()
    {
        return _isAutoDelete;
    }

    public abstract boolean isNameRequired();

    public String toString()
    {
        if (_destSyntax == DestSyntax.BURL)
        {
            return toURL();
        }
        else
        {
            return _address.toString();
        }

    }

    public boolean isCheckedForQueueBinding()
    {
        return _checkedForQueueBinding;
    }

    public void setCheckedForQueueBinding(boolean checkedForQueueBinding)
    {
        _checkedForQueueBinding = checkedForQueueBinding;
    }


    public boolean isExchangeExistsChecked()
    {
        return _exchangeExistsChecked;
    }

    public void setExchangeExistsChecked(final boolean exchangeExistsChecked)
    {
        _exchangeExistsChecked = exchangeExistsChecked;
    }

    public String toURL()
    {
        String url = _url;
        if(url == null)
        {


            StringBuffer sb = new StringBuffer();

            sb.append(_exchangeClass);
            sb.append("://");
            sb.append(_exchangeName);

            sb.append("/"+_routingKey+"/");

            if (_queueName != null)
            {
                sb.append(_queueName);
            }

            sb.append('?');

            if (_routingKey != null)
            {
                sb.append(BindingURL.OPTION_ROUTING_KEY);
                sb.append("='");
                sb.append(_routingKey).append("'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            // We can't allow both routingKey and bindingKey
            if (_routingKey == null && _bindingKeys != null && _bindingKeys.length>0)
            {

                for (AMQShortString bindingKey:_bindingKeys)
                {
                    sb.append(BindingURL.OPTION_BINDING_KEY);
                    sb.append("='");
                    sb.append(bindingKey);
                    sb.append("'");
                    sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);

                }
            }

            if (_isDurable)
            {
                sb.append(BindingURL.OPTION_DURABLE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_isExclusive)
            {
                sb.append(BindingURL.OPTION_EXCLUSIVE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            if (_isAutoDelete)
            {
                sb.append(BindingURL.OPTION_AUTODELETE);
                sb.append("='true'");
                sb.append(URLHelper.DEFAULT_OPTION_SEPERATOR);
            }

            //removeKey the last char '?' if there is no options , ',' if there are.
            sb.deleteCharAt(sb.length() - 1);
            url = sb.toString();
            _url = url;
        }
        return url;
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final AMQDestination that = (AMQDestination) o;

        if (!_exchangeClass.equals(that._exchangeClass))
        {
            return false;
        }
        if (!_exchangeName.equals(that._exchangeName))
        {
            return false;
        }
        if ((_queueName == null && that._queueName != null) ||
            (_queueName != null && !_queueName.equals(that._queueName)))
        {
            return false;
        }

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _exchangeName == null ? "".hashCode() : _exchangeName.hashCode();
        result = 29 * result + (_exchangeClass == null ? "".hashCode() :_exchangeClass.hashCode());
        //result = 29 * result + _destinationName.hashCode();
        if (_queueName != null)
        {
            result = 29 * result + _queueName.hashCode();
        }

        return result;
    }

    public Reference getReference() throws NamingException
    {
        return new Reference(
                this.getClass().getName(),
                new StringRefAddr(this.getClass().getName(), toURL()),
                AMQConnectionFactory.class.getName(),
                null);          // factory location
    }

    public static Destination createDestination(BindingURL binding)
    {
        AMQShortString type = binding.getExchangeClass();

        if (type.equals(ExchangeDefaults.DIRECT_EXCHANGE_CLASS))
        {
            return new AMQQueue(binding);
        }
        else if (type.equals(ExchangeDefaults.TOPIC_EXCHANGE_CLASS))
        {
            return new AMQTopic(binding);
        }
        else if (type.equals(ExchangeDefaults.HEADERS_EXCHANGE_CLASS))
        {
            return new AMQHeadersExchange(binding);
        }
        else
        {
            return new AMQAnyDestination(binding);
        }
    }

    public static Destination createDestination(String str) throws Exception
    {
         DestSyntax syntax = getDestType(str);
         str = stripSyntaxPrefix(str);
         if (syntax == DestSyntax.BURL)
         {          
             return createDestination(new AMQBindingURL(str));         
         }
         else
         {
             Address address = createAddressFromString(str);
             return new AMQAnyDestination(address);
         }
    }
    
    // ----- new address syntax -----------
    
    public static class Binding
    {
        String exchange;
        String bindingKey;
        String queue;
        Map<String,Object> args;
        
        public Binding(String exchange,
                       String queue,
                       String bindingKey,
                       Map<String,Object> args)
        {
            this.exchange = exchange;
            this.queue = queue;
            this.bindingKey = bindingKey;
            this.args = args;
        }
        
        public String getExchange() 
        {
            return exchange;
        }

        public String getQueue() 
        {
            return queue;
        }
        
        public String getBindingKey() 
        {
            return bindingKey;
        }
        
        public Map<String, Object> getArgs() 
        {
            return args;
        }
    }
    
    public Address getAddress() {
        return _address;
    }
    
    protected void setAddress(Address addr) {
        _address = addr;
    }
    
    public int getAddressType(){
        return _addressType;
    }

    public void setAddressType(int addressType){
        _addressType = addressType;
    }
    
    public String getAddressName() {
        return _name;
    }
    
    public void setAddressName(String name){
        _name = name;
    }

    public String getSubject() {
        return _subject;
    }

    public void setSubject(String subject) {
        _subject = subject;
    }
    
    public AddressOption getCreate() {
        return _create;
    }
    
    public void setCreate(AddressOption option) {
        _create = option;
    }
   
    public AddressOption getAssert() {
        return _assert;
    }

    public void setAssert(AddressOption option) {
        _assert = option;
    }
    
    public AddressOption getDelete() {
        return _delete;
    }

    public void setDelete(AddressOption option) {
        _delete = option;
    }

    public Node getTargetNode()
    {
        return _targetNode;
    }

    public void setTargetNode(Node node)
    {
        _targetNode = node;
    }

    public Node getSourceNode()
    {
        return _sourceNode;
    }

    public void setSourceNode(Node node)
    {
        _sourceNode = node;
    }

    public Link getLink()
    {
        return _link;
    }

    public void setLink(Link link)
    {
        _link = link;
    }
    
    public void setExchangeName(AMQShortString name)
    {
        this._exchangeName = name;
    }
    
    public void setExchangeClass(AMQShortString type)
    {
        this._exchangeClass = type;
    }
    
    public void setRoutingKey(AMQShortString rk)
    {
        this._routingKey = rk;
    }
    
    public boolean isAddressResolved()
    {
        return _isAddressResolved;
    }

    public void setAddressResolved(boolean addressResolved)
    {
        _isAddressResolved = addressResolved;
    }
    
    private static Address createAddressFromString(String str)
    {
        return Address.parse(str);
    }
    
    private void getInfoFromAddress() throws Exception
    {
        _name = _address.getName();
        _subject = _address.getSubject();
        
        _addrHelper = new AddressHelper(_address);
        
        _create = _addrHelper.getCreate() != null ?
                 AddressOption.getOption(_addrHelper.getCreate()):AddressOption.NEVER;
                
        _assert = _addrHelper.getAssert() != null ?
                 AddressOption.getOption(_addrHelper.getAssert()):AddressOption.NEVER;

        _delete = _addrHelper.getDelete() != null ?
                 AddressOption.getOption(_addrHelper.getDelete()):AddressOption.NEVER;
                 
        _browseOnly = _addrHelper.isBrowseOnly();
                        
        _addressType = _addrHelper.getTargetNodeType();         
        _targetNode =  _addrHelper.getTargetNode(_addressType);
        _sourceNode = _addrHelper.getSourceNode(_addressType);
        _link = _addrHelper.getLink();       
    }
    
    // This method is needed if we didn't know the node type at the beginning.
    // Therefore we have to query the broker to figure out the type.
    // Once the type is known we look for the necessary properties.
    public void rebuildTargetAndSourceNodes(int addressType)
    {
        _targetNode =  _addrHelper.getTargetNode(addressType);
        _sourceNode =  _addrHelper.getSourceNode(addressType);
    }
    
    // ----- / new address syntax -----------    

    public boolean isBrowseOnly()
    {
        return _browseOnly;
    }
    
    public void setBrowseOnly(boolean b)
    {
        _browseOnly = b;
    }
    
    public AMQDestination copyDestination()
    {
        AMQDestination dest = 
            new AMQAnyDestination(_exchangeName,
                                  _exchangeClass,
                                  _routingKey,
                                  _isExclusive, 
                                  _isAutoDelete,
                                  _queueName, 
                                  _isDurable,
                                  _bindingKeys
                                  );
        
        dest.setDestSyntax(_destSyntax);
        dest.setAddress(_address);
        dest.setAddressName(_name);
        dest.setSubject(_subject);
        dest.setCreate(_create); 
        dest.setAssert(_assert); 
        dest.setDelete(_create); 
        dest.setBrowseOnly(_browseOnly);
        dest.setAddressType(_addressType);
        dest.setTargetNode(_targetNode);
        dest.setSourceNode(_sourceNode);
        dest.setLink(_link);
        dest.setAddressResolved(_isAddressResolved);
        return dest;        
    }
    
    protected void setAutoDelete(boolean b)
    {
        _isAutoDelete = b;
    }
    
    protected void setDurable(boolean b)
    {
        _isDurable = b;
    }
}
