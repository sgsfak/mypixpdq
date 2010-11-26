package gr.forth.ics.icardea.pid;

import java.util.Hashtable;

final class AssigningAuthority {
	public final String namespace;
	public final String universal_id;
	public final String universal_type;
	
	static Hashtable<String, AssigningAuthority> authorities = new Hashtable<String, AssigningAuthority>();
	static Hashtable<String, AssigningAuthority> authorities_by_uid = new Hashtable<String, AssigningAuthority>();
	
	AssigningAuthority(String namespace, String universal_id, String universal_type) {
		this.namespace = namespace;
		this.universal_id = universal_id;
		this.universal_type = universal_type;
		
	}

	public static void add(AssigningAuthority a) {
		authorities.put(a.namespace, a);
		authorities_by_uid.put(a.universal_id+"^"+a.universal_type, a);
	}
	public static AssigningAuthority find(String ns) {
		return authorities.get(ns);
	}
	public static AssigningAuthority find_by_uid(String universal_id, String universal_type) {
		return authorities_by_uid.get(universal_id+"^"+universal_type);
	}
}