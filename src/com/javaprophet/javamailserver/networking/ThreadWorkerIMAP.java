package com.javaprophet.javamailserver.networking;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.bind.DatatypeConverter;
import com.javaprophet.javamailserver.JavaMailServer;
import com.javaprophet.javamailserver.mailbox.Email;
import com.javaprophet.javamailserver.mailbox.EmailAccount;
import com.javaprophet.javamailserver.mailbox.Mailbox;

public class ThreadWorkerIMAP extends Thread {
	
	public ThreadWorkerIMAP() {
		
	}
	
	private static class Work {
		public final Socket s;
		public final DataInputStream in;
		public final DataOutputStream out;
		public final boolean ssl;
		public int state = 0;
		public String authMethod = "";
		public EmailAccount authUser = null;
		public Mailbox selectedMailbox = null;
		public boolean isExamine = false;
		
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
	
	public void writeLine(Work w, String id, String data) throws IOException {
		System.out.println(w.hashCode() + ": " + id + " " + data);
		w.out.write(((id.length() > 0 ? (id + " ") : "") + data + JavaMailServer.crlf).getBytes());
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
					String cmd;
					String letters;
					String[] args;
					if (!(focus.state == 1)) {
						letters = line.substring(0, line.indexOf(" "));
						line = line.substring(letters.length() + 1);
						cmd = line.substring(0, line.contains(" ") ? line.indexOf(" ") : line.length()).toLowerCase();
						line = line.substring(cmd.length()).trim();
						args = line.split(" ");
					}else {
						letters = "";
						cmd = "";
						args = new String[0];
					}
					boolean noCMD = true;
					if (cmd.equals("capability")) {
						noCMD = false;
						writeLine(focus, "*", "CAPABILITY IMAP4rev1 AUTH=PLAIN LOGINDISABLED");
						writeLine(focus, letters, "OK Capability completed.");
					}else if (line.equals("logout")) {
						noCMD = false;
						writeLine(focus, letters, "OK " + (String)JavaMailServer.mainConfig.get("domain") + " terminating connection.");
						focus.s.close();
					}else if (cmd.equals("noop")) {
						noCMD = false;
						writeLine(focus, letters, "OK");
					}else if (cmd.equals("authenticate") && focus.state == 0) {
						noCMD = false;
						if (args.length >= 1 && args[0].equals("plain")) {
							writeLine(focus, "", "+");
							focus.state = 1;
							focus.authMethod = "plain" + letters;
						}else {
							writeLine(focus, letters, "BAD No type.");
						}
					}else if (focus.state == 1 && focus.authMethod.startsWith("plain")) {
						noCMD = false;
						String up = new String(DatatypeConverter.parseBase64Binary(line)).substring(1);
						String username = up.substring(0, up.indexOf(new String(new byte[]{0})));
						String password = up.substring(username.length() + 1);
						letters = focus.authMethod.substring(5);
						System.out.println(username + ":" + password);
						EmailAccount us = null;
						for (EmailAccount e : JavaMailServer.accounts) {
							if (e.email.equals(username) && e.password.equals(password)) {
								us = e;
								break;
							}
						}
						if (us != null) {
							writeLine(focus, letters, "OK");
							focus.authUser = us;
							focus.state = 2;
						}else {
							writeLine(focus, letters, "NO Authenticate Failed.");
							focus.state = 0;
						}
					}else if (focus.state >= 2) {
						if (cmd.equals("select")) {
							noCMD = false;
							if (args.length >= 1) {
								String ms = args[0];
								if (ms.startsWith("\"")) {
									ms = ms.substring(1);
								}
								if (ms.endsWith("\"")) {
									ms = ms.substring(0, ms.length() - 1);
								}
								Mailbox m = focus.authUser.getMailbox(ms);
								if (m != null) {
									if (focus.selectedMailbox != null) {
										writeLine(focus, "*", "OK [CLOSED] Previous mailbox closed.");
									}
									focus.selectedMailbox = m;
									focus.state = 3;
									writeLine(focus, "*", "FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)");
									writeLine(focus, "*", "OK [PERMANENTFLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft \\*)] Flags permitted.");
									writeLine(focus, "*", m.emails.size() + " EXISTS");
									int recent = 0;
									for (Email e : m.emails) {
										if (e.flags.contains("\\Recent")) recent++;
									}
									int unseen = 0;
									for (Email e : m.emails) {
										if (!e.flags.contains("\\Seen")) unseen++;
									}
									writeLine(focus, "*", recent + " RECENT");
									writeLine(focus, "*", "OK [UNSEEN " + unseen + "] Unseen messages");
									writeLine(focus, "*", "OK [UIDVALIDITY " + Integer.MAX_VALUE + "] UIDs valid");
									writeLine(focus, "*", "OK [UIDNEXT " + (m.emails.size() + 1) + "] Predicted next UID");
									writeLine(focus, "*", "OK [HIGHESTMODSEQ 1] Highest");
									writeLine(focus, letters, "OK [READ-WRITE] Select completed.");
								}else {
									writeLine(focus, letters, "NO Invalid mailbox.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("examine")) {
							noCMD = false;
							if (args.length >= 1) {
								String ms = args[0];
								if (ms.startsWith("\"")) {
									ms = ms.substring(1);
								}
								if (ms.endsWith("\"")) {
									ms = ms.substring(0, ms.length() - 1);
								}
								Mailbox m = focus.authUser.getMailbox(ms);
								if (m != null) {
									if (focus.selectedMailbox != null) {
										writeLine(focus, "*", "OK [CLOSED] Previous mailbox closed.");
									}
									focus.selectedMailbox = m;
									focus.isExamine = true;
									focus.state = 3;
									writeLine(focus, "*", "FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)");
									writeLine(focus, "*", "OK [PERMANENTFLAGS ()] Read-only mailbox.");
									writeLine(focus, "*", m.emails.size() + " EXISTS");
									int recent = 0;
									for (Email e : m.emails) {
										if (e.flags.contains("\\Recent")) recent++;
									}
									writeLine(focus, "*", recent + " RECENT");
									writeLine(focus, "*", "OK [UIDVALIDITY " + Integer.MAX_VALUE + "] UIDs valid");
									writeLine(focus, "*", "OK [UIDNEXT " + (m.emails.size() + 1) + "] Predicted next UID");
									writeLine(focus, "*", "OK [HIGHESTMODSEQ 1] Highest");
									writeLine(focus, letters, "OK [READ-ONLY] Select completed.");
								}else {
									writeLine(focus, letters, "NO Invalid mailbox.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("create")) {
							noCMD = false;
							if (args.length >= 1) {
								String ms = args[0];
								if (ms.startsWith("\"")) {
									ms = ms.substring(1);
								}
								if (ms.endsWith("\"")) {
									ms = ms.substring(0, ms.length() - 1);
								}
								if (focus.authUser.getMailbox(ms) != null) {
									writeLine(focus, letters, "NO Mailbox Exists.");
								}else {
									focus.authUser.mailboxes.add(new Mailbox(focus.authUser, ms));
									writeLine(focus, letters, "OK Mailbox created.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("delete")) {
							noCMD = false;
							if (args.length >= 1) {
								String ms = args[0];
								if (ms.startsWith("\"")) {
									ms = ms.substring(1);
								}
								if (ms.endsWith("\"")) {
									ms = ms.substring(0, ms.length() - 1);
								}
								Mailbox m = focus.authUser.getMailbox(ms);
								if (m == null || m.name.equals("INBOX") || m.name.equals("Trash")) {
									writeLine(focus, letters, "NO Invalid Mailbox.");
								}else {
									focus.authUser.mailboxes.remove(m);
									writeLine(focus, letters, "OK Mailbox deleted.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("rename")) {
							noCMD = false;
							if (args.length >= 2) {
								String ms = args[0];
								if (ms.startsWith("\"")) {
									ms = ms.substring(1);
								}
								if (ms.endsWith("\"")) {
									ms = ms.substring(0, ms.length() - 1);
								}
								String nn = args[1];
								if (nn.startsWith("\"")) {
									nn = nn.substring(1);
								}
								if (nn.endsWith("\"")) {
									nn = nn.substring(0, ms.length() - 1);
								}
								Mailbox m = focus.authUser.getMailbox(ms);
								if (m == null || m.name.equals("INBOX") || m.name.equals("Trash")) {
									writeLine(focus, letters, "NO Invalid Mailbox.");
								}else {
									m.name = nn;
									writeLine(focus, letters, "OK Mailbox renamed.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("subscribe")) {
							noCMD = false;
							if (args.length >= 1) {
								String ms = args[0];
								if (ms.startsWith("\"")) {
									ms = ms.substring(1);
								}
								if (ms.endsWith("\"")) {
									ms = ms.substring(0, ms.length() - 1);
								}
								Mailbox m = focus.authUser.getMailbox(ms);
								if (m == null) {
									writeLine(focus, letters, "NO Invalid Mailbox.");
								}else {
									m.subscribed = true;
									writeLine(focus, letters, "OK Mailbox subscribed.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("unsubscribe")) {
							noCMD = false;
							if (args.length >= 1) {
								String ms = args[0];
								if (ms.startsWith("\"")) {
									ms = ms.substring(1);
								}
								if (ms.endsWith("\"")) {
									ms = ms.substring(0, ms.length() - 1);
								}
								Mailbox m = focus.authUser.getMailbox(ms);
								if (m == null) {
									writeLine(focus, letters, "NO Invalid Mailbox.");
								}else {
									m.subscribed = false;
									writeLine(focus, letters, "OK Mailbox unsubscribed.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("list")) {
							noCMD = false;
							if (args.length >= 2) {
								String rn = args[0];
								if (rn.startsWith("\"")) {
									rn = rn.substring(1);
								}
								if (rn.endsWith("\"")) {
									rn = rn.substring(0, rn.length() - 1);
								}
								String mn = args[1];
								if (mn.startsWith("\"")) {
									mn = mn.substring(1);
								}
								if (mn.endsWith("\"")) {
									mn = mn.substring(0, mn.length() - 1);
								}
								Mailbox m = mn.length() == 0 && focus.selectedMailbox != null ? focus.selectedMailbox : focus.authUser.getMailbox(mn, true);
								if (m == null) {
									writeLine(focus, letters, "NO Invalid Mailbox.");
								}else {
									if (m != null) {
										writeLine(focus, "*", "LIST (\\HasNoChildren) \"" + rn + "\" \"" + m.name + "\"");
									}
									writeLine(focus, letters, "OK Mailbox list.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("lsub")) {
							noCMD = false;
							if (args.length >= 2) {
								String rn = args[0];
								if (rn.startsWith("\"")) {
									rn = rn.substring(1);
								}
								if (rn.endsWith("\"")) {
									rn = rn.substring(0, rn.length() - 1);
								}
								String mn = args[1];
								if (mn.startsWith("\"")) {
									mn = mn.substring(1);
								}
								if (mn.endsWith("\"")) {
									mn = mn.substring(0, mn.length() - 1);
								}
								Mailbox m = mn.length() == 0 && focus.selectedMailbox != null ? focus.selectedMailbox : focus.authUser.getMailbox(mn, true);
								if (!m.subscribed) {
									m = null;
								}
								if (m == null) {
									writeLine(focus, letters, "NO Invalid Mailbox.");
								}else {
									if (m != null) {
										writeLine(focus, "*", "LIST (\\HasNoChildren) \"" + rn + "\" \"" + m.name + "\"");
									}
									writeLine(focus, letters, "OK Mailbox list.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("status")) {
							noCMD = false;
							if (args.length >= 1) {
								String ms = args[0];
								if (ms.startsWith("\"")) {
									ms = ms.substring(1);
								}
								if (ms.endsWith("\"")) {
									ms = ms.substring(0, ms.length() - 1);
								}
								Mailbox m = focus.authUser.getMailbox(ms);
								if (m == null) {
									writeLine(focus, letters, "NO Invalid Mailbox.");
								}else {
									int recent = 0;
									for (Email e : m.emails) {
										if (e.flags.contains("\\Recent")) recent++;
									}
									int unseen = 0;
									for (Email e : m.emails) {
										if (!e.flags.contains("\\Seen")) unseen++;
									}
									writeLine(focus, "*", "STATUS " + m.name + " (MESSAGES " + m.emails.size() + " RECENT " + recent + " UIDNEXT " + (m.emails.size() + 1) + " UIDVALIDITY " + Integer.MAX_VALUE + " UNSEEN unseen)");
									writeLine(focus, letters, "OK Status.");
								}
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}else if (cmd.equals("append")) {
							noCMD = false;
							if (args.length >= 2) {
								writeLine(focus, letters, "NO Not Implemented.");
							}else {
								writeLine(focus, letters, "BAD No mailbox.");
							}
						}
						if (focus.state >= 3) {
							if (cmd.equals("check")) {
								noCMD = false;
								writeLine(focus, letters, "OK check.");
							}else if (cmd.equals("close")) {
								noCMD = false;
								for (int i = 0; i < focus.selectedMailbox.emails.size(); i++) {
									if (focus.selectedMailbox.emails.get(i).flags.contains("\\Deleted")) {
										focus.selectedMailbox.emails.remove(i);
										i--;
									}
								}
								focus.state = 2;
								focus.selectedMailbox = null;
								writeLine(focus, letters, "OK check.");
							}else if (cmd.equals("expunge")) {
								noCMD = false;
								for (int i = 0; i < focus.selectedMailbox.emails.size(); i++) {
									if (focus.selectedMailbox.emails.get(i).flags.contains("\\Deleted")) {
										focus.selectedMailbox.emails.remove(i);
										writeLine(focus, "*", (i + 1) + " EXPUNGE");
										i--;
									}
								}
								writeLine(focus, letters, "OK expunge.");
							}else if (cmd.equals("search")) {
								writeLine(focus, letters, "NO Not yet implemented.");
								noCMD = false;
							}else if (cmd.equals("fetch")) {
								noCMD = false;
							}else if (cmd.equals("store")) {
								noCMD = false;
							}else if (cmd.equals("copy")) {
								noCMD = false;
							}else if (cmd.equals("uid")) {
								noCMD = false;
							}
						}
					}
					if (noCMD) {
						writeLine(focus, letters, "BAD Command not recognized");
					}
				}
			}catch (IOException e) {
				if (!(e instanceof SocketException)) e.printStackTrace();
			}
			if (focus != null && !focus.s.isClosed()) workQueue.add(focus);
		}
	}
}
