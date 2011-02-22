package gr.forth.ics.icardea.mllp;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.app.SimpleServer;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.llp.LowerLayerProtocol;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.llp.MinLLPReader;
import ca.uhn.hl7v2.llp.MinLLPWriter;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

class iCArdeaApp implements Application {
	 public boolean canProcess(Message in) {
		 return true;		 
	 }
	 public Message processMessage(Message in) {
		 System.out.println("Got message "+in.getName());
		 System.out.println("Version "+in.getVersion());
		 Message res = null;
		 try {
		  res = in.generateACK();
		 }
		 catch (Exception e) {
			 e.printStackTrace();
		 }
		 return res;
	 }
	 
}
public class EHRListener {

	 private static String readFile(String path) throws IOException {
		 return readFile(new FileInputStream(new File(path)));
	 }
	 private static String readFile(FileInputStream stream) throws IOException {
		  try {
		    FileChannel fc = stream.getChannel();
		    MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
		    /* Instead of using default, pass in a decoder. */
		    return Charset.defaultCharset().decode(bb).toString();
		  }
		  finally {
		    stream.close();
		  }
		}

	  
	public static void main(String[] args) throws IOException {
		int port = 2575;
		
		SimpleServer sp = new SimpleServer(port, LowerLayerProtocol.makeLLP(), new GenericParser());

		sp.registerApplication("*", "*", new iCArdeaApp());
		sp.start();
		
		/*
		

		String host = "localhost";
		// String host = "iapetus";
		
		FileInputStream f = new FileInputStream("test/adt.hl7");
		// FileInputStream f = new FileInputStream("test/ADT-Messages.txt");
		//FileInputStream f = new FileInputStream("test/Labor-Message.hl7");
		try {
			Socket socket = new Socket(host, port);
			// socket.setSoLinger(true, 1000);
			MinLLPReader llpr = new MinLLPReader(socket.getInputStream());
			MinLLPWriter llpw = new MinLLPWriter(socket.getOutputStream());
			System.setProperty(MinLLPWriter.CHARSET_KEY, "UTF-8");
		
			GenericParser pp = new GenericParser();
			//for (String hl7msg: Hl7InputStreamReader.read(f)) {
			{
				String hl7msg = readFile(f);
				System.out.println("WRITE:\n"+hl7msg);
				
				llpw.writeMessage(hl7msg);
				
				String ack = llpr.getMessage();
				System.out.println("READ:\n"+ack);
			}
			llpr.close();

			sp.stop();
			while (sp.isRunning()) {
				try {
					Thread.sleep(4000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
*/
	}

}
