using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using org.apache.qpid.messaging;

namespace org.apache.qpid.messaging
{
    class Program
    {
        static void Main(string[] args)
        {
            //
            // Duration test - stub until proper nunit tests are ready...
            //
            Duration myDuration = new Duration(1234);

            Console.WriteLine("Duration should be : 1234, is : {0}",
                            myDuration.getMilliseconds());

            Console.WriteLine("Duration FOREVER should be : 1.8x10^19 (realbig), is : {0}",
                            myDuration.FOREVER());

            Console.WriteLine("Duration IMMEDIATE should be : 0, is : {0}",
                            myDuration.IMMEDIATE());

            Console.WriteLine("Duration SECOND should be : 1,000, is : {0}",
                            myDuration.SECOND());

            Console.WriteLine("Duration MINUTE should be : 60,000, is : {0}",
                            myDuration.MINUTE());

            //
            // and so on
            //
        }
    }
}
