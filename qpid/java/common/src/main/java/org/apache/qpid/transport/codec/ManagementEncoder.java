package org.apache.qpid.transport.codec;


import java.nio.ByteBuffer;

import org.apache.qpid.transport.codec.AbstractEncoder;

/*
 * @author rahulapi@gmailcom
 *
 * Management Encoder extends the basic java encoer (AbastractEncoder)
 * its a java encoder we use to encode the incoming messages
 * (encode the fields of the message)
 *
 * TODO (Gazza) : I don't like very much this class...this code should be part of BBDecoder
 */
public class ManagementEncoder extends AbstractEncoder{


	public  ByteBuffer _out=null;

	public ManagementEncoder(ByteBuffer out)
	{

		_out = out;
	}

	protected void doPut(byte b)
	{
		_out.put(b);
	}

	protected void doPut(ByteBuffer src)
	{
		_out.put(src);
	}

	public void writeBin128(byte[] s)
	{
		if(s==null)
		{
			s=new byte[16];

		}else if(s.length!=16){
			throw new IllegalArgumentException(""+s);
		}
		put(s);
	}

	@Override
	protected int beginSize16() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected int beginSize32() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected int beginSize8() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	protected void endSize16(int pos) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void endSize32(int pos) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void endSize8(int pos) {
		// TODO Auto-generated method stub

	}

}