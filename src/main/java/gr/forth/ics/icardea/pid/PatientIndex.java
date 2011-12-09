package gr.forth.ics.icardea.pid;

import java.io.IOException;

import org.apache.log4j.Logger;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.DefaultApplication;
import ca.uhn.hl7v2.app.ApplicationException;
import ca.uhn.hl7v2.model.Message;

import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;

import gr.forth.ics.icardea.mllp.HL7MLLPClient;
import gr.forth.ics.icardea.mllp.HL7MLLPServer;

final class CatchAllHandler extends DefaultApplication {

	static Logger logger = Logger.getLogger(CatchAllHandler.class);
	public Message processMessage(Message msg) throws ApplicationException{
		try {
			Terser t = new Terser(msg);
	        String messageType = t.get("/MSH-9-1");
	        String triggerEvent = t.get("/MSH-9-2");
			logger.debug("Got message '"+messageType+"^" + triggerEvent +"'. I don't handle such messages!");
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



public class PatientIndex {
	static Logger logger = Logger.getLogger(PatientIndex.class);
	
	public static final java.io.PrintStream outs = System.out; 
	public static void usage() {
		logger.warn("java PatientIndex <config.ini>\n") ;
	}

	public static String app_name = "PID"; 
	public static String fac_name = "icardeaPlatform";
	
	private HL7MLLPServer mllpServer = null;
	private HL7MLLPClient forwarder_ = null;
	
	public int port() {
		return this.mllpServer.port();
	}
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

		this.forwarder_ = new HL7MLLPClient();
		String [][] l = cfg.getKeysAndValues("listeners");
		for (int k = 0; k<l.length; ++k) {
			String hp = l[k][1]; 
			int sd = hp.indexOf("://");
			if (sd <= 0) {
				outs.println("Wrong listener format in ini file:"+hp+
						" (it should be <scheme>://<host>:<port> where scheme one of " +
						" 'mllp', 'mllps'). exiting..");
				return;			
			}
			String scheme = hp.substring(0, sd);
			boolean secure = "mllps".equalsIgnoreCase(scheme);
			sd += 3;
			int d = hp.indexOf(':', sd);
			if (d <= 0) {
				outs.println("Wrong listener format in ini file:"+hp+
						" (it should be <host>:<port>). exiting..");
				return;			
			}
			String h = hp.substring(sd, d);
			int p = Integer.parseInt(hp.substring(d+1));
			this.forwarder_.add_listener(h, p, secure);
		}

		for (String sec: cfg.getSections().keySet()) {
			if (sec.equalsIgnoreCase("server") || sec.equalsIgnoreCase("listeners"))
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
		this.mllpServer = new HL7MLLPServer();
		StorageManager sm = StorageManager.getInstance();
		sm.connect(dbHost);

		PatientIdentityFeedHandler pif = new PatientIdentityFeedHandler();
		pif.register(this.mllpServer, this.forwarder_);

		PatientDemographicsQueryHandler pdq = new PatientDemographicsQueryHandler();
		pdq.register(this.mllpServer);


		PIXQueryHandler pix = new PIXQueryHandler();
		pix.register(this.mllpServer);
		
		CatchAllHandler cah = new CatchAllHandler();
		this.mllpServer.registerApplication("*", "*", cah);
		this.mllpServer.init(port, new NoValidation());
		this.mllpServer.run();
	}
	public void stop() {
		if (this.mllpServer != null)
			this.mllpServer.stop();
	}

	public static void main(String[] args) throws Exception {
		PatientIndex pid = new PatientIndex();
		pid.run(args);
	}
}
