using System;
using System.Net.Sockets;
using NUnit.Framework;
using org.apache.qpid.client;
using test.Helpers;

namespace test
{
    [TestFixture]
    public class ConnectionTests
    {
        [SetUp]
        public void Setup()
        {

        }

        [Test]
        [ExpectedException(typeof(Exception))]
        public void should_raise_exception_in_calling_thread_on_authentification_failure()
        {
            var properties = ConfigHelpers.LoadConfig();

            var client = new Client();
            client.Connect(properties["Host"], Convert.ToInt16(properties["Port"]), properties["VirtualHost"],
                           properties["Username"], "some silly password to make sure the authentification fail");      
        }

        [Test]
        [ExpectedException(typeof(Exception))]
        public void should_raise_exception_in_calling_thread_on_authentification_failure_with_clodedListener()
        {
            var properties = ConfigHelpers.LoadConfig();

            var client = new Client();
            client.ClosedListener = new FakeListener();
            client.Connect(properties["Host"], Convert.ToInt16(properties["Port"]), properties["VirtualHost"],
                           properties["Username"], "some silly password to make sure the authentification fail");
        }

        [Test]
        public void should_not_block_on_close()
        {
            var properties = ConfigHelpers.LoadConfig();

            var client = new Client();
            client.Connect(properties["Host"], Convert.ToInt16(properties["Port"]), properties["VirtualHost"],
                           properties["Username"], properties["Password"]);
            client.Close();
        }
    }

    public class FakeListener : IClosedListener
    {
        public void OnClosed(ErrorCode errorCode, string reason, Exception t)
        {
        }
    }
}
