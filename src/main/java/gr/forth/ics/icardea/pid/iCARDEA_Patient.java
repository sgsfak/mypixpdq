package gr.forth.ics.icardea.pid;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v231.datatype.XAD;
import ca.uhn.hl7v2.model.v231.datatype.CX;
import ca.uhn.hl7v2.model.v231.datatype.XPN;
import ca.uhn.hl7v2.model.v231.datatype.XTN;
import ca.uhn.hl7v2.model.v231.segment.PID;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

class PrimKV {
	private void visitAtomic(BasicDBObject o, Object v, String name) {
		if (v == null)
			return;
		if (v instanceof PrimKV) {
			PrimKV kv = (PrimKV) v;
			o.append(name, kv.toDBObject());					
		}
		else 
			o.append(name, v);
	}
	DBObject toDBObject() {
		BasicDBObject o = new BasicDBObject();
		try {
			for (Field f: this.getClass().getDeclaredFields()) {
				Object v = f.get(this);
				if (v == null)
					continue;
				String name = f.getName();
				if (v instanceof List<?>) {
					ArrayList<Object> objs = new ArrayList<Object>();
					for (Object obj: (List<?>) v) {
						if (obj instanceof PrimKV) {
							PrimKV t = (PrimKV) obj;
							objs.add(t.toDBObject());
						}
						else
							objs.add(obj);
					}
					o.append(name, objs);
				}
				else
					visitAtomic(o, v, name);
			}
		} catch (Exception e) { e.printStackTrace(); }
		return o;
	}

