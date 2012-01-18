package gr.forth.ics.icardea.pid.test;

import java.io.IOException;
import java.net.Socket;
import java.security.Security;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import gr.forth.ics.icardea.pid.HL7Utils;
import gr.forth.ics.icardea.pid.PatientIndex;
import gr.forth.ics.icardea.pid.iCARDEA_Patient;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
//import static org.junit.Assert.*;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.app.ConnectionHub;
import ca.uhn.hl7v2.app.Initiator;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.llp.MinLowerLayerProtocol;
import ca.uhn.hl7v2.model.Message;

import ca.uhn.hl7v2.model.v25.datatype.CX;
import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.model.v25.message.RSP_K23;
import ca.uhn.hl7v2.model.v25.segment.QPD;
import ca.uhn.hl7v2.model.v25.segment.PID;
import ca.uhn.hl7v2.parser.PipeParser;

public class  PatientIndexTest {
	public static final String ICARDEA_PIX_OID = "1.2.826.0.1.3680043.2.44.248240.1";
	private static PatientIndex pid = null;
	private static Connection connection = null;
	
	private Message sendAndRecv(Message msg) throws LLPException, HL7Exception, IOException {
		// The initiator is used to transmit unsolicited messages
		Initiator initiator = connection.getInitiator();
		Message response = initiator.sendAndReceive(msg);
		return response;
	}

	@Test
	public void testPDQ() throws HL7Exception, LLPException, IOException {
		Message m = this.pdq("Sfakianakis", "Stelios", null, null);
		System.out.println("---PDQ----\n"+m.encode());
	}
	@Test
	public void testFeed() {

		// Insert (ADT_A01)
		//MSH|^~\&|OTHER_IBM_BRIDGE_TLS|IBM|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|20090224104152-0600||ADT^A01^ADT_A01|8686183982575368499|P|2.3.1||20090224104152-0600
		//PID|||103^^^icardea~o103^^^ORBIS~i103^^^CIED||SINGLETON1^MARION1||19661109|F
		//PV1||I
	}
	@Test
	public void testUpdate() {

		// Update (A08)
		//MSH|^~\&|OTHER_IBM_BRIDGE_TLS|IBM|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|20090224104204-0600||ADT^A08^ADT_A01|9241351356666182528|P|2.3.1||20090224104204-0600
		//PID|||102^^^IBOT&1.3.6.1.4.1.21367.2009.1.2.370&ISO||OTHER_IBM_BRIDGE^MARION||19661109|F
		//PV1||O
	}
	@Test
	public void testQuery() throws HL7Exception, LLPException, IOException {

		// PIX Query
		//MSH|^~\&|PACS_FUJIFILM|FUJIFILM|PAT_IDENTITY_X_REF_MGR_MISYS|ALLSCRIPTS|20090223144546||QBP^Q23^QBP_Q21|1235421946|P|2.5|||||||
		//QPD|IHEPIXQuery|Q231235421946|103^^^icardea|^^^ORBIS
		//RCP|I|

		QBP_Q21 a = new QBP_Q21();		
		// Construct MSH according to C2.2 of ITI TF-2x
		HL7Utils.createHeader(a.getMSH(), "2.5");
		a.getMSH().getMsh9_MessageType().parse("QBP^Q23^QBP_Q21");
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
		qpd.getQpd1_MessageQueryName().parse("IHE PIX Query");
		qpd.getQpd2_QueryTag().setValue("pix-query-1"); // A query identifier
		CX from = new CX(a);
		from.getCx1_IDNumber().setValue("o103"); // we search for this id in the iCARDEA domain

		from.getCx4_AssigningAuthority().getHd2_UniversalID().setValue(ICARDEA_PIX_OID);
		from.getCx4_AssigningAuthority().getHd3_UniversalIDType().setValue("ISO");

		qpd.getField(3,0).parse(from.encode());
		
		String toDomain = "ORBIS";
		
		a.getRCP().getRcp3_ResponseModality().getCe1_Identifier().setValue("I");
		Message m = this.sendAndRecv(a);
		RSP_K23 ret = (RSP_K23) m;
		System.out.println("***PIX*****\n"+ret.encode()+"\n+++++++");
		String status =ret.getQAK().getQak2_QueryResponseStatus().getValue();
		if ("AE".equals(status)) {
			// Application Error!!
			// ...
		}
		else if ("NF".equals(status)){
			// Not Found!!!
		}
		else {
			PID pid = ret.getQUERY_RESPONSE().getPID();
			for (CX d: pid.getPatientIdentifierList()) {
				if (toDomain.equals(d.getAssigningAuthority().getHd1_NamespaceID().getValue())) {
					String id = d.getCx1_IDNumber().getValue();
					System.out.println("Found ID="+id+" in domain "+toDomain);
				}
			}
		}
	}

	// PDQ - Produces something like:
	//MSH|^~\&|testclient^icardea|SALK^icardea|PID^icardea|iCARDEAPlatform|||QBP^Q22^QBP_Q21|blabla||2.5||||||UNICODE UTF-8
	//QPD|IHE PDQ Query|query-1|@PID.5.1.1^Duck~@PID.5.2^MARION
	//RCP|I
	public Message pdq(String fam_name, String giv_name, String sex, String dob) 
	throws HL7Exception, LLPException, IOException {

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
			qpd.getField(QIP_FLD_NUM, k++).parse(iCARDEA_Patient.SEX_SEG_FLD + "^" + sex);
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
		return this.sendAndRecv(a);
	}
	@BeforeClass
	public static void setup() throws Exception {
		pid = new PatientIndex();
		pid.run(new String[]{"config.ini"});
		
		PipeParser p = new PipeParser();
		Socket socket = null;

		if (pid.usesTLS()) {
			SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
			SSLSocket sslsocket = (SSLSocket) sslsocketfactory.createSocket("localhost", pid.port());
			sslsocket.startHandshake();
			socket = sslsocket;
		}
		else
			socket = new Socket("localhost", pid.port());
		connection = new Connection(p, new MinLowerLayerProtocol(), socket);
	}

	@AfterClass
	public static void cleanup() {
		pid.stop();
	}
	public static void main(String args[]) {

		org.junit.runner.JUnitCore.main("gr.forth.ics.icardea.pid.test.PatientIndexTest");
	}
}
