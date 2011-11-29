package gr.forth.ics.icardea.mllp;

import java.nio.charset.Charset;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;

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