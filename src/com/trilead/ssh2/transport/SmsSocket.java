/**
 *
 */
package com.trilead.ssh2.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * @author davitb
 *
 */
public class SmsSocket extends Socket {

	static final private String DEBUG_TAG = "smssocket";

	SmsOutputStream _outs = new SmsOutputStream();
	SmsInputStream _ins = new SmsInputStream();

	public class SmsInputStream extends InputStream {

		public PipedInputStream _pipeReader = new PipedInputStream(128 * 8 * 1024);

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
			if (count - num > 100) {
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

	public class SmsOutputStream extends OutputStream {

		ByteArrayOutputStream _stream = new ByteArrayOutputStream();
		public PipedOutputStream _pipeWriter = new PipedOutputStream();

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
			if (count == 16) {
				int k = 10;
				k++;
				//return;
			}
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

			sendHTTPMessage("s", data);
			_stream = new ByteArrayOutputStream();
		}
	}

	public SmsSocket() throws IOException {
		_ins._pipeReader.connect(_outs._pipeWriter);
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
    public synchronized void close()
    {
    	bTerminateThread = true;
    }

    private boolean bTerminateThread = false;

    @Override
    public void connect(SocketAddress remoteAddr, int timeout)
    {
    	Log.e(DEBUG_TAG, "RemoteAddr: " + remoteAddr.toString());

    	//SmsManager.getDefault().sendTextMessage("+14084390019", null, "utyu", null, null);

		new Thread(new Runnable() {
	        public void run() {
	        	try {
	        		while (!bTerminateThread) {
		        		Thread.sleep(500);

		        		Log.e(DEBUG_TAG, "Reading thread: preparing to send HTTP");
						byte[] response = sendHTTPMessage("r", new byte[0]);
						if (response.length > 0) {
							_outs._pipeWriter.write(response, 0, response.length);
						}
	        		}
	        	}
	        	catch (Exception e) {
	        		Log.e(DEBUG_TAG, e.getMessage());
	        	}
	        }
	    }).start();

    }

    private String prepareJSONMessage(String op, String msg) throws IOException
    {
		try {
			JSONObject js = new JSONObject();
			js.put("op", op);
			if (op.equals("s")) {
				js.put("msg", msg);
			}
			return js.toString();
		}
		catch (JSONException e) {
			throw new IOException("Cannot create JSON");
		}
    }

    private byte[] extractResponseFromJSON(String jsonMsg) throws IOException
    {
		try {
			byte[] response = new byte[0];
			JSONObject js = new JSONObject(jsonMsg);
			jsonMsg = js.getString("op");
			if (jsonMsg.equals("r")) {
				jsonMsg = js.getString("msg");
				if (jsonMsg.length() > 0) {
					//response = Base64.decode(resp, Base64.NO_WRAP | Base64.URL_SAFE);
					response = hexStringToByteArray(jsonMsg);
				}
			}
			Log.e(DEBUG_TAG, "received buffer size: " + response.length);
			return response;
		}
		catch (JSONException e) {
			throw new IOException("Cannot parse JSON");
		}
    }

    private synchronized byte[] sendHTTPMessage(String op, byte[] buffer) throws IOException
    {
    	Log.e(DEBUG_TAG, "sending buffer size: " + buffer.length);
		//String msg = Base64.encodeToString(buffer, Base64.NO_WRAP | Base64.URL_SAFE);
    	String msg = bytesToHex(buffer);
    	//Log.e(DEBUG_TAG, "sending buffer: " + bytesToHex(buffer));

		msg = prepareJSONMessage(op, msg);

		String url = "http://481b2aa.ngrok.com/ssh?msg=" + msg;

		Log.e(DEBUG_TAG, "HTTP request: " + url);

		return extractResponseFromJSON(downloadUrl(url));
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

	static int _num_times = 0;

	// Given a URL, establishes an HttpUrlConnection and retrieves
	// the web page content as a InputStream, which it returns as
	// a string.
	private String downloadUrl(String myurl) throws IOException {
	     InputStream is = null;
	     // Only display the first 500 characters of the retrieved
	     // web page content.
	     int len = 64 * 1024;

	     try {
	         URL url = new URL(myurl);
	         HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	         conn.setReadTimeout(10000 /* milliseconds */);
	         conn.setConnectTimeout(15000 /* milliseconds */);
	         conn.setRequestMethod("GET");
	         conn.setDoInput(true);
	         // Starts the query
	         conn.connect();
	         int response = conn.getResponseCode();
	         Log.d(DEBUG_TAG, "The response is: " + response);
	         is = conn.getInputStream();

	         // Convert the InputStream into a string
	         String contentAsString = readIt(is, len);
	         return contentAsString;

	     // Makes sure that the InputStream is closed after the app is
	     // finished using it.
	     }
	     catch (Exception e) {
	    	 throw new IOException(e.getMessage());
	     }
	     finally {
	         if (is != null) {
	             is.close();
	         }
	     }
	 }

	 private static String readIt(final InputStream is, final int bufferSize)
	 {
	   final char[] buffer = new char[bufferSize];
	   final StringBuilder out = new StringBuilder();
	   try {
	     final Reader in = new InputStreamReader(is, "UTF-8");
	     try {
	       for (;;) {
	         int rsz = in.read(buffer, 0, buffer.length);
	         if (rsz < 0)
	           break;
	         out.append(buffer, 0, rsz);
	       }
	     }
	     finally {
	       in.close();
	     }
	   }
	   catch (UnsupportedEncodingException ex) {
	     /* ... */
	   }
	   catch (IOException ex) {
	       /* ... */
	   }
	   return out.toString();
	}
}
