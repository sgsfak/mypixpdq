package gr.forth.ics.icardea.pid;

import java.util.regex.Pattern;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.QueryBuilder;

/**
 * StorageManager is the class responsible for storing, retrieving, and
 * searching in the Database This implementation is using MongoDB document
 * database
 * 
 * @author ssfak
 * 
 */
final class StorageManager {
	public static final String DB_NAME = "pid";
	public static final String COLL_NAME = "patients";
	
	private Mongo mon_;
	private DB db_;
	
	/** This is a singleton class.
	 * The Java MongoDB driver is thread safe using a pool of 
	 * connections shared amongst threads. 
	 * See: http://www.mongodb.org/display/DOCS/Java+Driver+Concurrency 
	 */
	private StorageManager() {}

	/**
	 * SingletonHolder is loaded on the first execution of Singleton.getInstance() 
	 * or the first access to SingletonHolder.INSTANCE, not before.
	 */
	private static class SingletonHolder { 
		public static final StorageManager INSTANCE = new StorageManager();
	}

	public static StorageManager getInstance() {
		return SingletonHolder.INSTANCE;
	}
	 
	public void connect(String host) throws Exception {
		this.mon_ = new Mongo(host);
		this.db_ = this.mon_.getDB(DB_NAME);		
	}
	
	public void insert_pid(iCARDEA_Patient tr) {
		DBCollection coll = this.db_.getCollection(COLL_NAME);
		DBObject o = tr.toDBObject();
		o.put("created_", new java.util.Date());
		coll.save(o, com.mongodb.WriteConcern.SAFE);
	}
	public void update_pid(iCARDEA_Patient tr) {
		DBCollection coll = this.db_.getCollection(COLL_NAME);
		DBObject o = tr.toDBObject();
		java.util.ArrayList<DBObject> ids = new java.util.ArrayList<DBObject>();
		for (String k: o.keySet()) {
			if (k.startsWith(iCARDEA_Patient.ID_PREFIX))
				ids.add(new BasicDBObject(k, o.get(k)));
		}
		if (ids.size() == 0)
			return;
		DBObject qry;
		if (ids.size() == 1)
			qry = ids.get(0);
		else {
			QueryBuilder q = new QueryBuilder();
			qry = q.or(ids.toArray(new DBObject[0])).get();
		}
		DBObject upd = new BasicDBObject("$set", o);
		
	System.out.println("UMONQ="+qry);
		
		coll.update(qry, upd, false, true, com.mongodb.WriteConcern.SAFE);
	}
	
	private void addCritPattern(DBObject o, String key, String value) {
		if (value.indexOf('*') > 0) {
			value.replace("*", ".*");		
		}
		if (value.charAt(0)!='^')
			value = "^" + value;

		Pattern re = Pattern.compile(value);
		o.put(key, re);	
	}
	public iCARDEA_Patient[] query(iCARDEA_Patient tr) {
		BasicDBObject query = new BasicDBObject();
		if (tr.family_name != null)
			addCritPattern(query, "family_name", tr.family_name);
		if (tr.given_name != null)
			addCritPattern(query, "given_name", tr.given_name);
		if (tr.date_of_birth != null)
			addCritPattern(query, "date_of_birth", tr.date_of_birth);
		if (tr.sex != null)
			query.put("sex", tr.sex);
		for (iCARDEA_Patient.ID id: tr.ids) {
			if (id.id != null)
				query.append(iCARDEA_Patient.ID_PREFIX+id.namespace, id.id);
			else
				query.append(iCARDEA_Patient.ID_PREFIX+id.namespace, new BasicDBObject("$exists", true));
		}
		
System.out.println("MONQ="+query);

		DBCollection coll = this.db_.getCollection(COLL_NAME);
		final int SORT_ORDER = -1; // descending 
        DBCursor cur = coll.find(query).sort(new BasicDBObject("created_",SORT_ORDER)).limit(100);

		java.util.ArrayList<iCARDEA_Patient> patLst = new java.util.ArrayList<iCARDEA_Patient>();
        while(cur.hasNext()) {
        	iCARDEA_Patient o = iCARDEA_Patient.create_from_DBObject(cur.next());
        	patLst.add(o);
        }
        iCARDEA_Patient[] patients = new iCARDEA_Patient[0];
        return patLst.toArray(patients);
	}

	public iCARDEA_Patient retrieve(iCARDEA_Patient.ID id, String... idNS) {
		BasicDBObject query = new BasicDBObject(iCARDEA_Patient.ID_PREFIX+id.namespace, id.id);
		
		DBCollection coll = this.db_.getCollection(COLL_NAME);
		DBObject dbObj = null;
		if (idNS.length > 0) {
			DBObject fo = new BasicDBObject();
			for (String f: idNS)
				fo.put(iCARDEA_Patient.ID_PREFIX+f, 1);
			fo.put("family_name", 1);
			fo.put("given_name", 1);
			dbObj = coll.findOne(query, fo);
			
		}
		else
			dbObj = coll.findOne(query);
		if (dbObj == null)
			return null;
		return iCARDEA_Patient.create_from_DBObject(dbObj);
	}
}

