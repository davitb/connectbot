/**
 *
 */
package com.trilead.ssh2.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;

/**
 * @author davitb
 *
 */
public class SmsSocket extends Socket {

	static final private String DEBUG_TAG = "smssocket";

	static final public String[] _twilio_nums = {
		"+14083594145",
		"+14086693027",
		"+14086693024",
		"+14083594050"
		};

	SmsOutputStream _outs = new SmsOutputStream();
	SmsInputStream _ins = new SmsInputStream();

	private int _nextNumber = 0;

	private String _hostname;

	public class SmsInputStream extends InputStream {

		public PipedInputStream _pipeReader = new PipedInputStream(128 * 10 * 1024);

		public SmsInputStream() {
		}

		@Override
		public int read (byte[] buffer) throws IOException
		{
			Log.e(DEBUG_TAG, "read 1");
			return readBuffer(buffer, 0, buffer.length);
		}

		@Override
		public int read (byte[] buffer, int byteOffset, int byteCount) throws IOException
		{
			Log.e(DEBUG_TAG, "read 2");
			return readBuffer(buffer, byteOffset, byteCount);
		}

		@Override
		public int read() throws IOException
		{
			Log.e(DEBUG_TAG, "read 3");
			byte[] buffer = new byte[1];
			readBuffer(buffer, 0, 1);
			return buffer[0];
		}

		private int readBuffer(byte[] buffer, int offset, int count) throws IOException
		{
			Log.e(DEBUG_TAG, "expected read bytes size: " + String.valueOf(count));
			int num = _pipeReader.read(buffer, offset, count);
//			if (count - num > 100)
			{
				try {
					// Give a break to the reader
					Thread.sleep(10);
				}
				catch (InterruptedException e) {}
			}
			Log.e(DEBUG_TAG, "read bytes size: " + String.valueOf(num));
			Log.e(DEBUG_TAG, "still available for read: " + String.valueOf(_pipeReader.available()));
			return num;
		}

		@Override
		public int available() throws IOException
		{
			return _pipeReader.available();
		}

		@Override
		public void close() throws IOException
		{
			_pipeReader.close();
		}

		@Override
		public void mark(int readlimit)
		{
			_pipeReader.mark(readlimit);
		}

		@Override
		public boolean markSupported()
		{
			return _pipeReader.markSupported();
		}

		@Override
		public void reset() throws IOException
		{
			_pipeReader.reset();
		}

		@Override
		public long skip(long byteCount) throws IOException
		{
			return _pipeReader.skip(byteCount);
		}
	}

	public static PipedOutputStream _pipeWriter = new PipedOutputStream();

	public class SmsOutputStream extends OutputStream {

		ByteArrayOutputStream _stream = new ByteArrayOutputStream();

		@Override
		public void write(byte[] buffer) throws IOException
		{
			Log.e(DEBUG_TAG, "write 1, count " + String.valueOf(buffer.length));
			_stream.write(buffer, 0, buffer.length);
		}

		@Override
		public void write(byte[] buffer, int offset, int count) throws IOException
		{
			Log.e(DEBUG_TAG, "write 2, count " + String.valueOf(count));
			_stream.write(buffer, offset, count);
		}

		@Override
		public void write(int oneByte) throws IOException
		{
			Log.e(DEBUG_TAG, "write 3, count 1");
			_stream.write(oneByte);
		}

		@Override
		public void flush() throws IOException
		{
			byte[] data = _stream.toByteArray();
			if (data.length == 16) {
				return;
			}
			Log.e(DEBUG_TAG, "flush, count: " + data.length);

			sendDataToTwilio("s", data);
			_stream = new ByteArrayOutputStream();
		}
	}

	public SmsSocket(String hostname) throws IOException {
		_pipeWriter = new PipedOutputStream();
		_ins._pipeReader.connect(_pipeWriter);
		_hostname = hostname;
	}

	/*
     * Returns a stream of type CompressionInputStream
     */
    @Override
	public InputStream getInputStream() throws IOException
    {
        return _ins;
    }

    /*
     * Returns a stream of type CompressionOutputStream
     */
    @Override
	public OutputStream getOutputStream() throws IOException
    {
        return _outs;
    }

    @Override
    public synchronized void setSoTimeout(int timeout)
    {

    }

    @Override
    public void setTcpNoDelay(boolean on)
    {

    }

    @Override
    public synchronized void close() throws IOException
    {
		_pipeWriter.flush();
    	sendSMS("l0100");
    }

    @Override
    public void connect(SocketAddress remoteAddr, int timeout) throws IOException
    {
    	Log.e(DEBUG_TAG, "RemoteAddr: " + remoteAddr.toString());

    	_pipeWriter.flush();
    	sendSMS("n0100" + _hostname);
    }

    private ArrayList<String> encodeForSmsProtocol(byte[] buffer) throws IOException
    {
    	//Log.e(DEBUG_TAG, bytesToHex(buffer));

    	int chunkSize = 155;

    	String b64EncodedMsg = Base64.encodeToString(buffer, Base64.NO_WRAP | Base64.URL_SAFE);
    	List<String> chunks = splitEqually(b64EncodedMsg, chunkSize);

    	ArrayList<String> strs = new ArrayList<String>();
    	for (int i = 0; i < chunks.size(); ++i) {
        	StringBuilder b = new StringBuilder();

        	b.append("s");
        	b.append(Integer.toHexString(0x100 | chunks.size()).substring(1));
        	b.append(Integer.toHexString(0x100 | i).substring(1));
        	b.append(chunks.get(i));

        	strs.add(b.toString());
    	}

    	return strs;
    }

    public void sendSMS(ArrayList<String> chunks)
    {
    	for (String val : chunks) {
    		sendSMS(val);
    	}
    }

    public void sendSMS(String oneChunk)
    {
		Log.e(DEBUG_TAG, "Sending through sms: " + oneChunk);

    	SmsManager.getDefault().sendTextMessage(getNextTwilioNumber(),
    													 null,
    													 oneChunk,
    													 null,
    													 null);
    }

    private void sendDataToTwilio(String op, byte[] buffer) throws IOException
    {
    	Log.e(DEBUG_TAG, "sending buffer size: " + buffer.length);

		sendSMS(encodeForSmsProtocol(buffer));
    }

    public static List<String> splitEqually(String text, int size)
    {
        List<String> ret = new ArrayList<String>((text.length() + size - 1) / size);

        for (int start = 0; start < text.length(); start += size) {
            ret.add(text.substring(start, Math.min(text.length(), start + size)));
        }
        return ret;
    }

    private String getNextTwilioNumber()
    {
    	if (++_nextNumber >= _twilio_nums.length) {
    		_nextNumber = 0;
    	}

    	return _twilio_nums[_nextNumber];
    }

}
