using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Org.Apache.Qpid.Messaging;

namespace Org.Apache.Qpid.Messaging
{
    class Program
    {
        static void Main(string[] args)
        {
            //
            // Duration test - stub until proper nunit tests are ready...

            Duration myDuration = new Duration(1234);

            Console.WriteLine("Duration should be : 1234, is : {0}",
                            myDuration.Milliseconds);

            Console.WriteLine("Duration FOREVER should be : 1.8x10^19 (realbig), is : {0}",
                            DurationConstants.FORVER.Milliseconds);

            Console.WriteLine("Duration IMMEDIATE should be : 0, is : {0}",
                            DurationConstants.IMMEDIATE.Milliseconds);

            Console.WriteLine("Duration SECOND should be : 1,000, is : {0}",
                            DurationConstants.SECOND.Milliseconds);

            Console.WriteLine("Duration MINUTE should be : 60,000, is : {0}",
                            DurationConstants.MINUTE.Milliseconds);

            Duration isInfinite = new Duration();

            Console.WriteLine("Duration() should be : realbig, is : {0}",
                            isInfinite.Milliseconds);

            Duration fiveMinutes = new Duration(DurationConstants.MINUTE.Milliseconds * 5);
            Console.WriteLine("Duration 5MINUTE should be : 300,000, is : {0}",
                            fiveMinutes.Milliseconds);

            Duration fiveSec = DurationConstants.SECOND * 5;
            Console.WriteLine("Duration 5SECOND should be : 5,000 is : {0}",
                            fiveSec.Milliseconds);
            //
            // and so on
            //

            Dictionary<string, object> dx = new Dictionary<string, object>();

            Console.WriteLine("Dictionary.GetType() {0}", dx.GetType());

            //
            // Address test
            //
            Address aEmpty = new Address();
            Address aStr   = new Address("rare");

            Dictionary<string, object> options = new Dictionary<string,object>();
            options["one"] = 1;
            options["two"] = "two";

            Address aSubj = new Address("rare2", "subj", options);

            Address aType = new Address ("check3", "subj", options, "hot");

            Console.WriteLine("aEmpty : {0}", aEmpty.ToStr());
            Console.WriteLine("aStr   : {0}", aStr.ToStr());
            Console.WriteLine("aSubj  : {0}", aSubj.ToStr());
            Console.WriteLine("aType  : {0}", aType.ToStr());

            //
            // Raw message data retrieval
            //

            Message m2 = new Message("rarey");
            UInt64 m2Size = m2.GetContentSize();


            byte[] myRaw = new byte [m2Size];

            m2.GetRaw(myRaw);
            Console.WriteLine("Got raw array size {0}", m2Size);
            for (UInt64 i = 0; i < m2Size; i++)
                Console.Write("{0} ", myRaw[i].ToString());
            Console.WriteLine();

            //
            // Raw message creation
            //
            byte[] rawData = new byte[10];
            for (byte i=0; i<10; i++)
                rawData[i] = i;
            Message m3 = new Message(rawData);

            byte[] rawDataReadback = new byte[m3.GetContentSize()];
            m3.GetRaw(rawDataReadback);
            for (UInt64 i = 0; i < m3.GetContentSize(); i++)
                Console.Write("{0} ", rawDataReadback[i].ToString());
            Console.WriteLine();

            //
            // Raw message from array slice
            //
            byte[] rawData4 = new byte[256];
            for (int i = 0; i <= 255; i++)
                rawData4[i] = (byte)i;

            Message m4 = new Message(rawData4, 246, 10);

            byte[] rawDataReadback4 = new byte[m4.GetContentSize()];
            m4.GetRaw(rawDataReadback4);
            for (UInt64 i = 0; i < m4.GetContentSize(); i++)
                Console.Write("{0} ", rawDataReadback4[i].ToString());
            Console.WriteLine();

        }
    }
}
