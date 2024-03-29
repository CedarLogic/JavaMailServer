package com.javaprophet.javamailserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Scanner;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.json.simple.JSONObject;
import com.javaprophet.javamailserver.imap.ConnectionIMAP;
import com.javaprophet.javamailserver.mailbox.EmailAccount;
import com.javaprophet.javamailserver.networking.Connection;
import com.javaprophet.javamailserver.smtp.ConnectionSMTP;
import com.javaprophet.javamailserver.sync.HardDriveSync;
import com.javaprophet.javamailserver.sync.Sync;
import com.javaprophet.javamailserver.util.Config;
import com.javaprophet.javamailserver.util.ConfigFormat;
import com.javaprophet.javamailserver.util.FileManager;

public class JavaMailServer {
	public static final String VERSION = "1.0";
	public static Config mainConfig;
	public static final FileManager fileManager = new FileManager();
	public static final String crlf = System.getProperty("line.separator");
	public static Sync curSync = null;
	public static boolean dead = false;
	
	public static void setupFolders() {
		fileManager.getMainDir().mkdirs();
		fileManager.getTemp().mkdirs();
		fileManager.getSync().mkdirs();
	}
	
	public static final ArrayList<Connection> runningThreads = new ArrayList<Connection>();
	public static final ArrayList<EmailAccount> accounts = new ArrayList<EmailAccount>();
	private static final ArrayList<ServerSocket> runningServers = new ArrayList<ServerSocket>();
	
	public static void registerAccount(String email, String password) {
		accounts.add(new EmailAccount(email, password));
	}
	
