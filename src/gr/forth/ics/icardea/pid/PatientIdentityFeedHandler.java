package gr.forth.ics.icardea.pid;
import gr.forth.ics.icardea.mllp.HL7MLLPServer;

import java.io.IOException;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.app.DefaultApplication;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.v231.segment.PID;
import ca.uhn.hl7v2.model.v231.message.ACK;
import ca.uhn.hl7v2.model.v25.message.RSP_K21;
import ca.uhn.hl7v2.util.Terser;

/**
 * Patient Identity Feed Handler (ITI TF-2a / 3.8)
 *  
 * This is for the Patient Identity Management – Admit/Register or Update
 * Patient. The following events from a Patient Identity Source Actor will
 * trigger one of the Admit/Register or Update messages:
 *  - A01 – Admission of an in-patient into a facility
 *  - A04 – Registration of an outpatient for a visit of the facility
 *  - A05 – Pre-admission of an in-patient (i.e., registration of patient
 * information ahead of actual admission).
 * 
 * Changes to patient demographics (e.g., change in patient name, patient
 * address, etc.) shall trigger the following Admit/Register or Update message:
 *  - A08 – Update Patient Information
 *  
 * The Patient Identity Feed transaction is conducted by the HL7 ADT message.
 */
final class PatientIdentityFeedHandler extends DefaultApplication {
	
	public void register(HL7MLLPServer s) {
		s.registerApplication("ADT", "A01", this);
		s.registerApplication("ADT", "A04", this);
		s.registerApplication("ADT", "A05", this);
		s.registerApplication("ADT", "A08", this);
	}

	public boolean canProcess(Message msg) {
		Terser t = new Terser(msg);
		try {
			Segment s = t.getSegment("/PID");
			if (!(s instanceof PID))
				return false;
			String trigEvent = t.get("/MSH-9-2");
			if (trigEvent != "A01" && trigEvent != "A04" && trigEvent != "A05"
					&& trigEvent != "A08")
				return false;
		} catch (HL7Exception e) {
			return false;
		}
		return true;
	}
	/**
	 * See ITI-vol2a, 3.8
	 */
	public Message processMessage(Message msg) throws ApplicationException{
		Terser terser = new Terser(msg);
		ACK a = null;
		try {
			String trigEvent = terser.get("/MSH-9-2");
			a = (ACK) makeACK( (Segment) msg.get("MSH"));
			a.getMSH().getMsh9_MessageType().parse("ACK^"+trigEvent);
			
			HL7Utils.fillResponseHeader(terser.getSegment("/MSH"), a.getMSH());
			
			Segment s = terser.getSegment("/PID");
			PID pid = (PID) s;
			Type[] tt = pid.getField(3);
			for (Type t: tt) {
				AssigningAuthority auth = null;
				String tons = Terser.getPrimitive(t, 4, 1).getValue();
				if (tons == null || "".equals(tons)) {
					String uid = Terser.getPrimitive(t, 4, 2).getValue();
					String uid_type = Terser.getPrimitive(t, 4, 3).getValue();
					auth = AssigningAuthority.find_by_uid(uid, uid_type);
				}
				else
					auth = AssigningAuthority.find(tons);
				if (auth == null) {
					HL7Exception ex = new HL7Exception("Unsupported authority:"+pid.getField(3, 0).encode(), HL7Exception.UNKNOWN_KEY_IDENTIFIER);
					throw ex;
				}
			}
			
			System.out.println("PID:"+pid.encode());
			iCARDEA_Patient tr = iCARDEA_Patient.create_from_PID(pid);
			
			if ("A08".equals(trigEvent)) {
				if (tr.ids.size() == 0)
					throw new HL7Exception("No identifiers given", HL7Exception.DATA_TYPE_ERROR);
				// iCARDEA_Patient m = StorageManager.getInstance().retrieve(tr.ids[0]);
				
				StorageManager.getInstance().update_pid(tr);
			}
			else 
				StorageManager.getInstance().insert_pid(tr);
			a.getMSA().getMsa2_MessageControlID().setValue(terser.get("/MSH-10"));
			a.getMSA().getMsa1_AcknowledgementCode().setValue("AA");
			
		} catch (HL7Exception e) {
			e.printStackTrace();
			try {
				a.getMSA().getMsa1_AcknowledgementCode().setValue("AE");
				// a.getMSA().getMsa3_TextMessage().setValue(e.getMessage());
				// HL7Utils.fillErrHeader(a, e);
			} catch (HL7Exception ex) {
				ex.printStackTrace();
				throw new ApplicationException(ex.getMessage(), ex);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new ApplicationException(e.getMessage(), e);
		}
		try {
			System.out.println("Sending:\n"+a.encode());
		} catch (HL7Exception e) {
		}
		
		return a;
	}
}

