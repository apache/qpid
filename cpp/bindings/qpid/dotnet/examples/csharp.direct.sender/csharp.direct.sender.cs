using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using org.apache.qpid.messaging;

namespace csharp.direct.sender
{
    class Program
    {
        static void Main(string[] args)
        {
            String host = "localhost:5672";
            String addr = "amq.direct/key";
            int nMsg = 10;

            Connection conn = new Connection(host);

            conn.open();

            if (!conn.isOpen())
            {
                Console.WriteLine("Failed to open connection to host : {0}", host);
            }
            else
            {
                Session sess = conn.createSession();

                Sender snd = sess.createSender(addr);

                for (int i = 0; i < nMsg; i++)
                {
                    Message msg = new Message(String.Format("Test Message {0}", i));

                    snd.send(msg);
                }

                conn.close();
            }
        }
    }
}
