using System;
using System.Text;
using log4net;
using Apache.Qpid.Messaging;

namespace Apache.Qpid.Client.Tests.interop.TestCases
{
    /// <summary>
    /// Implements tet case 1, dummy run. This test case sends no test messages, it exists to confirm that the test harness
    /// is interacting with the coordinator correctly.
    ///
    /// <p><table id="crc"><caption>CRC Card</caption>
    /// <tr><th> Responsibilities <th> Collaborations
    /// <tr><td> Supply the name of the test case that this implements.
    /// <tr><td> Accept/Reject invites based on test parameters.
    /// <tr><td> Adapt to assigned roles.
    /// <tr><td> Perform test case actions.
    /// <tr><td> Generate test reports.
    /// </table>
    /// </summary>
    public class TestCase1DummyRun : InteropClientTestCase
    {
        private static ILog log = LogManager.GetLogger(typeof(TestCase1DummyRun));

        public String GetName()
        {
            log.Debug("public String getName(): called");

            return "TC1_DummyRun";
        }

        public bool AcceptInvite(IMessage inviteMessage)
        {
            log.Debug("public boolean acceptInvite(Message inviteMessage): called");

            // Test parameters don't matter, accept all invites.
            return true;
        }

        public void AssignRole(Roles role, IMessage assignRoleMessage)
        {
            log.Debug("public void assignRole(Roles role, Message assignRoleMessage): called");

            // Do nothing, both roles are the same.
        }

        public void Start()
        {
            log.Debug("public void start(): called");

            // Do nothing.
        }

        public IMessage GetReport(IChannel channel)
        {
            log.Debug("public Message getReport(Session session): called");

            // Generate a dummy report, the coordinator expects a report but doesn't care what it is.
            return channel.CreateTextMessage("Dummy Run, Ok.");
        }

        public void OnMessage(IMessage message)
        {
            log.Debug("public void onMessage(Message message = " + message + "): called");

            // Ignore any messages.
        }
    }
}
