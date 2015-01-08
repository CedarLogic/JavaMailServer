package com.javaprophet.javamailserver.util;

import java.io.File;
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
	
	public File getBaseFile(String name) {
		return new File(getMainDir(), name);
	}
}
