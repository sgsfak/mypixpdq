package gr.forth.ics.icardea.mllp;

import java.nio.charset.Charset;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.validation.ValidationContext;

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