using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using org.apache.qpid.messaging;

namespace CSharpDirect
{
    class Program
    {
        // Direct receiver example
        //
        // Receive 10 messages from localhost:5672, amq.direct/key
        //
        static void Main(string[] args)
        {
            String host = "localhost:5672";
            String addr = "amq.direct/key";
            int    nMsg = 10;

            Connection conn = new Connection(host);

            conn.open();

            if (!conn.isOpen())
            {
                Console.WriteLine("Failed to open connection to host : {0}", host);
            }
            else
            {

                Session sess = conn.createSession();

                Duration dur = new Duration(1000 * 3600 * 24); // Wait one day

                Receiver rcv = sess.createReceiver(addr);

                Message msg = new Message("");

                for (int i = 0; i < nMsg; i++)
                {
                    try
                    {
                        Message msg2 = rcv.fetch(dur);
                        Console.WriteLine("Rcvd msg {0} : {1}", i, msg2.getContent());
                    }
                    catch (Exception e)
                    {
                        Console.WriteLine("Exception {0}.", e);
                    }
                }

                conn.close();
            }
        }
    }
}