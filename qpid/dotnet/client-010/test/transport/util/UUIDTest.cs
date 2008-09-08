
using System;
using NUnit.Framework;
using org.apache.qpid.transport.util;

namespace test.transport.util
{
    [TestFixture]

    public class UUIDTest
    {
        [Test]
        public void createUUID()
        {
            UUID uuid = UUID.randomUUID();
            String uuidStr = uuid.ToString();
            Assert.IsNotNull(uuid);
            UUID uuid2 = UUID.randomUUID();
            Assert.AreNotSame(uuid, uuid2);
        }
    }
}
