package gr.forth.ics.icardea.pid;

import org.apache.log4j.Logger;

import gr.forth.ics.icardea.mllp.HL7MLLPServer;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.model.v25.message.RSP_K21;
import ca.uhn.hl7v2.model.v25.segment.QPD;
import ca.uhn.hl7v2.util.Terser;

/**
 * Patient Demographics Query: ITI-21 (ITI TF-2a / 3.21)
 * 
 * This transaction involves a request by the Patient Demographics Consumer
 * Actor for information about patients whose demographic data match data
 * provided in the query message. The request is received by the Patient
 * Demographics Supplier Actor. The Patient Demographics Supplier Actor
 * immediately processes the request and returns a response in the form of
 * demographic information for matching patients
 * 
 * 
 * The Patient Demographics Query is conducted by the HL7v2.5 QBP^Q22 message. The
 * receiver shall respond to the query by sending the RSP^K22 message.
 * 
 * @author ssfak
 * 
 */
class PatientDemographicsQueryHandler implements Application {
	static Logger logger = Logger.getLogger(PatientDemographicsQueryHandler.class);

	final static int QIP_FLD_NUM = 3;
	
	public void register(HL7MLLPServer s) {
		s.registerApplication("QBP", "Q22", this);
	}
	
	/*
	 * What we expect is QBP_Q22 v2.5 message but this is implemented through
	 * QBP_Q21 according to
	 * hapi-structures-v25-1.0.1-sources.jar/ca/uhn/hl7v2/parser/eventmap/2.5.properties
	 */
	public boolean canProcess(Message msg) {
		return msg instanceof QBP_Q21;
	}
	/**
	 * See ITI-vol2a, 3.21
	 */
	public Message processMessage(Message msg) throws ApplicationException{
		try {
			logger.debug("Received:"+msg.encode());
		} catch (HL7Exception e) {
		}
		QBP_Q21 m = (QBP_Q21) msg;
		QPD qpd = m.getQPD();
		String qt = qpd.getQpd2_QueryTag().getValue();
	
		RSP_K21 resp = new RSP_K21();
		// See also the OpenPIXPQD implementation at http://goo.gl/ohoOJ
		try {
			String reqMsgCtrlId = m.getMSH().getMessageControlID().getValue();
			// String reqRcvApp = m.getMSH().getReceivingApplication().encode();
			String reqSndApp = m.getMSH().getSendingApplication().encode();
			
			HL7Utils.fillResponseHeader(m.getMSH(), resp.getMSH());
			
			resp.getMSH().getMsh9_MessageType().parse("RSP^K22^RSP_K21");
			// resp.getMSH().getSendingApplication().parse(reqRcvApp);
			resp.getMSH().getReceivingApplication().parse(reqSndApp);
			resp.getMSA().getMessageControlID().setValue(reqMsgCtrlId);

			AssigningAuthority fromAuth = null;
			String ns = Terser.get(qpd, 8, 0, 4, 1);
			if (ns != null && !"".equals(ns)) {
				fromAuth = AssigningAuthority.find(ns);
				if (fromAuth == null) {
					HL7Exception ex = new HL7Exception("Unsupported authority:"+qpd.getField(3, 0).encode(), HL7Exception.UNKNOWN_KEY_IDENTIFIER);
					ex.setSegmentName("QPD");
					ex.setSegmentRepetition(1);
					ex.setFieldPosition(8);
					throw ex;
				}
			}


			int num = qpd.getField(QIP_FLD_NUM).length;
			iCARDEA_Patient criteria = new iCARDEA_Patient();
			String selId = null, selIdNS = null, selIdOid = null, selIdType = "ISO";
			for (int i=0; i<num ;++i) {
				String nv = qpd.getField(QIP_FLD_NUM, i).encode();
				int ind = nv.indexOf('^');
				if (ind < 0 )
					throw new HL7Exception("Parse error in QIP (no '^')", HL7Exception.DATA_TYPE_ERROR);
				String trait = nv.substring(0, ind);
				String val = nv.substring(ind+1);
				// System.out.println(trait+"="+val);
				
				if (trait.equalsIgnoreCase(iCARDEA_Patient.FNAME_SEG_FLD))
					criteria.name.family_name = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.GNAME_SEG_FLD))
					criteria.name.given_name = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.MOT_FNAME_SEG_FLD))
					criteria.mothers_name.family_name = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.MOT_GNAME_SEG_FLD))
					criteria.mothers_name.given_name = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.ADDR_STREET_SEG_FLD))
					criteria.addr.street = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.ADDR_CITY_SEG_FLD))
					criteria.addr.city = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.ADDR_STATE_SEG_FLD))
					criteria.addr.state = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.ADDR_ZIP_SEG_FLD))
					criteria.addr.zip = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.ADDR_COUNTRY_SEG_FLD))
					criteria.addr.country = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.ADDR_TYPE_SEG_FLD))
					criteria.addr.type = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.DOB_SEG_FLD))
					criteria.date_of_birth = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.SEX_SEG_FLD))
					criteria.sex = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.ACCNUM_SEG_FLD))
					criteria.accnum = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.ID_SEG_FLD))
					selId = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.IDNS_SEG_FLD))
					selIdNS = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.IDOID_SEG_FLD))
					selIdOid = val;
				else if (trait.equalsIgnoreCase(iCARDEA_Patient.IDTYPE_SEG_FLD))
					selIdType = val;
				else
					throw new HL7Exception("Unsupported QIP:"+trait, HL7Exception.DATA_TYPE_ERROR);
			}
			if (selId != null) {
                                if (selIdNS != null)
                                        criteria.ids.add(new iCARDEA_Patient.ID(selIdNS, selId));
                                else if (selIdOid != null){
                                        AssigningAuthority auth = AssigningAuthority.find_by_uid(selIdOid, selIdType);
                                        if (auth == null) {
                                                HL7Exception ex = new HL7Exception("Unsupported authority:"+selIdOid+"&"+selIdType, 
                                                                                   HL7Exception.UNKNOWN_KEY_IDENTIFIER);
                                                ex.setSegmentName("QPD");
                                                ex.setSegmentRepetition(1);
                                                ex.setFieldPosition(8);
                                                throw ex;
                                        }
                                        criteria.ids.add(new iCARDEA_Patient.ID(auth.namespace, selId));
                                }
                        }
		
			iCARDEA_Patient[] pats = StorageManager.getInstance().query(criteria);
			
			resp.getQAK().getQak1_QueryTag().setValue(qt);
			resp.getQAK().getQak2_QueryResponseStatus().setValue("OK");
			resp.getQPD().parse(qpd.encode());
			
			int k = 0;
			for (iCARDEA_Patient p : pats) {
				ca.uhn.hl7v2.model.v25.segment.PID pid = resp
						.getQUERY_RESPONSE(k++).getPID();
				p.toPidv25(pid);
			}
			resp.getMSA().getAcknowledgmentCode().setValue("AA");
			
		} catch (HL7Exception e) {
			e.printStackTrace();
			try {
				resp.getMSA().getAcknowledgmentCode().setValue("AE");
				// resp.getMSA().getTextMessage().setValue(e.getMessage());
				HL7Utils.fillErrHeader(resp, e);
				resp.getQAK().getQak1_QueryTag().setValue(qt);
				resp.getQAK().getQak2_QueryResponseStatus().setValue("AE");
				resp.getQPD().parse(qpd.encode());
			} catch (HL7Exception ex) {
				ex.printStackTrace();
				throw new ApplicationException(ex.getMessage(), ex);
			}
		} catch (Exception e) {
			e.printStackTrace();
			try {
				resp.getMSA().getAcknowledgmentCode().setValue("AR");
				resp.getMSA().getTextMessage().setValue(e.getMessage());
				resp.getERR().getErr3_HL7ErrorCode().getCwe1_Identifier().setValue(""+HL7Exception.APPLICATION_INTERNAL_ERROR);
				resp.getERR().getErr3_HL7ErrorCode().getCwe2_Text().setValue(e.getMessage());

				resp.getQAK().getQak1_QueryTag().setValue(qt);
				resp.getQAK().getQak2_QueryResponseStatus().setValue("AR");
				resp.getQPD().parse(qpd.encode());
			} catch (HL7Exception ex) {
				ex.printStackTrace();
				throw new ApplicationException(ex.getMessage(), ex);
			}
		}
		try {
			logger.debug("Sending:"+resp.encode());
		} catch (HL7Exception e) {
		}
		return resp;
	}
}
