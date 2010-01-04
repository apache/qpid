using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Xml;
using log4net.Config;

namespace test.Helpers
{
    class ConfigHelpers
    {
        public static Dictionary<string, string> LoadConfig()
        {
            Dictionary<string, string> properties = new Dictionary<string, string>();

            XmlConfigurator.Configure(new FileInfo("/log.xml"));
            // populate default properties
            properties.Add("Username", "guest");
            properties.Add("Password", "guest");
            properties.Add("Host", "localhost");
            properties.Add("Port", "5672");
            properties.Add("VirtualHost", "test");
            //Read the test config file  
            XmlTextReader reader = new XmlTextReader(Environment.CurrentDirectory + "/Qpid Test.dll.config");
            while (reader.Read())
            {
                // if node type is an element
                if (reader.NodeType == XmlNodeType.Element && reader.Name.Equals("add"))
                {
                    if (properties.ContainsKey(reader.GetAttribute("key")))
                    {
                        properties[reader.GetAttribute("key")] = reader.GetAttribute("value");
                    }
                    else
                    {
                        properties.Add(reader.GetAttribute("key"), reader.GetAttribute("value"));
                    }
                }
            }

            return properties;
        }
    }
}
