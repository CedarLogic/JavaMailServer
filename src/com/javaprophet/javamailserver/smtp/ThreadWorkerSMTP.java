package com.javaprophet.javamailserver.smtp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.bind.DatatypeConverter;
import com.javaprophet.javamailserver.JavaMailServer;

public class ThreadWorkerSMTP extends Thread {
	
	public ThreadWorkerSMTP() {
		
	}
	
	private static class Work {
		public final Socket s;
		public final DataInputStream in;
		public final DataOutputStream out;
		public final boolean ssl;
		public int state = 0;
		public boolean isExtended = false;
		public String authUser = "", mailFrom = "";
		public ArrayList<String> rcptTo = new ArrayList<String>();
		public ArrayList<String> data = new ArrayList<String>();
		
		public Work(Socket s, DataInputStream in, DataOutputStream out, boolean ssl) {
			this.s = s;
			this.in = in;
			this.out = out;
			this.ssl = ssl;
		}
	}
	
	public static void clearWork() {
		workQueue.clear();
	}
	
	private static LinkedBlockingQueue<Work> workQueue = new LinkedBlockingQueue<Work>();
	
	public static void addWork(Socket s, DataInputStream in, DataOutputStream out, boolean ssl) {
		workQueue.add(new Work(s, in, out, ssl));
	}
	
	private boolean keepRunning = true;
	
	public void close() {
		keepRunning = false;
	}
	
	public void writeMLine(Work w, int response, String data) throws IOException {
		System.out.println(w.hashCode() + ": " + response + "-" + data);
		w.out.write((response + "-" + data + JavaMailServer.crlf).getBytes());
	}
	
	public void writeLine(Work w, int response, String data) throws IOException {
		System.out.println(w.hashCode() + ": " + response + " " + data);
		w.out.write((response + " " + data + JavaMailServer.crlf).getBytes());
	}
	
	public void flushMessage() {
		
	}
	
	public void run() {
		while (keepRunning) {
			Work focus = workQueue.poll();
			if (focus == null) {
				try {
					Thread.sleep(10L);
				}catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			try {
				if (!focus.s.isClosed()) {
					String line = focus.in.readLine().trim();
					System.out.println(focus.hashCode() + ": " + line);
					if (line.startsWith("EHLO")) {
						writeMLine(focus, 250, (String)JavaMailServer.mainConfig.get("domain"));
						writeMLine(focus, 250, "AUTH PLAIN LOGIN");
						writeLine(focus, 250, "AUTH=PLAIN LOGIN");
						focus.state = 1;
						focus.isExtended = true;
					}else if (line.startsWith("HELO")) {
						writeLine(focus, 250, "OK");
						focus.state = 1;
						focus.isExtended = false;
					}else if (line.startsWith("AUTH")) {
						if (line.length() >= 5) {
							line = line.substring(5);
							if (line.startsWith("PLAIN")) {
								line = line.substring(6).trim();
								if (line.length() > 0) {
									String up = new String(DatatypeConverter.parseBase64Binary(line)).substring(1);
									String username = up.substring(0, up.indexOf(new String(new byte[]{0})));
									String password = up.substring(username.length() + 1);
									System.out.println(username + ":" + password);
									boolean good = true;
									if (good) {
										writeLine(focus, 250, "OK");
										focus.authUser = username;
										focus.state = 1;
									}else {
										writeLine(focus, 535, "authentication failed");
									}
								}else {
									writeLine(focus, 501, "Syntax error in parameters or arguments");
								}
							}else {
								writeLine(focus, 501, "Syntax error in parameters or arguments");
							}
						}else {
							writeLine(focus, 501, "Syntax error in parameters or arguments");
						}
					}else if (focus.state >= 1 && focus.state != 4 && line.startsWith("MAIL FROM:")) {
						focus.mailFrom = line.substring(10);
						focus.rcptTo.clear();
						focus.data.clear();
						focus.state = 2;
						writeLine(focus, 250, "OK");
					}else if (focus.state == 2 || focus.state == 3 && line.startsWith("RCPT TO:")) {
						focus.rcptTo.add(line.substring(8));
						focus.state = 3;
						writeLine(focus, 250, "OK");
					}else if (focus.state == 3 && line.startsWith("DATA")) {
						focus.state = 4;
						writeLine(focus, 354, "Start mail input; end with <CRLF>.<CRLF>");
					}else if (focus.state == 4) {
						if (!line.equals(".")) {
							focus.data.add(line);
						}else {
							focus.state = 1;
							flushMessage();
							writeLine(focus, 250, "OK");
						}
					}else if (line.equals("QUIT")) {
						writeLine(focus, 251, (String)JavaMailServer.mainConfig.get("domain") + " terminating connection.");
						focus.s.close();
					}else {
						writeLine(focus, 500, "Command not recognized");
					}
				}
			}catch (IOException e) {
				if (!(e instanceof SocketException)) e.printStackTrace();
			}
			if (focus != null && !focus.s.isClosed()) workQueue.add(focus);
		}
	}
}
