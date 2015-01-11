package com.javaprophet.javamailserver.util;

import java.io.File;
import org.json.simple.JSONObject;
import com.javaprophet.javamailserver.JavaMailServer;

public class FileManager {
	public FileManager() {
		
	}
	
	public File getMainDir() {
		return new File((String)JavaMailServer.mainConfig.get("dir"));
	}
	
	public File getTemp() {
		return new File(getMainDir(), (String)JavaMailServer.mainConfig.get("temp"));
	}
	
	public File getSync() {
		return new File(getMainDir(), (String)JavaMailServer.mainConfig.get("hdsync"));
	}
	
	public File getBaseFile(String name) {
		return new File(getMainDir(), name);
	}
	
	public File getSSL() {
		return new File(getMainDir(), (String)(((JSONObject)JavaMailServer.mainConfig.get("ssl")).get("folder")));
	}
	
	public File getSSLKeystore() {
		return new File(getSSL(), (String)(((JSONObject)JavaMailServer.mainConfig.get("ssl")).get("keyFile")));
	}
}
