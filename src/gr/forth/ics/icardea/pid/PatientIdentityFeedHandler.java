package gr.forth.ics.icardea.pid;
import gr.forth.ics.icardea.mllp.HL7MLLPServer;

import java.io.IOException;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.app.DefaultApplication;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.v231.segment.PID;
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
		Terser t = new Terser(msg);
		Message resp = null;
		try {
			String trigEvent = t.get("/MSH-9-2");
			Segment s = t.getSegment("/PID");
			PID pid = (PID) s;
			System.out.println("PID:"+pid);
			iCARDEA_Patient tr = iCARDEA_Patient.create_from_PID(pid);
			
			if ("A08".equals(trigEvent)) {
				if (tr.ids.length == 0)
					throw new HL7Exception("No identifiers given", HL7Exception.DATA_TYPE_ERROR);
				StorageManager.getInstance().update_pid(tr);
			}
			else 
				StorageManager.getInstance().insert_pid(tr);
			resp = msg.generateACK();
		} catch (HL7Exception e) {
			e.printStackTrace();
			try {
				resp = msg.generateACK("AE", e);
			} catch (HL7Exception ex) {
				ex.printStackTrace();
				throw new ApplicationException(ex.getMessage(), ex);
			} catch (IOException ex) {
				ex.printStackTrace();
				throw new ApplicationException(ex.getMessage(), ex);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new ApplicationException(e.getMessage(), e);
		}
		try {
			System.out.println("Sending:\n"+resp.encode());
		} catch (HL7Exception e) {
		}
		
		return resp;
	}
}

