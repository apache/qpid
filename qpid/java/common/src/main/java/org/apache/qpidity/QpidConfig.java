package org.apache.qpidity;

/**
 * API to configure the Security parameters of the client.
 * The user can choose to pick the config from any source 
 * and set it using this class.
 *
 */
public class QpidConfig
{
    private static QpidConfig _instance = new QpidConfig();
    
    private SecurityMechanism[] securityMechanisms = 
        new SecurityMechanism[]{new SecurityMechanism("PLAIN","org.apache.qpidity.security.UsernamePasswordCallbackHandler"),
                                new SecurityMechanism("CRAM_MD5","org.apache.qpidity.security.UsernamePasswordCallbackHandler")};

    private SaslClientFactory[] saslClientFactories =
        new SaslClientFactory[]{new SaslClientFactory("AMQPLAIN","org.apache.qpidity.security.amqplain.AmqPlainSaslClientFactory")};       
    
   private  QpidConfig(){}
    
   public static QpidConfig get()
   {
       return _instance;
   }
    
   public void setSecurityMechanisms(SecurityMechanism... securityMechanisms)
   {
       this.securityMechanisms = securityMechanisms;
   }   
   
   public SecurityMechanism[] getSecurityMechanisms()
   {
       return securityMechanisms;
   }
       
   public void setSaslClientFactories(SaslClientFactory... saslClientFactories)
   {
       this.saslClientFactories = saslClientFactories;
   }   
   
   public SaslClientFactory[] getSaslClientFactories()
   {
       return saslClientFactories;
   }
   
   public class SecurityMechanism
   {
        String type;
        String handler;
        
        SecurityMechanism(String type,String handler)
        {
            this.type = type;
            this.handler = handler;
        }

        public String getHandler()
        {
            return handler;
        }

        public String getType()
        {
            return type;
        }
   }
   
   public class SaslClientFactory
   {
        String type;
        String factoryClass;
        
        SaslClientFactory(String type,String factoryClass)
        {
            this.type = type;
            this.factoryClass = factoryClass;
        }

        public String getFactoryClass()
        {
            return factoryClass;
        }

        public String getType()
        {
            return type;
        }
   }
}
