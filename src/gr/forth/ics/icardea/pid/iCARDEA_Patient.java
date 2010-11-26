package gr.forth.ics.icardea.pid;

import ca.uhn.hl7v2.model.v231.datatype.CX;
import ca.uhn.hl7v2.model.v231.datatype.XPN;
import ca.uhn.hl7v2.model.v231.segment.PID;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

final class iCARDEA_Patient {
	public static final String ID_PREFIX = "id:";
	/* The following are used for the QPD-3 segment 
	 * (QIP - query input parameter list) of the 
	 * IHE's Patient Demographic Query
	 */
	public static final String ID_SEG_FLD = "@PID.3.1";
	public static final String IDNS_SEG_FLD = "@PID.3.4.1";
	public static final String FNAME_SEG_FLD = "@PID.5.1.1";
	public static final String GNAME_SEG_FLD = "@PID.5.2";
	public static final String DOB_SEG_FLD = "@PID.7.1";
	public static final String SE×_SEG_FLD = "@PID.8";
	
	public static final class ID {
		public String namespace;
		public String id;
		
		public ID(String ns, String id) {
			this.namespace = ns;
			this.id = id;
		}
	}
	public ID[] ids;
	public String family_name;
	public String given_name;
	public String sex;
	public String date_of_birth;

	public iCARDEA_Patient() {
		this.ids = new ID[0];
	}
	public iCARDEA_Patient(String fn, String gn, String bd, String g) {
		this.ids = new ID[0];
		this.family_name = fn;
		this.given_name = gn;
		this.date_of_birth = bd;
		this.sex = g;
	}
	public static iCARDEA_Patient create_from_PID(PID pid) {
		iCARDEA_Patient tr = new iCARDEA_Patient();
		java.util.ArrayList<ID> ids = new java.util.ArrayList<ID>();
		for (CX cx: pid.getPid3_PatientIdentifierList()) {
			String id = cx.getCx1_ID().getValue();	
			String namespace = cx.getCx4_AssigningAuthority().getHd1_NamespaceID().getValue();
			System.out.println("{"+namespace+"}"+id);
			ids.add( new ID(namespace, id));
			
		}
		tr.ids = ids.toArray(tr.ids);
		
		XPN name = pid.getPatientName()[0];
		tr.family_name = name.getFamilyLastName().getFamilyName().getValue();
		tr.given_name = name.getGivenName().getValue();
		tr.date_of_birth = pid.getDateTimeOfBirth().getTimeOfAnEvent().getValue();
		tr.sex = pid.getSex().getValue();
		return tr;
	}
	

	public static iCARDEA_Patient create_from_DBObject(DBObject o) {

		iCARDEA_Patient tr = new iCARDEA_Patient();
		java.util.ArrayList<ID> ids = new java.util.ArrayList<ID>();
		for (String k: o.keySet()) {
			if ("family_name".equals(k)) 
				tr.family_name = (String) o.get(k);
			else if ("given_name".equals(k)) 
				tr.given_name = (String) o.get(k);
			else if ("date_of_birth".equals(k)) 
				tr.date_of_birth = (String) o.get(k);
			else if ("sex".equals(k)) 
				tr.sex = (String) o.get(k);
			else if (k.startsWith(ID_PREFIX)) {
				String ns = k.substring(ID_PREFIX.length());
				String id = (String) o.get(k);
				ids.add( new ID(ns, id));
			}
		}
		tr.ids = ids.toArray(tr.ids);
		return tr;
	}
	public DBObject toDBObject() {
		BasicDBObject o = new BasicDBObject();
		if (this.family_name != null)
			o.append("family_name", this.family_name);
		if (this.given_name != null)
			o.append("given_name", this.given_name);
		if (this.date_of_birth != null)
			o.append("date_of_birth", this.date_of_birth);
		if (this.sex != null)
			o.append("sex", this.sex);
		for (ID id: this.ids)
			o.append(ID_PREFIX+id.namespace, id.id);
		return o;
	}
}
