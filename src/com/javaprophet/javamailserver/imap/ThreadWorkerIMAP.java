package com.javaprophet.javamailserver.imap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadWorkerIMAP extends Thread {
	
	public ThreadWorkerIMAP() {
		
	}
	
	public static void clearWork() {
		workQueue.clear();
	}
	
	private static LinkedBlockingQueue<IMAPWork> workQueue = new LinkedBlockingQueue<IMAPWork>();
	
	public static void addWork(Socket s, DataInputStream in, DataOutputStream out, boolean ssl) {
		workQueue.add(new IMAPWork(s, in, out, ssl));
	}
	
	private boolean keepRunning = true;
	
	public void close() {
		keepRunning = false;
	}
	
	public void run() {
		while (keepRunning) {
			IMAPWork focus = workQueue.poll();
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
						String[] nargs = new String[args.length];
						String ctps = "";
						int cloc = 0;
						int clen = 0;
						boolean act = false;
						int nlen = args.length;
						for (int i = 0; i < args.length; i++) {
							if (!act && args[i].contains("(")) {
								act = true;
								ctps = "";
								cloc = i;
								nlen += 1;
							}
							if (act) {
								ctps += args[i] + " ";
								clen++;
								nlen--;
								if (args[i].contains(")")) {
									ctps = ctps.trim();
									nargs[cloc] = ctps;
									act = false;
								}
							}else {
								nargs[i] = args[i];
							}
						}
						args = new String[nlen];
						for (int i = 0; i < nlen; i++) {
							args[i] = nargs[i];
						}
					}else {
						letters = line;
						cmd = "";
						args = new String[0];
					}
					for (IMAPCommand comm : IMAPHandler.commands) {
						if (focus.state == 1 ? comm.comm.equals("") : comm.comm.equals(cmd)) {
							comm.run(focus, letters, args);
						}
					}
				}
			}catch (IOException e) {
				if (!(e instanceof SocketException)) e.printStackTrace();
			}
			if (focus != null && !focus.s.isClosed()) workQueue.add(focus);
		}
	}
}
