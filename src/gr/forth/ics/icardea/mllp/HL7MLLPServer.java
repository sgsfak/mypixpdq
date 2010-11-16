package gr.forth.ics.icardea.mllp;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import java.nio.charset.Charset;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.app.MessageTypeRouter;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.DefaultXMLParser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;


import nu.xom.Serializer;
import nu.xom.converters.DOMConverter;

class MLLP_Delimiters {
	// See http://www.corepointhealth.com/resource-center/hl7-resources/mlp-minimum-layer-protocol
	public static final byte MLLP_HEADER = 0x0b;
	public static final byte MLLP_TRAILER1 = 0x1c;
	public static final byte MLLP_TRAILER2 = 0x0d;
}
class MLLPDecoder extends FrameDecoder {
    @Override
    protected Object decode(ChannelHandlerContext ctx, 
    		Channel channel, ChannelBuffer buffer) {

		System.out.println("Decoding...");
    	int last = buffer.writerIndex() - 1;
        if (buffer.getByte(last) != MLLP_Delimiters.MLLP_TRAILER2) {
            return null;
        }
        buffer.writerIndex(last); // eat MLLP_END2
        --last;
        if (buffer.getByte(last) == MLLP_Delimiters.MLLP_TRAILER1)
            buffer.writerIndex(last); // eat MLLP_END1
        	
        int first = buffer.readerIndex();
        if (buffer.getByte(first) == MLLP_Delimiters.MLLP_HEADER) {
            buffer.readByte(); // increment readerIndex, eat MLLP_START
        }
        Message msg = null;
		try {
			PipeParser pp = new PipeParser();
			msg = pp.parse(buffer.toString(Charset.forName("UTF-8")));
			buffer.readerIndex(buffer.writerIndex());
		} catch (HL7Exception e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}
        return msg;
    }
}
class MLLPEncoder extends SimpleChannelDownstreamHandler {
	@Override
	public void	writeRequested(ChannelHandlerContext ctx, MessageEvent e) {
		Message res = (Message) e.getMessage();
		System.out.println("Encoding...");
		try {
			PipeParser pp = new PipeParser();
			byte[] encoded = pp.encode(res).getBytes(Charset.forName("UTF-8"));
			ChannelBuffer outbuf = ChannelBuffers.buffer(encoded.length + 3);
			
			outbuf.writeByte(MLLP_Delimiters.MLLP_HEADER);
			outbuf.writeBytes(encoded);
			outbuf.writeByte(MLLP_Delimiters.MLLP_TRAILER1);
			outbuf.writeByte(MLLP_Delimiters.MLLP_TRAILER2);
			Channels.write(ctx, e.getFuture(), outbuf);
			
		} catch (HL7Exception ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}
	}
}
class HL7MsgHandler extends SimpleChannelUpstreamHandler {	
	private MessageTypeRouter router_;
	public HL7MsgHandler(MessageTypeRouter router) {
		this.router_ = router;
	}
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent ev) {
		
		Message res = null;
		try {
			Message msg = (Message) ev.getMessage();
			
			res = router_.processMessage(msg);
			
			ev.getChannel().write(res);
		} catch (ApplicationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
		// Close the connection when an exception is raised.
		e.getCause().printStackTrace();
		e.getChannel().close();
	}
}

class TestApp extends ca.uhn.hl7v2.app.DefaultApplication {
	public Message processMessage(Message msg) {
		 System.out.println("Got message "+msg.getName());
		 System.out.println("Version "+msg.getVersion());
		 Message res = null;
		 try {/*
				Segment msh = (Segment) msg.get("MSH");
				Composite msh_9 = (Composite)msh.getField(9, 0);
				String messageType = ((Primitive) msh_9.getComponent(0)).getValue();
				String triggerEvent = ((Primitive) msh_9.getComponent(1)).getValue();
				*/
				Terser t = new Terser(msg);
				String messageType = t.get("/MSH-9-1");
				String triggerEvent = t.get("/MSH-9-2");
				System.out.println("MESSAGE CODE = "+messageType + " TRIGGER EVENT=" + triggerEvent);
				
				DefaultXMLParser xmlp = new DefaultXMLParser(); 
				nu.xom.Document doc = DOMConverter.convert(xmlp.encodeDocument(msg));
				System.out.println("HL72XML:");
				Serializer serializer = new Serializer(System.out, "UTF-8");
				serializer.setIndent(4);
				serializer.setMaxLength(64);
				serializer.write(doc);
				serializer.flush();
		          
				System.out.println("Generating ACK");
				res = makeACK((Segment) msg.get("MSH"));
		 }
		 catch (IOException e) {
			 // TODO Auto-generated catch block
			 e.printStackTrace();
		 } catch (HL7Exception e) {
			 // TODO Auto-generated catch block
			 e.printStackTrace();
		 } 
		 return res;
	 }
}
public class HL7MLLPServer {
	public static final int DEFAULT_PORT = 2575;
	private int port_;
	private ServerBootstrap bootstrap_;
	private MessageTypeRouter router_;
	
	public HL7MLLPServer() {
		this.router_ = new MessageTypeRouter();
	}
	public void init() {
		init(DEFAULT_PORT);
	}
	public void init(int port) {
		this.port_ = port;
		// Configure the server.
		this.bootstrap_ = new ServerBootstrap(
				new NioServerSocketChannelFactory(
						Executors.newCachedThreadPool(),
						Executors.newCachedThreadPool(),
						10));

		// Set up the pipeline factory.
		this.bootstrap_.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(
						new MLLPDecoder(),
						new HL7MsgHandler(router_),
						new MLLPEncoder()
						);
			}
		});
	}
	/**
	 * Registers the given application to handle messages corresponding to the given type
	 * and trigger event.  Only one application can be registered for a given message type
	 * and trigger event combination.  A repeated registration for a particular combination 
	 * of type and trigger event over-writes the previous one.  Note that the wildcard "*" 
	 * for messageType or triggerEvent means any type or event, respectively.   
	 */
	public void registerApplication(String messageType, String triggerEvent, Application handler) {
     synchronized(this.router_) {
    	 this.router_.registerApplication(messageType, triggerEvent, handler);
     }
	}
	
	public void run() {
		System.out.println("Start listening...");
		// Bind and start to accept incoming connections.
		this.bootstrap_.bind(new InetSocketAddress(this.port_));		
	}
	
	public static void main(String[] args) throws Exception {
		HL7MLLPServer s = new HL7MLLPServer();
		s.registerApplication("*", "*", new TestApp()/* new ca.uhn.hl7v2.app.DefaultApplication() */);
		s.init();
		s.run();

	}
}
