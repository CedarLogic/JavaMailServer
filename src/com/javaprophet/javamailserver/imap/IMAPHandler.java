package com.javaprophet.javamailserver.imap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import javax.xml.bind.DatatypeConverter;
import com.javaprophet.javamailserver.JavaMailServer;
import com.javaprophet.javamailserver.mailbox.Email;
import com.javaprophet.javamailserver.mailbox.EmailAccount;
import com.javaprophet.javamailserver.mailbox.Mailbox;

public class IMAPHandler {
	public static final ArrayList<IMAPCommand> commands = new ArrayList<IMAPCommand>();
	static {
		commands.add(new IMAPCommand("capability", 0, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				focus.writeLine(focus, "*", "CAPABILITY IMAP4rev1 AUTH=PLAIN LOGINDISABLED");
				focus.writeLine(focus, letters, "OK Capability completed.");
			}
			
		});
		commands.add(new IMAPCommand("logout", 0, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				focus.writeLine(focus, letters, "OK " + (String)JavaMailServer.mainConfig.get("domain") + " terminating connection.");
				focus.s.close();
			}
			
		});
		commands.add(new IMAPCommand("noop", 0, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				focus.writeLine(focus, letters, "OK");
			}
			
		});
		commands.add(new IMAPCommand("authenticate", 0, 0) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				if (args.length >= 1 && args[0].equals("plain")) {
					focus.writeLine(focus, "", "+");
					focus.state = 1;
					focus.authMethod = "plain" + letters;
				}else {
					focus.writeLine(focus, letters, "BAD No type.");
				}
			}
			
		});
		commands.add(new IMAPCommand("", 1, 1) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				String up = new String(DatatypeConverter.parseBase64Binary(letters)).substring(1);
				String username = up.substring(0, up.indexOf(new String(new byte[]{0})));
				String password = up.substring(username.length() + 1);
				String letters2 = focus.authMethod.substring(5);
				System.out.println(username + ":" + password);
				EmailAccount us = null;
				for (EmailAccount e : JavaMailServer.accounts) {
					if (e.email.equals(username) && e.password.equals(password)) {
						us = e;
						break;
					}
				}
				if (us != null) {
					focus.writeLine(focus, letters2, "OK");
					focus.authUser = us;
					focus.state = 2;
				}else {
					focus.writeLine(focus, letters2, "NO Authenticate Failed.");
					focus.state = 0;
				}
			}
			
		});
		
		commands.add(new IMAPCommand("select", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
							focus.writeLine(focus, "*", "OK [CLOSED] Previous mailbox closed.");
						}
						focus.selectedMailbox = m;
						focus.state = 3;
						focus.writeLine(focus, "*", "FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)");
						focus.writeLine(focus, "*", "OK [PERMANENTFLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft \\*)] Flags permitted.");
						focus.writeLine(focus, "*", m.emails.size() + " EXISTS");
						int recent = 0;
						for (Email e : m.emails) {
							if (e.flags.contains("\\Recent")) recent++;
						}
						int unseen = 0;
						for (Email e : m.emails) {
							if (!e.flags.contains("\\Seen")) unseen++;
						}
						focus.writeLine(focus, "*", recent + " RECENT");
						focus.writeLine(focus, "*", "OK [UNSEEN " + unseen + "] Unseen messages");
						focus.writeLine(focus, "*", "OK [UIDVALIDITY " + Integer.MAX_VALUE + "] UIDs valid");
						focus.writeLine(focus, "*", "OK [UIDNEXT " + (m.emails.size() + 1) + "] Predicted next UID");
						focus.writeLine(focus, "*", "OK [HIGHESTMODSEQ 1] Highest");
						focus.writeLine(focus, letters, "OK [READ-WRITE] Select completed.");
					}else {
						focus.writeLine(focus, letters, "NO Invalid mailbox.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("examine", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
							focus.writeLine(focus, "*", "OK [CLOSED] Previous mailbox closed.");
						}
						focus.selectedMailbox = m;
						focus.isExamine = true;
						focus.state = 3;
						focus.writeLine(focus, "*", "FLAGS (\\Answered \\Flagged \\Deleted \\Seen \\Draft)");
						focus.writeLine(focus, "*", "OK [PERMANENTFLAGS ()] Read-only mailbox.");
						focus.writeLine(focus, "*", m.emails.size() + " EXISTS");
						int recent = 0;
						for (Email e : m.emails) {
							if (e.flags.contains("\\Recent")) recent++;
						}
						focus.writeLine(focus, "*", recent + " RECENT");
						focus.writeLine(focus, "*", "OK [UIDVALIDITY " + Integer.MAX_VALUE + "] UIDs valid");
						focus.writeLine(focus, "*", "OK [UIDNEXT " + (m.emails.size() + 1) + "] Predicted next UID");
						focus.writeLine(focus, "*", "OK [HIGHESTMODSEQ 1] Highest");
						focus.writeLine(focus, letters, "OK [READ-ONLY] Select completed.");
					}else {
						focus.writeLine(focus, letters, "NO Invalid mailbox.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("create", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				if (args.length >= 1) {
					String ms = args[0];
					if (ms.startsWith("\"")) {
						ms = ms.substring(1);
					}
					if (ms.endsWith("\"")) {
						ms = ms.substring(0, ms.length() - 1);
					}
					if (focus.authUser.getMailbox(ms) != null) {
						focus.writeLine(focus, letters, "NO Mailbox Exists.");
					}else {
						focus.authUser.mailboxes.add(new Mailbox(focus.authUser, ms));
						focus.writeLine(focus, letters, "OK Mailbox created.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("delete", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
						focus.writeLine(focus, letters, "NO Invalid Mailbox.");
					}else {
						focus.authUser.mailboxes.remove(m);
						focus.writeLine(focus, letters, "OK Mailbox deleted.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("rename", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
						focus.writeLine(focus, letters, "NO Invalid Mailbox.");
					}else {
						m.name = nn;
						focus.writeLine(focus, letters, "OK Mailbox renamed.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("subscribe", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
						focus.writeLine(focus, letters, "NO Invalid Mailbox.");
					}else {
						m.subscribed = true;
						focus.writeLine(focus, letters, "OK Mailbox subscribed.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("unsubscribe", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
						focus.writeLine(focus, letters, "NO Invalid Mailbox.");
					}else {
						m.subscribed = false;
						focus.writeLine(focus, letters, "OK Mailbox unsubscribed.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("list", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
						focus.writeLine(focus, letters, "NO Invalid Mailbox.");
					}else {
						if (m != null) {
							focus.writeLine(focus, "*", "LIST (\\HasNoChildren) \"" + rn + "\" \"" + m.name + "\"");
						}
						focus.writeLine(focus, letters, "OK Mailbox list.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("lsub", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
						focus.writeLine(focus, letters, "NO Invalid Mailbox.");
					}else {
						if (m != null) {
							focus.writeLine(focus, "*", "LIST (\\HasNoChildren) \"" + rn + "\" \"" + m.name + "\"");
						}
						focus.writeLine(focus, letters, "OK Mailbox list.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("status", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
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
						focus.writeLine(focus, letters, "NO Invalid Mailbox.");
					}else {
						int recent = 0;
						for (Email e : m.emails) {
							if (e.flags.contains("\\Recent")) recent++;
						}
						int unseen = 0;
						for (Email e : m.emails) {
							if (!e.flags.contains("\\Seen")) unseen++;
						}
						focus.writeLine(focus, "*", "STATUS " + m.name + " (MESSAGES " + m.emails.size() + " RECENT " + recent + " UIDNEXT " + (m.emails.size() + 1) + " UIDVALIDITY " + Integer.MAX_VALUE + " UNSEEN unseen)");
						focus.writeLine(focus, letters, "OK Status.");
					}
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("append", 2, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				if (args.length >= 2) {
					focus.writeLine(focus, letters, "NO Not Implemented.");
				}else {
					focus.writeLine(focus, letters, "BAD No mailbox.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("check", 3, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				focus.writeLine(focus, letters, "OK check.");
			}
			
		});
		
		commands.add(new IMAPCommand("close", 3, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				for (int i = 0; i < focus.selectedMailbox.emails.size(); i++) {
					if (focus.selectedMailbox.emails.get(i).flags.contains("\\Deleted")) {
						focus.selectedMailbox.emails.remove(i);
						i--;
					}
				}
				focus.state = 2;
				focus.selectedMailbox = null;
				focus.writeLine(focus, letters, "OK close.");
			}
			
		});
		
		commands.add(new IMAPCommand("expunge", 3, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				for (int i = 0; i < focus.selectedMailbox.emails.size(); i++) {
					if (focus.selectedMailbox.emails.get(i).flags.contains("\\Deleted")) {
						focus.selectedMailbox.emails.remove(i);
						focus.writeLine(focus, "*", (i + 1) + " EXPUNGE");
						i--;
					}
				}
				focus.writeLine(focus, letters, "OK expunge.");
			}
			
		});
		
		commands.add(new IMAPCommand("search", 3, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				if (args.length >= 2) {
					args = new String[]{args[1]};
				}
				if (args.length >= 1) {
					focus.writeLine(focus, "*", "SEARCH");
				}
				focus.writeLine(focus, letters, "OK Not yet implemented.");
			}
			
		});
		
		commands.add(new IMAPCommand("fetch", 3, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				if (args.length >= 2) {
					String seq = args[0];
					ArrayList<Email> toFetch = new ArrayList<Email>();
					if (seq.contains(":")) {
						int i = Integer.parseInt(seq.substring(0, seq.indexOf(":"))) - 1;
						String f = seq.substring(seq.indexOf(":") + 1);
						int f2 = f.equals("*") ? focus.selectedMailbox.emails.size() : Integer.parseInt(f) - 1;
						for (; i < f2; i++) {
							toFetch.add(focus.selectedMailbox.emails.get(i));
						}
					}else {
						if (seq.equals("*")) {
							toFetch.add(focus.selectedMailbox.emails.get(focus.selectedMailbox.emails.size() - 1));
						}else {
							toFetch.add(focus.selectedMailbox.emails.get(Integer.parseInt(seq) - 1));
						}
					}
					String[] tps = args[1].substring(1, args[1].length() - 1).split(" ");
					String[] ttps = new String[tps.length];
					String ctps = "";
					int cloc = 0;
					int clen = 0;
					boolean act = false;
					int nlen = tps.length;
					for (int i = 0; i < tps.length; i++) {
						if (!act && tps[i].contains("[")) {
							act = true;
							ctps = "";
							cloc = i;
							nlen += 1;
						}
						if (act) {
							ctps += tps[i] + " ";
							clen++;
							nlen--;
							if (tps[i].contains("]")) {
								ctps = ctps.trim();
								ttps[cloc] = ctps;
								act = false;
							}
						}else {
							ttps[i] = tps[i];
						}
					}
					tps = new String[nlen];
					for (int i = 0; i < nlen; i++) {
						tps[i] = ttps[i];
					}
					for (Email e : toFetch) {
						String ret = e.uid + " FETCH (";
						for (String s : tps) {
							s = s.toLowerCase();
							if (s.equals("uid")) {
								
							}else if (s.equals("rfc822.size")) {
								
							}else if (s.equals("flags")) {
								ret += "FLAGS (";
								for (String flag : e.flags) {
									ret += flag + " ";
								}
								ret = ret.trim();
								ret += ")";
							}else if (s.equals("body.peek")) {
								
							}
							ret += " ";
						}
						ret = ret.trim();
						ret += ")";
						focus.writeLine(focus, "*", ret);
					}
					focus.writeLine(focus, letters, "OK");
				}else {
					focus.writeLine(focus, letters, "BAD Missing Arguments.");
				}
			}
			
		});
		
		commands.add(new IMAPCommand("store", 3, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				focus.writeLine(focus, letters, "NO Not yet implemented.");
			}
			
		});
		
		commands.add(new IMAPCommand("copy", 3, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				focus.writeLine(focus, letters, "NO Not yet implemented.");
			}
			
		});
		
		commands.add(new IMAPCommand("uid", 3, 100) {
			
			@Override
			public void run(IMAPWork focus, String letters, String[] args) throws IOException {
				if (args.length >= 1) {
					if (args[0].toLowerCase().equals("fetch")) {
						if (args.length >= 3) {
							String seq = args[1];
							ArrayList<Email> toFetch = new ArrayList<Email>();
							if (seq.contains(":")) {
								int i = Integer.parseInt(seq.substring(0, seq.indexOf(":"))) - 1;
								String f = seq.substring(seq.indexOf(":") + 1);
								int f2 = f.equals("*") ? focus.selectedMailbox.emails.size() : Integer.parseInt(f) - 1;
								for (; i < f2; i++) {
									toFetch.add(focus.selectedMailbox.emails.get(i));
								}
							}else {
								if (seq.equals("*")) {
									toFetch.add(focus.selectedMailbox.emails.get(focus.selectedMailbox.emails.size() - 1));
								}else {
									toFetch.add(focus.selectedMailbox.emails.get(Integer.parseInt(seq) - 1));
								}
							}
							String[] tps = args[2].substring(1, args[2].length() - 1).split(" ");
							String[] ttps = new String[tps.length];
							String ctps = "";
							int cloc = 0;
							int clen = 0;
							boolean act = false;
							int nlen = tps.length;
							for (int i = 0; i < tps.length; i++) {
								if (!act && tps[i].contains("[")) {
									act = true;
									ctps = "";
									cloc = i;
									nlen += 1;
								}
								if (act) {
									ctps += tps[i] + " ";
									clen++;
									nlen--;
									if (tps[i].contains("]")) {
										ctps = ctps.trim();
										ttps[cloc] = ctps;
										act = false;
									}
								}else {
									ttps[i] = tps[i];
								}
							}
							tps = new String[nlen];
							for (int i = 0; i < nlen; i++) {
								tps[i] = ttps[i];
							}
							for (Email e : toFetch) {
								String ret = e.uid + " FETCH (UID " + e.uid + " ";
								for (String s : tps) {
									s = s.toLowerCase();
									if (s.equals("uid")) {
										// uid already
									}else if (s.equals("rfc822.size")) {
										ret += "RFC822.SIZE " + e.data.length();
									}else if (s.equals("flags")) {
										ret += "FLAGS (";
										for (String flag : e.flags) {
											ret += flag + " ";
										}
										ret = ret.trim();
										ret += ")";
									}else if (s.startsWith("body") && s.contains("[")) {
										s = s.substring(s.indexOf("["), s.length() - 1);
										String[] headers = s.split(" "); // TODO: no, check if its HEADER.FIELDS...
										for (String header : headers) {
											Scanner scan = new Scanner(e.data);
											while (scan.hasNextLine()) {
												String lne = scan.nextLine().trim();
												if (lne.length() <= 0 || !lne.contains(":")) {
													break;
												}
												
											}
										}
									}
									ret += " ";
								}
								ret = ret.trim();
								ret += ")";
								focus.writeLine(focus, "*", ret);
							}
							focus.writeLine(focus, letters, "OK");
						}else {
							focus.writeLine(focus, letters, "BAD Missing Arguments.");
						}
					}
				}else {
					focus.writeLine(focus, letters, "BAD Missing Arguments.");
				}
			}
			
		});
		
	}
}
