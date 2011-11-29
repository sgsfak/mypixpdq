package gr.forth.ics.icardea.mllp;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import java.nio.charset.Charset;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.app.MessageTypeRouter;
import ca.uhn.hl7v2.app.Responder;
import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Primitive;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.parser.DefaultXMLParser;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.ValidationContext;
import ca.uhn.hl7v2.validation.impl.DefaultValidation;


import nu.xom.Serializer;
import nu.xom.converters.DOMConverter;

class MLLP_Delimiters {
	// See http://www.corepointhealth.com/resource-center/hl7-resources/mlp-minimum-layer-protocol
	public static final byte MLLP_HEADER = 0x0b;
	public static final byte MLLP_TRAILER1 = 0x1c;
	public static final byte MLLP_TRAILER2 = 0x0d;
}
class MLLPDecoder extends FrameDecoder {
	static Logger logger = Logger.getLogger(MLLPDecoder.class);
	private ValidationContext validator_;
	MLLPDecoder(ValidationContext c) {
		this.validator_ = c;
	}
    @Override
    protected Object decode(ChannelHandlerContext ctx, 
    		Channel channel, ChannelBuffer buffer) {
    	if (!buffer.readable())
    		return null;
    	int last = buffer.writerIndex() - 1;
        if (buffer.getByte(last) != MLLP_Delimiters.MLLP_TRAILER2) {
            return null;
        }
        buffer.writerIndex(last); // eat MLLP_TRAILER2
        --last;
        if (buffer.getByte(last) == MLLP_Delimiters.MLLP_TRAILER1)
            buffer.writerIndex(last); // eat MLLP_TRAILER1
        	
        int first = buffer.readerIndex();
        if (buffer.getByte(first) == MLLP_Delimiters.MLLP_HEADER) {
            buffer.readByte(); // increment readerIndex, eat MLLP_HEADER
        }
        Message msg = null;
		try {
			GenericParser pp = new GenericParser();
			if (this.validator_ != null)
				pp.setValidationContext(this.validator_);
                        String data = buffer.toString(Charset.forName("UTF-8"));
                        logger.info("Recvd HL7 data:\n" + data);
			msg = pp.parse(data);
			buffer.readerIndex(buffer.writerIndex());
		} catch (HL7Exception ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}
        return msg;
    }
}
class MLLPEncoder extends SimpleChannelDownstreamHandler {
	static Logger logger = Logger.getLogger(MLLPEncoder.class);
	@Override
	public void	writeRequested(ChannelHandlerContext ctx, MessageEvent e) {
		Message res = (Message) e.getMessage();
		try {
			// GenericParser pp = new GenericParser();
                        String data = res.getParser().encode(res);
                        logger.info("Sending HL7 data:\n" + data);
                        
			byte[] encoded = data.getBytes(Charset.forName("UTF-8"));
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
	private ChannelGroup chanGrp_;
	public HL7MsgHandler(MessageTypeRouter router, ChannelGroup chanGrp) {
		this.router_ = router;
		this.chanGrp_ = chanGrp;
	}
	@Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
      // HERE: Add all accepted channels to the group
      //       so that they are closed properly on shutdown
      //       If the added channel is closed before shutdown,
      //       it will be removed from the group automatically.
	  this.chanGrp_.add(ctx.getChannel());
    } 
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent ev) {
		
		Message res = null;
		Message msg = (Message) ev.getMessage();
		try {
			res = router_.processMessage(msg);
			
		} catch (ApplicationException e) {
			try {				
				String err = Responder.logAndMakeErrorMessage(e, (Segment)msg.get("MSH"), msg.getParser(), null);
				res = msg.getParser().parse(err);
			} catch (HL7Exception e1) {
				e1.printStackTrace();
			}
		} 
		if (res != null)
			ev.getChannel().write(res);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
		// Close the connection when an exception is raised.
		// e.getCause().printStackTrace();
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
	static Logger logger = Logger.getLogger(HL7MLLPServer.class);

	public static final int DEFAULT_PORT = 2575;
	private int port_;
	private ValidationContext validator_ = null;
	private NioServerSocketChannelFactory chanFactory_;
	public final ChannelGroup chanGrp_ = new DefaultChannelGroup("HL7MLLPServer");
	private ServerBootstrap mllp_bootstrap_;
	private MessageTypeRouter router_;

	public int port() {
		return this.port_;
	}
	static class HL7MLLPServerShutdown extends Thread {
	    private HL7MLLPServer server_;
	    public HL7MLLPServerShutdown(HL7MLLPServer server) {
	        super();
	        this.server_ = server;
	    }
	    public void run() {
			// logger.info("SHUTTING DOWN server!");
	        try {
	            this.server_.stop();
	        } catch (Exception ee) {
	            ee.printStackTrace();
	        }
	    }
	}
	public HL7MLLPServer() {
		this.router_ = new MessageTypeRouter();
	}
	public void init() {
		init(DEFAULT_PORT);
	}
	public void init(int port) {
		init(port, null);
	}
	public void init(int port, ValidationContext c) {
		this.port_ = port;
		this.validator_ = c;
		// Configure the server.
		this.chanFactory_ = new NioServerSocketChannelFactory(
				Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool()
				);
		this.mllp_bootstrap_ = new ServerBootstrap(this.chanFactory_);

		// Set up the pipeline factory.
		
		this.mllp_bootstrap_.setPipelineFactory(new ChannelPipelineFactory() {
			public ChannelPipeline getPipeline() throws Exception {
				return Channels.pipeline(
						new MLLPDecoder(validator_),
						new HL7MsgHandler(router_,chanGrp_),
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
		logger.info("Starting MLLP server (TCP port: "+ this.port_ + ")...");
		// Bind and start to accept incoming connections.
		Channel bChan = this.mllp_bootstrap_.bind(new InetSocketAddress(this.port_));
		this.chanGrp_.add(bChan);
		Runtime.getRuntime().addShutdownHook(new HL7MLLPServerShutdown(this));
	}
	public void stop() {
		logger.info("Stopping MLLP server...");
		this.chanGrp_.close().awaitUninterruptibly();
		this.chanFactory_.releaseExternalResources();
	}
	public static void main(String[] args) throws Exception {
		HL7MLLPServer s = new HL7MLLPServer();
		s.registerApplication("*", "*", new TestApp()/* new ca.uhn.hl7v2.app.DefaultApplication() */);
		s.init(HL7MLLPServer.DEFAULT_PORT, new ca.uhn.hl7v2.validation.impl.NoValidation());
		s.run();
	}
}