	void fill_from_DBObject(DBObject o) {
		try {
			for (Field f: this.getClass().getDeclaredFields()) {
				String k = f.getName();
				if (!o.containsField(k))
					continue;
//				if (List.class.isAssignableFrom(f.getType())) {
//					
//				}
				else if (PrimKV.class.isAssignableFrom(f.getType())) {
					PrimKV kv = (PrimKV) f.getType().newInstance();
					kv.fill_from_DBObject( (DBObject) o.get(k)); 
					f.set(this, kv);
				}
				else 
					f.set(this, o.get(k));
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
}
final public class iCARDEA_Patient extends PrimKV {
	public static final String ID_PREFIX = "id:";
	/* The following are used for the QPD-3 segment 
	 * (QIP - query input parameter list) of the 
	 * IHE's Patient Demographic Query
	 */
	public static final String ID_SEG_FLD = "@PID.3.1";
	public static final String IDNS_SEG_FLD = "@PID.3.4.1";
	public static final String IDOID_SEG_FLD = "@PID.3.4.2";
	public static final String IDTYPE_SEG_FLD = "@PID.3.4.3";
	public static final String FNAME_SEG_FLD = "@PID.5.1.1";
	public static final String GNAME_SEG_FLD = "@PID.5.2";
	public static final String MOT_FNAME_SEG_FLD = "@PID.6.1.1";
	public static final String MOT_GNAME_SEG_FLD = "@PID.6.2";
	public static final String DOB_SEG_FLD = "@PID.7.1";
	public static final String SEX_SEG_FLD = "@PID.8";
        public static final String ADDR_STREET_SEG_FLD = "@PID.11.1.1";
	public static final String ADDR_CITY_SEG_FLD = "@PID.11.3";
	public static final String ADDR_STATE_SEG_FLD = "@PID.11.4";
	public static final String ADDR_ZIP_SEG_FLD = "@PID.11.5";
	public static final String ADDR_COUNTRY_SEG_FLD = "@PID.11.6";
	public static final String ADDR_TYPE_SEG_FLD = "@PID.11.7";
	public static final String ACCNUM_SEG_FLD = "@PID.18.1";
	
	public static final class ID extends PrimKV {
		public String namespace;
		public String id;
		
		public ID(String ns, String id) {
			this.namespace = ns;
			this.id = id;
		}
		public String toString() {
			return id + "@" + namespace;
		}
	}
	public static final class Name extends PrimKV {
		public String family_name;
		public String given_name;
		public String type_code;
		
				
		public String hl7encode() {
			return String.format(
					"%s^%s^^^^^%s", // "Smith^John^J^III^DR^PHD^L"
					family_name == null ? "" : family_name,
					given_name == null ? "" : given_name,
					type_code == null ? "" : type_code);
		}
		public XPN toXPN(PID segm) throws HL7Exception {
			XPN n = segm.getPid5_PatientName(0);
			n.getFamilyLastName().getFn1_FamilyName().setValue(this.family_name);
			n.getGivenName().setValue(this.given_name);
			n.getNameTypeCode().setValue(this.type_code);
			return n;
		}
	}

	public static final class Address extends PrimKV {

		public String street;
		public String city;
		public String state;
		public String zip;
		public String country;
		public String type;

		public String hl7encode() {
			return String.format(
					"%s^^%s^%s^%s^%s^%s^^^", //1234 Easy St.^Ste. 123^San Francisco^CA^95123^USA^B^^SF^
					street == null ? "" : street,
					city == null ? "" : city,
					state == null ? "" : state,
					zip == null ? "" : zip,
					country == null ? "" : country,
					type == null ? "" : type);
		}								
									
							
		public XAD toXAD(PID segm) throws HL7Exception {
			XAD ad = segm.getPid11_PatientAddress(0);
			ad.getXad1_StreetAddress().setValue(this.street);
			ad.getXad3_City().setValue(this.city);
			ad.getXad4_StateOrProvince().setValue(this.state);
			ad.getXad5_ZipOrPostalCode().setValue(this.zip);
			ad.getXad6_Country().setValue(this.country);
			ad.getXad7_AddressType().setValue(this.type);
			return ad;
		}
	}
	public List<ID> ids;
	public Name name;
	public Name mothers_name;
	public Address addr;
	public String sex;
	public String date_of_birth;
	public String tel_home;
	public String tel_work;
	public String ssn;
	public String accnum;
	public String drivers_lic;

	public iCARDEA_Patient() {
		this.ids = new ArrayList<ID>();
		this.name = new Name();
		this.mothers_name = new Name();
		this.addr = new Address();
	}
	public iCARDEA_Patient(String fn, String gn, String bd, String g) {
		this.ids = new ArrayList<ID>();
		this.name = new Name();
		this.name.family_name = fn;
		this.name.given_name = gn;
		this.mothers_name = new Name();
		this.addr = new Address();
		this.date_of_birth = bd;
		this.sex = g;
	}
	public static iCARDEA_Patient create_from_PID(PID pid) {
		iCARDEA_Patient tr = new iCARDEA_Patient();
		for (CX cx: pid.getPid3_PatientIdentifierList()) {
			String id = cx.getCx1_ID().getValue();	
			String namespace = cx.getCx4_AssigningAuthority().getHd1_NamespaceID().getValue();
			if (namespace == null || "".equals(namespace)) {
				String uid = cx.getCx4_AssigningAuthority().getHd2_UniversalID().getValue();
				String uid_type = cx.getCx4_AssigningAuthority().getHd3_UniversalIDType().getValue();
				AssigningAuthority auth = AssigningAuthority.find_by_uid(uid, uid_type);
				namespace = auth.namespace;
			}
		
			System.out.println("{"+namespace+"}"+id);
			tr.ids.add( new ID(namespace, id));
			
		}
		
		for (XPN name: pid.getPid5_PatientName()) {
			tr.name.family_name = name.getFamilyLastName().getFamilyName().getValue();
			tr.name.given_name = name.getGivenName().getValue();
			tr.name.type_code = name.getNameTypeCode().getValue();
			break; // take only the first 
		}
		for (XPN name: pid.getPid6_MotherSMaidenName()) {
			tr.mothers_name.family_name = name.getFamilyLastName().getFamilyName().getValue();
			tr.mothers_name.given_name = name.getGivenName().getValue();
			tr.mothers_name.type_code = name.getNameTypeCode().getValue();
			break; // take only the first 
		}
		for (XAD a: pid.getPid11_PatientAddress()) {
			tr.addr.city = a.getCity().getValue();
			tr.addr.country = a.getCountry().getValue();
			tr.addr.state = a.getStateOrProvince().getValue();
			tr.addr.street = a.getStreetAddress().getValue();
			tr.addr.type = a.getAddressType().getValue();
			tr.addr.zip = a.getZipOrPostalCode().getValue();		
		}
		tr.date_of_birth = pid.getDateTimeOfBirth().getTimeOfAnEvent().getValue();
		tr.sex = pid.getSex().getValue();
		try {
			for (XTN tel: pid.getPid13_PhoneNumberHome()) {
				tr.tel_home = tel.encode();
				break; // take only the first
			}
			for (XTN tel: pid.getPid14_PhoneNumberBusiness()) {
				tr.tel_home = tel.encode();
				break; // take only the first
			}
			tr.ssn = pid.getPid19_SSNNumberPatient().getValue();
			tr.drivers_lic = pid.getPid20_DriverSLicenseNumberPatient().encode();
			tr.accnum = pid.getPid18_PatientAccountNumber().encode();
		} catch (HL7Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return tr;
	}
	

	public static iCARDEA_Patient create_from_DBObject(DBObject o) {

		iCARDEA_Patient tr = new iCARDEA_Patient();
		for (String k: o.keySet()) {
			if ("name".equals(k))
				tr.name.fill_from_DBObject((DBObject) o.get(k));
			else if ("mothers_name".equals(k))
				tr.mothers_name.fill_from_DBObject((DBObject) o.get(k));
			else if ("addr".equals(k))
				tr.addr.fill_from_DBObject((DBObject) o.get(k));
			else if ("date_of_birth".equals(k)) 
				tr.date_of_birth = (String) o.get(k);
			else if ("sex".equals(k)) 
				tr.sex = (String) o.get(k);
			else if ("ssn".equals(k)) 
				tr.ssn = (String) o.get(k);
			else if ("tel_home".equals(k)) 
				tr.tel_home = (String) o.get(k);
			else if ("tel_work".equals(k)) 
				tr.tel_work = (String) o.get(k);
			else if ("drivers_lic".equals(k)) 
				tr.drivers_lic = (String) o.get(k);
			else if ("accnum".equals(k)) 
				tr.accnum = (String) o.get(k);
			else if ("ids".equals(k)) {
				for(DBObject i: (List<DBObject>)o.get(k)) {
					String ns = (String) (i.containsField("namespace") ?  i.get("namespace") : null);
					String id = (String) (i.containsField("id") ?  i.get("id") : null);
					tr.ids.add(new ID(ns, id));
				}
			}
			else if (k.startsWith(ID_PREFIX)) {
				String ns = k.substring(ID_PREFIX.length());
				String id = (String) o.get(k);
				tr.ids.add( new ID(ns, id));
			}
		}
		return tr;
	}
	public DBObject toDBObject() {

		BasicDBObject obj = new BasicDBObject();
		if (this.name != null)
			obj.append("name", this.name.toDBObject());
		if (this.mothers_name != null)
			obj.append("mothers_name", this.mothers_name.toDBObject());		
		if (this.addr != null)
			obj.append("addr", this.addr.toDBObject());	
		if (this.date_of_birth != null)
			obj.append("date_of_birth", this.date_of_birth);
		if (this.sex != null)
			obj.append("sex", this.sex);
		if (this.tel_home != null)
			obj.append("tel_home", this.tel_home);
		if (this.tel_work != null)
			obj.append("tel_work", this.tel_work);
		if (this.ssn != null)
			obj.append("ssn", this.ssn);
		if (this.drivers_lic != null)
			obj.append("drivers_lic", this.drivers_lic);
		if (this.accnum != null)
			obj.append("accnum", this.accnum);
		
		List<DBObject> lids = new ArrayList<DBObject>();
		for (ID id: this.ids)
			lids.add(id.toDBObject());
		obj.append("ids", lids);
		return obj;
	}

	public void toPidv25(ca.uhn.hl7v2.model.v25.segment.PID pid) throws HL7Exception {
		toPidv25(pid, false);
	}
	public void toPidv25(ca.uhn.hl7v2.model.v25.segment.PID pid, boolean justIds) throws HL7Exception {
		iCARDEA_Patient p = this;
		int i = 0;
		for (iCARDEA_Patient.ID d: p.ids) {
			AssigningAuthority auth = AssigningAuthority.find(d.namespace);
			if (auth == null) {
				// this should not happen!!
				auth = new AssigningAuthority(d.namespace, "","");
			}
			ca.uhn.hl7v2.model.v25.datatype.CX cx = pid.getPid3_PatientIdentifierList(i);
			cx.getCx4_AssigningAuthority().getHd1_NamespaceID().setValue(d.namespace);
			cx.getCx4_AssigningAuthority().getHd2_UniversalID().setValue(auth.universal_id);
			cx.getCx4_AssigningAuthority().getHd3_UniversalIDType().setValue(auth.universal_type);
			cx.getCx1_IDNumber().setValue(d.id);
			++i;
		}
		if (justIds)
			return;
		pid.getPid5_PatientName(0).parse(p.name.hl7encode());
		pid.getPid6_MotherSMaidenName(0).parse(p.mothers_name.hl7encode());
		if (p.date_of_birth != null)
			pid.getDateTimeOfBirth().getTs1_Time().parse(p.date_of_birth);
		if (p.sex != null)
			pid.getPid8_AdministrativeSex().setValue(p.sex);
		System.out.println("ADDR="+p.addr.hl7encode());
		pid.getPid11_PatientAddress(0).parse(p.addr.hl7encode());
		if (p.tel_home != null)
			pid.getPid13_PhoneNumberHome(0).parse(p.tel_home);
		if (p.tel_work != null)
			pid.getPid14_PhoneNumberBusiness(0).parse(p.tel_work);
		if (p.ssn != null)
			pid.getPid19_SSNNumberPatient().parse(p.ssn);
		if (p.drivers_lic != null)
			pid.getPid20_DriverSLicenseNumberPatient().parse(p.drivers_lic);
		if (p.accnum != null)
			pid.getPid18_PatientAccountNumber().parse(p.accnum);
	}
}
