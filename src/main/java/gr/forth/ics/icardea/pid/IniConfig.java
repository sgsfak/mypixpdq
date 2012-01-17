package gr.forth.ics.icardea.pid;

import java.io.*;
import java.util.Hashtable;

final class IniConfig {

	Hashtable<String, Hashtable<String, String>> sections;

	public IniConfig() {
		sections = new Hashtable<String, Hashtable<String, String>>();
	}


	public void setKeyValue(String section, String key, String value) {
		try {
			getSection(section).put(key.toLowerCase(), value);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}


	public Hashtable<String, Hashtable<String, String>> getSections() {
		return sections;
	}


	public Hashtable<String, String> getSection(String section) {
		return sections.get(section.toLowerCase());
	}


	public boolean isNullOrEmpty(String section, String key) {
		String value = getKeyValue(section, key);
		return (value == null || value.length() == 0);
	}

	public String getKeyValue(String section, String key, String defaultValue) {
		String v = defaultValue;
		try {
			v = getSection(section).get(key.toLowerCase());
		} catch (NullPointerException e) {
			return defaultValue;
		}
		if (v == null)
			v = defaultValue;
		return v;
	}
	public String getKeyValue(String section, String key) {
		return getKeyValue(section, key, null);
	}


	public int getKeyIntValue(String section, String key) {
		return getKeyIntValue(section, key, 0);
	}

	public int getKeyIntValue(String section, String key, int defaultValue) {
		String value = getKeyValue(section, key.toLowerCase());
		if (value == null) {
			return defaultValue;
		} else {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}
	}

	public boolean getKeyBoolValue(String section, String key, boolean defaultValue) {
		String value = getKeyValue(section, key.toLowerCase());
		if (value == null)
			return defaultValue;
		else 
			return Boolean.parseBoolean(value);
	}

	public String[][] getKeysAndValues(String aSection) {
		Hashtable<String, String> section = getSection(aSection);
		if (section == null) {
			return null;
		}
		String[][] results = new String[section.size()][2];
		int i = 0;
		for (String k : section.keySet()) {
			results[i][0] = k;
			results[i][1] = section.get(k);
		}
		return results;
	}

	public IniConfig load(String filename) throws FileNotFoundException {
		return load(new FileInputStream(filename));
	}

	public void save(String filename) throws IOException {
		save(new FileOutputStream(filename));
	}

	public IniConfig load(InputStream in) {
		try {
			BufferedReader input = new BufferedReader(new InputStreamReader(in));
			String line;
			Hashtable<String, String> section = null;
			String section_name;
			while ((line = input.readLine()) != null) {
				if (line.startsWith(";") || line.startsWith("#"))
					continue;
				line = line.trim();
				if (line.startsWith("[")) {
					// new section
					section_name = line.substring(1, line.indexOf("]"))
					.toLowerCase();
					section = sections.get(section_name);
					if (section == null) {
						section = new Hashtable<String, String>();
						sections.put(section_name, section);
					}
				} else if (line.indexOf("=") != -1 && section != null) {
					// new key
					String key = line.substring(0, line.indexOf("=")).trim()
					.toLowerCase();
					String value = line.substring(line.indexOf("=") + 1).trim();
					section.put(key, value);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this;
	}

	public void save(OutputStream out) {
		try {
			PrintWriter output = new PrintWriter(out);
			for (String section_name : sections.keySet()) {
				output.println("[" + section_name + "]");
				Hashtable<String, String> section = sections.get(section_name);
				for (String k : section.keySet())
					output.println(" " + k + "=" + section.get(k));
			}
			output.flush();
			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public void addSection(String section) {
		sections.put(section.toLowerCase(), new Hashtable<String, String>());
	}


	public static void main(String[] args) throws Exception {
		(new IniConfig()).load(new FileInputStream("config.ini")).save(System.out);
	}
}
