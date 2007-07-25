package org.apache.qpidity;

public class CommonSessionDelegate extends Delegate<Session>
{

	public void sessionAttached(Session session, SessionAttached struct) {}
    
	public void sessionFlow(Session session, SessionFlow struct) {}
    
	public void sessionFlowOk(Session session, SessionFlowOk struct) {}
    
	public void sessionClose(Session session, SessionClose struct) {}
    
	public void sessionClosed(Session session, SessionClosed struct) {}
    
	public void sessionResume(Session session, SessionResume struct) {}
    
	public void sessionPing(Session session, SessionPing struct) {}
    
	public void sessionPong(Session session, SessionPong struct) {}
    
	public void sessionSuspend(Session session, SessionSuspend struct) {}
    
	public void sessionDetached(Session session, SessionDetached struct) {}
    

}
