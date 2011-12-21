package gr.forth.ics.icardea.mllp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Security;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.log4j.Logger;
/*
import org.jboss.netty.channel.Channels;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
*/
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.app.ConnectionHub;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.llp.MinLowerLayerProtocol;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;

/*
class MLLPClientHandler extends SimpleChannelUpstreamHandler {

	private static final Logger logger = Logger.getLogger(
			MLLPClientHandler.class.getName());

	@Override
	public void handleUpstream(
			ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
		if (e instanceof ChannelStateEvent) {
			logger.info(e.toString());
		}
		super.handleUpstream(ctx, e);
	}

	@Override
	public void messageReceived(
			ChannelHandlerContext ctx, MessageEvent e) {
		// Print out the line received from the server.
		// System.err.println(e.getMessage());
	}

	@Override
	public void exceptionCaught(
			ChannelHandlerContext ctx, ExceptionEvent e) {
		logger.warn(
				"Unexpected exception from downstream."+  e.getCause());
		e.getChannel().close();
	}
}

class MLLPClientPipelineFactory implements ChannelPipelineFactory {
	public ChannelPipeline getPipeline() throws Exception {
		// Create and configure a new pipeline for a new channel.
		ChannelPipeline p = Channels.pipeline();
		p.addLast("encoder", new MLLPEncoder());
		p.addLast("decoder", new MLLPDecoder(null));
		p.addLast("logic",   new MLLPClientHandler());
		return p;
	}
}
*/
class ForwardHandler implements Runnable {
	static Logger logger = Logger.getLogger(ForwardHandler.class);
	Message msg;
	InetSocketAddress to;
	boolean secure;
	ForwardHandler(InetSocketAddress to, Message msg, boolean secure) {
		this.to = to;
		this.msg = msg;
		this.secure = secure;
	}
	public void run() {
		PipeParser p = new PipeParser();
		Connection connection = null;
		Message response = null;
		try {
			Socket socket = null;
			if (this.secure) {
				SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
				SSLSocket sslsocket = (SSLSocket) sslsocketfactory.createSocket(to.getHostName(), to.getPort());
				sslsocket.startHandshake();
				socket = sslsocket;
			}
			else
				socket = new Socket(to.getHostName(), to.getPort());
			connection = new Connection(p, new MinLowerLayerProtocol(), socket);
			response = connection.getInitiator().sendAndReceive(msg);
		} catch (HL7Exception e) {
			logger.warn(e.getMessage());
		} catch (LLPException e) {
			logger.warn(e.getMessage());
		} catch (IOException e) {
			logger.warn(e.getMessage());
		}
		finally {
			if (connection != null) 
				connection.close();
		}
	}
}
class ConnectionInfo {
	InetSocketAddress hp;
	boolean secure;
	
	ConnectionInfo(InetSocketAddress hp, boolean secure) {
		this.hp = hp;
		this.secure = secure;
	}
}
public class HL7MLLPClient {
	static Logger logger = Logger.getLogger(HL7MLLPClient.class);
	// private ChannelFactory chanFact_;
	private ArrayList<ConnectionInfo> listeners_ = new ArrayList<ConnectionInfo>();
	private ExecutorService pool = Executors.newCachedThreadPool();
	public HL7MLLPClient() {
		/*
		// Configure the client.
		this.chanFact_ = 
			new NioClientSocketChannelFactory(
					Executors.newCachedThreadPool(),
					Executors.newCachedThreadPool());
					*/
		Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
	}
	public void add_listener(String host, int port, boolean secure) {
		logger.info("adding " + (secure ? "ssl" : "") + " listener " + host + ":" + port); 
		this.listeners_.add(new ConnectionInfo(new InetSocketAddress(host, port), secure));
	}
	public void send(final Message msg) {
		try {
			logger.info("Sending Update Notification:\n" + msg.encode());
		} catch (HL7Exception e) {
		}
		for (ConnectionInfo to: this.listeners_) {
			pool.execute(new ForwardHandler(to.hp, msg, to.secure));
			/*
		// TODO: make parallel connections
			// Set up the pipeline factory.
			ClientBootstrap b = new ClientBootstrap(this.chanFact_);
			b.setPipelineFactory(new MLLPClientPipelineFactory());

			// Start the connection attempt.
			ChannelFuture future = b.connect(to);

			// Wait until the connection attempt succeeds or fails.
			Channel channel = future.awaitUninterruptibly().getChannel();
			if (!future.isSuccess()) {
				logger.warn(future.getCause().getMessage());
				return;
			}

			ChannelFuture lastWriteFuture = channel.write(msg);
			channel.getCloseFuture().awaitUninterruptibly();

			// Wait until all messages are flushed before closing the channel.
			if (lastWriteFuture != null) {
				lastWriteFuture.awaitUninterruptibly();
			}

			// Close the connection.  Make sure the close operation ends because
			// all I/O operations are asynchronous in Netty.
			channel.close().awaitUninterruptibly();
			*/
		}
	}
	public void stop() {
		// Shut down all thread pools to exit.
		// this.chanFact_.releaseExternalResources();
		this.pool.shutdown();
	}

	public static void main(String args[]) {
		String s = "MSH|^~\\&|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|iCARDEA|iCARDEA|20090224104152-0600||ADT^A01^ADT_A01|8686183982575368499|P|2.5||20090224104152-0600\r"+
		"PID|||103^^^icardea~o103^^^ORBIS~c103^^^CIED||KABAK^YILDIRAY||19790311|M|||||3122101763^PRN~5337186789^PRS~yildiray@srdc.com.tr^NET\r"+
		"PV1||I\r";


		Message msg = null;
		try {
			PipeParser pp = new PipeParser();
			msg = pp.parse(s);
		} catch (HL7Exception ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}

		HL7MLLPClient c = new HL7MLLPClient();
		c.add_listener("iapetus", 2825, false);
		c.send(msg);
		c.stop();
	}
}
