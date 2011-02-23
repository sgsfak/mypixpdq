package gr.forth.ics.icardea.pid;

import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.primitive.CommonTS;
import ca.uhn.hl7v2.util.Terser;

final public class HL7Utils {
	public final static String ENC_CHARS = "^~\\&";
	public final static String FLD_SEP = "|";

	public static void createHeader(Segment msh)
	throws HL7Exception, IOException {
		createHeader(msh, "2.5");
	}
	public static void createHeader(Segment msh, String version)
			throws HL7Exception {
		if (!msh.getName().equals("MSH"))
			throw new HL7Exception("Need MSH segments.  Got "
					+ msh.getName());
		Terser.set(msh, 2, 0, 1, 1, ENC_CHARS);
		Terser.set(msh, 1, 0, 1, 1, FLD_SEP);
		Terser.set(msh, 1, 0, 1, 1, FLD_SEP);

		msh.getField(3, 0).parse(PatientIndex.app_name);
		msh.getField(4, 0).parse(PatientIndex.fac_name);
		
		GregorianCalendar now = new GregorianCalendar();
		now.setTime(new Date());
		Terser.set(msh, 7, 0, 1, 1, CommonTS.toHl7TSFormat(now));
		Terser.set(msh, 10, 0, 1, 1, java.util.UUID.randomUUID().toString());
		Terser.set(msh, 11, 0, 1, 1, "P");
		Terser.set(msh, 12, 0, 1, 1, version);
	}
		
	/* shameless copy from HAPI's DefaultApplication */
	public static void fillResponseHeader(Segment inbound, Segment outbound)
			throws HL7Exception {
		if (!inbound.getName().equals("MSH")
				|| !outbound.getName().equals("MSH"))
			throw new HL7Exception("Need MSH segments.  Got "
					+ inbound.getName() + " and " + outbound.getName());

		// get MSH data from incoming message ...
		String encChars = Terser.get(inbound, 2, 0, 1, 1);
		String fieldSep = Terser.get(inbound, 1, 0, 1, 1);
		String procID = Terser.get(inbound, 11, 0, 1, 1);
		String version = null;
		try {
			version = Terser.get(inbound, 12, 0, 1, 1);
		} catch (HL7Exception e) {}
		if (version == null)
			version = "2.5";
		// populate outbound MSH using data from inbound message ...
		Terser.set(outbound, 2, 0, 1, 1, encChars);
		Terser.set(outbound, 1, 0, 1, 1, fieldSep);
		outbound.getField(3, 0).parse(PatientIndex.app_name);
		outbound.getField(4, 0).parse(PatientIndex.fac_name);
		outbound.getField(5, 0).parse(inbound.getField(3, 0).encode());
		outbound.getField(6, 0).parse(inbound.getField(4, 0).encode());
		
		GregorianCalendar now = new GregorianCalendar();
		now.setTime(new Date());
		Terser.set(outbound, 7, 0, 1, 1, CommonTS.toHl7TSFormat(now));
		Terser.set(outbound, 10, 0, 1, 1, java.util.UUID.randomUUID().toString());
		Terser.set(outbound, 11, 0, 1, 1, procID);
		Terser.set(outbound, 12, 0, 1, 1, version);
	}
	public static void fillErrHeader(Message msg, HL7Exception e) throws HL7Exception {
		Terser t = new Terser(msg);
		//resp.getERR().getErr2_ErrorLocation(0).getErl1_SegmentID().setValue(e.getSegmentName());
		t.set("/ERR-2-1", e.getSegmentName());
		// resp.getERR().getErr2_ErrorLocation(0).getErl2_SegmentSequence().setValue(""+e.getSegmentRepetition());
		t.set("/ERR-2-2", ""+e.getSegmentRepetition());

		// resp.getERR().getErr2_ErrorLocation(0).getErl3_FieldPosition().setValue(""+e.getFieldPosition());
		t.set("/ERR-2-3", ""+e.getFieldPosition());
		// resp.getERR().getErr2_ErrorLocation(0).getErl4_FieldRepetition().setValue("1");
		t.set("/ERR-2-4", "1");
		// resp.getERR().getErr3_HL7ErrorCode().getCwe1_Identifier().setValue(""+e.getErrorCode());
		t.set("/ERR-3-1", ""+e.getErrorCode());
		// resp.getERR().getErr3_HL7ErrorCode().getCwe2_Text().setValue(e.getMessage());
		t.set("/ERR-3-2", e.getMessage());
		// resp.getERR().getErr4_Severity().setValue("E");
		t.set("/ERR-4", "E");
	}
}
