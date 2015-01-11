package com.javaprophet.javamailserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import org.json.simple.JSONObject;
import com.javaprophet.javamailserver.imap.ConnectionIMAP;
import com.javaprophet.javamailserver.mailbox.EmailAccount;
import com.javaprophet.javamailserver.networking.Connection;
import com.javaprophet.javamailserver.smtp.ConnectionSMTP;
import com.javaprophet.javamailserver.util.Config;
import com.javaprophet.javamailserver.util.ConfigFormat;
import com.javaprophet.javamailserver.util.FileManager;

public class JavaMailServer {
	public static final String VERSION = "1.0";
	public static Config mainConfig;
	public static final FileManager fileManager = new FileManager();
	public static final String crlf = System.getProperty("line.separator");
	
	public static void setupFolders() {
		fileManager.getMainDir().mkdirs();
		fileManager.getTemp().mkdirs();
	}
	
	public static final ArrayList<Connection> runningThreads = new ArrayList<Connection>();
	public static final ArrayList<EmailAccount> accounts = new ArrayList<EmailAccount>();
	
	public static void main(String[] args) {
		try {
			System.out.println("Loading Configs");
			final File cfg = new File(args.length > 0 ? args[0] : "C:\\jms\\main.cfg");
			mainConfig = new Config(cfg, new ConfigFormat() {
				public void format(JSONObject json) {
					if (!json.containsKey("version")) json.put("version", JavaMailServer.VERSION);
					if (!json.containsKey("dir")) json.put("dir", cfg.getParentFile().getAbsolutePath());
					if (!json.containsKey("temp")) json.put("temp", "temp");
					if (!json.containsKey("domain")) json.put("domain", "minealts.com");
					if (!json.containsKey("smtpport")) json.put("smtpport", 25);
					if (!json.containsKey("imapport")) json.put("imapport", 143);
					if (!json.containsKey("threadType")) json.put("threadType", 0);
					// if (!json.containsKey("ssl")) json.put("ssl", new JSONObject());
					// JSONObject ssl = (JSONObject)json.get("ssl");
					// if (!ssl.containsKey("enabled")) ssl.put("enabled", false);
					// if (!ssl.containsKey("forceSSL")) ssl.put("forceSSL", false); // TODO: implement
					// if (!ssl.containsKey("bindport")) ssl.put("bindport", 443);
					// if (!ssl.containsKey("folder")) ssl.put("folder", "ssl");
					// if (!ssl.containsKey("keyFile")) ssl.put("keyFile", "keystore");
					// if (!ssl.containsKey("keystorePassword")) ssl.put("keystorePassword", "password");
					// if (!ssl.containsKey("keyPassword")) ssl.put("keyPassword", "password");
				}
			});
			mainConfig.load();
			setupFolders();
			ConnectionSMTP.init();
			ConnectionIMAP.init();
			final int smtpport = Integer.parseInt(mainConfig.get("smtpport").toString());
			final int imapport = Integer.parseInt(mainConfig.get("imapport").toString());
			System.out.println("Loading Accounts and Mailboxes...");
			accounts.add(new EmailAccount("cock", "sdfsadfsadfasdf", 0));
			System.out.println("Starting SMTPServer on " + smtpport);
			Thread smtp = new Thread() {
				public void run() {
					try {
						ServerSocket server = new ServerSocket(smtpport);
						while (!server.isClosed()) {
							Socket s = server.accept();
							DataOutputStream out = new DataOutputStream(s.getOutputStream());
							out.flush();
							DataInputStream in = new DataInputStream(s.getInputStream());
							out.write(("220 " + mainConfig.get("domain") + " ESMTP JavaMailServer" + crlf).getBytes());
							ConnectionSMTP c = new ConnectionSMTP(s, in, out, false);
							c.handleConnection();
							runningThreads.add(c);
						}
					}catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
			smtp.start();
			System.out.println("Starting IMAPServer on " + imapport);
			Thread imap = new Thread() {
				public void run() {
					try {
						ServerSocket server = new ServerSocket(imapport);
						while (!server.isClosed()) {
							Socket s = server.accept();
							DataOutputStream out = new DataOutputStream(s.getOutputStream());
							out.flush();
							DataInputStream in = new DataInputStream(s.getInputStream());
							out.write(("* OK " + mainConfig.get("domain") + " IMAP JavaMailServer ready." + crlf).getBytes());
							ConnectionIMAP c = new ConnectionIMAP(s, in, out, false);
							c.handleConnection();
							runningThreads.add(c);
						}
					}catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
			imap.start();
			mainConfig.save();
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
}
