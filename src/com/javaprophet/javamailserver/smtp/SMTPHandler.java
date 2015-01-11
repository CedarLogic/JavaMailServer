package com.javaprophet.javamailserver.smtp;

import java.io.IOException;
import java.util.ArrayList;
import javax.xml.bind.DatatypeConverter;
import com.javaprophet.javamailserver.JavaMailServer;
import com.javaprophet.javamailserver.mailbox.Email;
import com.javaprophet.javamailserver.mailbox.EmailAccount;
import com.javaprophet.javamailserver.mailbox.EmailRouter;

public class SMTPHandler {
	public static final ArrayList<SMTPCommand> commands = new ArrayList<SMTPCommand>();
	static {
		commands.add(new SMTPCommand("helo", 0, 100) {
			public void run(SMTPWork focus, String line) throws IOException {
				focus.writeLine(250, "OK");
				focus.state = 1;
				focus.isExtended = false;
			}
		});
		
		commands.add(new SMTPCommand("ehlo", 0, 100) {
			public void run(SMTPWork focus, String line) throws IOException {
				focus.writeMLine(250, (String)JavaMailServer.mainConfig.get("domain"));
				focus.writeMLine(250, "AUTH PLAIN LOGIN");
				focus.writeLine(250, "AUTH=PLAIN LOGIN");
				focus.state = 1;
				focus.isExtended = true;
			}
		});
		
		commands.add(new SMTPCommand("auth", 1, 100) {
			public void run(SMTPWork focus, String line) throws IOException {
				if (line.toUpperCase().startsWith("PLAIN")) {
					line = line.substring(6).trim();
					if (line.length() > 0) {
						String up = new String(DatatypeConverter.parseBase64Binary(line)).substring(1);
						String username = up.substring(0, up.indexOf(new String(new byte[]{0})));
						String password = up.substring(username.length() + 1);
						System.out.println(username + ":" + password);
						EmailAccount us = null;
						for (EmailAccount e : JavaMailServer.accounts) {
							if (e.email.equals(username) && e.password.equals(password)) {
								us = e;
								break;
							}
						}
						if (us != null) {
							focus.writeLine(250, "OK");
							focus.authUser = us;
							focus.state = 2;
						}else {
							focus.writeLine(535, "authentication failed");
						}
					}else {
						focus.writeLine(501, "Syntax error in parameters or arguments");
					}
				}else {
					focus.writeLine(501, "Syntax error in parameters or arguments");
				}
			}
		});
		
		commands.add(new SMTPCommand("mail", 1, 3) {
			public void run(SMTPWork focus, String line) throws IOException {
				if (line.toLowerCase().startsWith("from:")) {
					focus.mailFrom = line.substring(5).trim();
					if (focus.mailFrom.endsWith("@" + JavaMailServer.mainConfig.get("domain")) && focus.authUser == null) {
						focus.writeLine(535, "NO Not Authorized");
						return;
					}
					focus.rcptTo.clear();
					focus.data.clear();
					focus.state = 3;
					focus.writeLine(250, "OK");
				}else {
					focus.writeLine(500, "Lacking FROM");
				}
			}
		});
		
		commands.add(new SMTPCommand("rcpt", 3, 4) {
			public void run(SMTPWork focus, String line) throws IOException {
				if (line.toLowerCase().startsWith("to:")) {
					focus.rcptTo.add(line.substring(3).trim());
					focus.state = 4;
					focus.writeLine(250, "OK");
				}else {
					focus.writeLine(500, "Lacking TO");
				}
			}
		});
		
		commands.add(new SMTPCommand("data", 4, 4) {
			public void run(SMTPWork focus, String line) throws IOException {
				focus.state = 101;
				focus.writeLine(354, "Start mail input; end with <CRLF>.<CRLF>");
			}
		});
		
		commands.add(new SMTPCommand("", 101, 101) {
			public void run(SMTPWork focus, String line) throws IOException {
				if (!line.equals(".")) {
					focus.data.add(line);
				}else {
					focus.state = 1;
					String data = "";
					for (String l : focus.data) {
						data += l + JavaMailServer.crlf;
					}
					Email email = new Email(data, -1, focus.mailFrom);
					email.to.addAll(focus.rcptTo);
					EmailRouter.route(email);
					focus.writeLine(250, "OK");
				}
			}
		});
		
		commands.add(new SMTPCommand("quit", 1, 100) {
			public void run(SMTPWork focus, String line) throws IOException {
				focus.writeLine(251, (String)JavaMailServer.mainConfig.get("domain") + " terminating connection.");
				focus.s.close();
			}
		});
	}
}