	private static SSLContext sc;
	
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
					if (!json.containsKey("syncType")) json.put("syncType", 0);
					if (!json.containsKey("hdsync")) json.put("hdsync", "sync");
					if (!json.containsKey("ssl")) json.put("ssl", new JSONObject());
					JSONObject ssl = (JSONObject)json.get("ssl");
					if (!ssl.containsKey("enabled")) ssl.put("enabled", false);
					if (!ssl.containsKey("bindport")) ssl.put("smtpport", 465);
					if (!ssl.containsKey("bindport")) ssl.put("imapport", 993);
					if (!ssl.containsKey("folder")) ssl.put("folder", "ssl");
					if (!ssl.containsKey("keyFile")) ssl.put("keyFile", "keystore");
					if (!ssl.containsKey("keystorePassword")) ssl.put("keystorePassword", "password");
					if (!ssl.containsKey("keyPassword")) ssl.put("keyPassword", "password");
				}
			});
			mainConfig.load();
			setupFolders();
			if ((Long)mainConfig.get("syncType") == 0) {
				curSync = new HardDriveSync();
			}
			System.out.println("Loading Accounts and Mailboxes...");
			if (curSync != null) {
				curSync.load(accounts);
			}
			ConnectionSMTP.init();
			ConnectionIMAP.init();
			final int smtpport = Integer.parseInt(mainConfig.get("smtpport").toString());
			final int imapport = Integer.parseInt(mainConfig.get("imapport").toString());
			final int SSLsmtpport = Integer.parseInt(((JSONObject)mainConfig.get("ssl")).get("smtpport").toString());
			final int SSLimapport = Integer.parseInt(((JSONObject)mainConfig.get("ssl")).get("imapport").toString());
			System.out.println("Starting SMTPServer on " + smtpport);
			Thread smtp = new Thread() {
				public void run() {
					try {
						ServerSocket server = new ServerSocket(smtpport);
						runningServers.add(server);
						while (!server.isClosed() && !dead) {
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
					dead = true;
				}
			};
			smtp.start();
			System.out.println("Starting IMAPServer on " + imapport);
			Thread imap = new Thread() {
				public void run() {
					try {
						ServerSocket server = new ServerSocket(imapport);
						runningServers.add(server);
						while (!server.isClosed() && !dead) {
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
					dead = true;
				}
			};
			imap.start();
			if ((boolean)((JSONObject)mainConfig.get("ssl")).get("enabled") == true) {
				System.out.println("Initializing SSL");
				KeyStore ks = KeyStore.getInstance("JKS");
				InputStream ksIs = new FileInputStream(fileManager.getSSLKeystore());
				try {
					ks.load(ksIs, ((JSONObject)mainConfig.get("ssl")).get("keystorePassword").toString().toCharArray());
				}finally {
					if (ksIs != null) {
						ksIs.close();
					}
				}
				KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(ks, ((JSONObject)mainConfig.get("ssl")).get("keyPassword").toString().toCharArray());
				TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
					public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
					}
					
					public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
					}
					
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}
				}};
				sc = null;
				String[] possibleProtocols = new String[]{"TLSv1.2", "TLSv1.1", "TLSv1", "TLSv1.0"};
				String fp = "";
				for (String protocol : possibleProtocols) {
					try {
						sc = SSLContext.getInstance(protocol);
						fp = protocol;
					}catch (NoSuchAlgorithmException e) {
						continue;
					}
				}
				if (sc == null) {
					System.out.println("No suitable TLS protocols found, please upgrade Java! SSL disabled.");
				}else {
					sc.init(kmf.getKeyManagers(), trustAllCerts, new SecureRandom());
					System.out.println("Starting SSLSMTPServer on " + SSLsmtpport);
					Thread ssmtp = new Thread() {
						public void run() {
							try {
								SSLServerSocket sslserver = (SSLServerSocket)sc.getServerSocketFactory().createServerSocket(SSLsmtpport);
								runningServers.add(sslserver);
								while (!sslserver.isClosed() && !dead) {
									Socket s = sslserver.accept();
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
							dead = true;
						}
					};
					ssmtp.start();
					System.out.println("Starting SSLIMAPServer on " + SSLimapport);
					Thread simap = new Thread() {
						public void run() {
							try {
								SSLServerSocket sslserver = (SSLServerSocket)sc.getServerSocketFactory().createServerSocket(SSLimapport);
								runningServers.add(sslserver);
								while (!sslserver.isClosed() && !dead) {
									Socket s = sslserver.accept();
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
							dead = true;
						}
					};
					simap.start();
				}
			}
			Scanner scan = new Scanner(System.in);
			while (!dead) {
				String command = scan.nextLine();
				if (command.equals("exit") || command.equals("stop")) {
					dead = true;
				}else if (command.equals("save")) {
					if (curSync != null) try {
						curSync.save(accounts);
					}catch (IOException e) {
						e.printStackTrace();
					}
					mainConfig.save();
					System.out.println("Saved Config & Accounts!");
				}else if (command.startsWith("register")) {
					String[] args2 = command.substring(8).trim().split(" ");
					if (args2.length == 2) {
						String email = args2[0];
						String password = args2[1];
						if (!email.endsWith("@" + mainConfig.get("domain"))) {
							System.out.println("Invalid Email Address!");
						}else {
							boolean bad = false;
							for (EmailAccount acct : accounts) {
								if (acct.email.equals(email)) {
									bad = true;
									break;
								}
							}
							if (bad) {
								System.out.println("Email Taken!");
							}else {
								registerAccount(email, password);
								System.out.println("Successfully Registered!");
							}
						}
					}else {
						System.out.println("Unspecified Email/Password!");
					}
				}else if (command.equals("reload")) {
					try {
						mainConfig.load();
					}catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println("Loaded Config! Some entries will require a restart. Emails not reloaded.");
				}else if (command.equals("help")) {
					System.out.println("Commands:");
					System.out.println("exit/stop");
					System.out.println("reload");
					System.out.println("help");
					System.out.println("save");
					System.out.println("register <email>@" + mainConfig.get("domain") + " <password>");
					System.out.println("");
					System.out.println("Java Mail Server(JMS) version " + VERSION);
				}else {
					System.out.println("Unknown Command: " + command);
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
		}finally {
			if (curSync != null) try {
				curSync.save(accounts);
			}catch (IOException e) {
				e.printStackTrace();
			}
			mainConfig.save();
			for (ServerSocket server : runningServers) {
				try {
					if (!server.isClosed()) server.close();
				}catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
