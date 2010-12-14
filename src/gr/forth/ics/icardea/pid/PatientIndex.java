package gr.forth.ics.icardea.pid;

import java.io.IOException;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.DefaultApplication;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.model.Message;

import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.model.v25.segment.QPD;
import ca.uhn.hl7v2.util.Terser;

import gr.forth.ics.icardea.mllp.HL7MLLPServer;

final class CatchAllHandler extends DefaultApplication {

	public Message processMessage(Message msg) throws ApplicationException{
		try {
			Terser t = new Terser(msg);
	        String messageType = t.get("/MSH-9-1");
	        String triggerEvent = t.get("/MSH-9-2");
			System.out.println("Got message '"+messageType+"^" + triggerEvent +"'. I don't handle such messages!");
			return msg.generateACK("AR", 
					new HL7Exception("Cannot handle this type of message", HL7Exception.UNSUPPORTED_MESSAGE_TYPE));
		} catch (HL7Exception e) {
			e.printStackTrace();
			throw new ApplicationException(e.getMessage(), e);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ApplicationException(e.getMessage(), e);
		}
	}
}


final class  Tests {
// Insert (ADT_A01)
//MSH|^~\&|OTHER_IBM_BRIDGE_TLS|IBM|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|20090224104152-0600||ADT^A01^ADT_A01|8686183982575368499|P|2.3.1||20090224104152-0600
//PID|||103^^^icardea~o103^^^ORBIS~i103^^^CIED||SINGLETON1^MARION1||19661109|F
//PV1||I
	
	// Update (A08)
//	MSH|^~\&|OTHER_IBM_BRIDGE_TLS|IBM|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|20090224104204-0600||ADT^A08^ADT_A01|9241351356666182528|P|2.3.1||20090224104204-0600
//	PID|||102^^^IBOT&1.3.6.1.4.1.21367.2009.1.2.370&ISO||OTHER_IBM_BRIDGE^MARION||19661109|F
//	PV1||O
	
// PIX Query
//MSH|^~\&|PACS_FUJIFILM|FUJIFILM|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|20090223144546||QBP^Q23^QBP_Q21|1235421946|P|2.5|||||||
//QPD|IHEPIXQuery|Q231235421946|103^^^icardea|^^^ORBIS
//RCP|I|
	
// PDQ - Produces something like:
//MSH|^~\&|testclient^icardea|SALK^icardea|PID^icardea|iCARDEAPlatform|||QBP^Q22^QBP_Q21|blabla||2.5||||||UNICODE UTF-8
//QPD|IHE PDQ Query|query-1|@PID.5.1.1^Duck~@PID.5.2^MARION
//RCP|I
	
	public static Message pdq(String fam_name, String giv_name, String sex, String dob) 
		throws HL7Exception {

		QBP_Q21 a = new QBP_Q21();		
		// Construct MSH according to C2.2 of ITI TF-2x
		HL7Utils.createHeader(a.getMSH(), "2.5");
		a.getMSH().getMsh9_MessageType().parse("QBP^Q22^QBP_Q21");
		// Set UTF-8 character set? See:
		// http://wiki.hl7.org/index.php?title=Character_Set_used_in_v2_messages
		a.getMSH().getCharacterSet(0).setValue("UNICODE UTF-8");
		
		// Set Sending app identification
		a.getMSH().getSendingApplication().parse("testclient^icardea");
		a.getMSH().getSendingFacility().parse("SALK^icardea");
		// Set Receiving app identification
		a.getMSH().getReceivingApplication().parse("PID^icardea");
		a.getMSH().getReceivingFacility().parse("iCARDEAPlatform");
		QPD qpd = a.getQPD();
		qpd.getQpd1_MessageQueryName().parse("IHE PDQ Query");
		qpd.getQpd2_QueryTag().setValue("query-1"); // A query identifier
		
		final int QIP_FLD_NUM = 3;
		int k = 0;
		if (fam_name!=null)
			qpd.getField(QIP_FLD_NUM, k++).parse(iCARDEA_Patient.FNAME_SEG_FLD + "^"+fam_name);
		if (giv_name!=null)
			qpd.getField(QIP_FLD_NUM, k++).parse(iCARDEA_Patient.GNAME_SEG_FLD + "^"+giv_name);
		if (sex !=null)
			qpd.getField(QIP_FLD_NUM, k++).parse(iCARDEA_Patient.SE×_SEG_FLD + "^" + sex);
		if (dob != null)
			qpd.getField(QIP_FLD_NUM, k++).parse(iCARDEA_Patient.DOB_SEG_FLD + "^" + dob);
		
		int num = qpd.getField(QIP_FLD_NUM).length;
		for (int i=0; i<num ;++i) {
			String nv = qpd.getField(QIP_FLD_NUM, i).encode();
			int ind = nv.indexOf('^');
			String trait = nv.substring(0, ind);
			String val = nv.substring(ind+1);
			System.out.println(trait+"="+val);
		}
		a.getRCP().getRcp1_QueryPriority().setValue("I"); // immediate mode response
		return a;
	}
}

public class PatientIndex {
	public static final java.io.PrintStream outs = System.out; 
	public static void usage() {
		outs.println("java PatientIndex <config.ini>\n") ;
	}

	public static String app_name = "PID"; 
	public static String fac_name = "icardeaPlatform"; 
	public void run(String[] args) throws Exception {
		if (args.length != 1) {
			usage();
			return;
		}
		IniConfig cfg = new IniConfig();
		cfg.load(args[0]);

		int port = cfg.getKeyIntValue("server", "port", 2575);
		String dbHost = cfg.getKeyValue("server", "mongo_host", "localhost");
		PatientIndex.app_name = cfg.getKeyValue("server", "application_name", app_name);
		PatientIndex.fac_name = cfg.getKeyValue("server", "facility_name", fac_name);
		

		for (String sec: cfg.getSections().keySet()) {
			if (sec.equalsIgnoreCase("server"))
				continue;
			String ns = cfg.getKeyValue(sec, "namespace");
			if (AssigningAuthority.find(ns) != null) {
				outs.println("Duplicate namespace in ini file:"+ns+" exiting..");
				return;
			}
			String uid = cfg.getKeyValue(sec, "universal_id", "");
			String uid_type = cfg.getKeyValue(sec, "universal_type", "");
			AssigningAuthority.add(new AssigningAuthority(ns, uid, uid_type));
			
		}
		HL7MLLPServer s = new HL7MLLPServer();
		StorageManager sm = StorageManager.getInstance();
		sm.connect(dbHost);

		PatientIdentityFeedHandler pif = new PatientIdentityFeedHandler();
		pif.register(s);

		PatientDemographicsQueryHandler pdq = new PatientDemographicsQueryHandler();
		pdq.register(s);


		PIXQueryHandler pix = new PIXQueryHandler();
		pix.register(s);
		
		CatchAllHandler cah = new CatchAllHandler();
		s.registerApplication("*", "*", cah);
		s.init(port);
		s.run();
	}

	public static void main(String[] args) throws Exception {
		PatientIndex pid = new PatientIndex();
		if (true)
			pid.run(args);
		else {
			Message m = Tests.pdq("Sfakianakis", "Stelios", null, "2007");
			outs.println(m.encode());
		}
	}
}
