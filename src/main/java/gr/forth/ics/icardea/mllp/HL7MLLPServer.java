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
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;

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


class HL7MsgHandler extends SimpleChannelUpstreamHandler {	
	private MessageTypeRouter router_;
	private ChannelGroup chanGrp_;
	public HL7MsgHandler(MessageTypeRouter router, ChannelGroup chanGrp) {
		this.router_ = router;
		this.chanGrp_ = chanGrp;
	}
	/*
	@Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
      // HERE: Add all accepted channels to the group
      //       so that they are closed properly on shutdown
      //       If the added channel is closed before shutdown,
      //       it will be removed from the group automatically.
	  this.chanGrp_.add(ctx.getChannel());
    } 
    */
	@Override
	public void channelConnected(
			ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {

		// Get the SslHandler in the current pipeline.
		// We (possibly) added it in SecureChatPipelineFactory.
		final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
		if (sslHandler != null) {
			// Get notified when SSL handshake is done.
			ChannelFuture handshakeFuture = sslHandler.handshake();
			handshakeFuture.addListener(new SSLFutureListener(this.chanGrp_));
		}
		else
			this.chanGrp_.add(ctx.getChannel());
	}
	@Override
	public void channelDisconnected(
			ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		// Unregister the channel from the global channel list
		// so the channel does not receive messages anymore.
		this.chanGrp_.remove(e.getChannel());
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
	
	 private static final class SSLFutureListener implements ChannelFutureListener {

	      private ChannelGroup chanGrp_;

	      SSLFutureListener(ChannelGroup chanGrp) {
	          this.chanGrp_ = chanGrp;
	      }

	      public void operationComplete(ChannelFuture future) throws Exception {
	          if (future.isSuccess()) {
	              // Now the session is secured!
	              // Register the channel to the global channel list
	              // so the channel received the messages from others.
	        	  chanGrp_.add(future.getChannel());
	          } else {
	              future.getChannel().close();
	          }
	      }
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

class HL7MLLPServerPipelineFactory implements ChannelPipelineFactory {
	private final HL7MLLPServer server_;
	HL7MLLPServerPipelineFactory(HL7MLLPServer server) {
		this.server_ = server;
	}

	public ChannelPipeline getPipeline() throws Exception {
		ChannelPipeline pipeline = Channels.pipeline();
		if (this.server_.isSecure_) {
			SSLEngine engine =
				SslContextFactory.getInstance().getServerContext().createSSLEngine();
			engine.setUseClientMode(false); // We are the Server!!!
			// these are proposed in the ATNA wiki
			String[] suites = 
			{
					"TLS_RSA_WITH_AES_128_CBC_SHA", 
					"TLS_DHE_DSS_WITH_AES_128_CBC_SHA"  // Diffie Hellman Key Exchange using DSS Certificate
			};
			String enabledProtocols[] = { "TLSv1" };
			engine.setEnabledCipherSuites(suites);
			engine.setEnabledProtocols(enabledProtocols);
			
			pipeline.addLast("ssl", new SslHandler(engine));
		}
		pipeline.addLast("decoder",new MLLPDecoder(this.server_.validator_));
		pipeline.addLast("framer", new HL7MsgHandler(this.server_.router_,this.server_.chanGrp_));
		pipeline.addLast("encoder",new MLLPEncoder());
		return pipeline;
	}
}
public class HL7MLLPServer {
	static Logger logger = Logger.getLogger(HL7MLLPServer.class);

	public static final int DEFAULT_PORT = 2575;
	private int port_;
	ValidationContext validator_ = null;
	NioServerSocketChannelFactory chanFactory_;
	public final ChannelGroup chanGrp_ = new DefaultChannelGroup("HL7MLLPServer");
	private ServerBootstrap mllp_bootstrap_;
	MessageTypeRouter router_;
	boolean isSecure_ = false;

	public int port() {
		return this.port_;
	}
	public boolean usesTLS() {
		return this.isSecure_;
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
	public void init_with_tls(int port, ValidationContext c, String keystoreFileName, String keystorePass) {

		this.isSecure_ = true;
		SslContextFactory.getInstance().init(keystoreFileName, keystorePass);
		this.init(port, c);
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
		
		this.mllp_bootstrap_.setPipelineFactory(new HL7MLLPServerPipelineFactory(this));
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
		logger.info("Starting MLLP server (TCP port: "+ this.port_ + ") SSL: "+ 
				(this.isSecure_ ? " YES " : "NO"));
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
