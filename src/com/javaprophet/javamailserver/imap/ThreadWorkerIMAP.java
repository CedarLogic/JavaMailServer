package com.javaprophet.javamailserver.imap;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;
import com.javaprophet.javamailserver.util.StringFormatter;

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
						args = StringFormatter.congealBySurroundings(line.split(" "), "(", ")");
					}else {
						letters = line;
						cmd = "";
						args = new String[0];
					}
					boolean r = false;
					for (IMAPCommand comm : IMAPHandler.commands) {
						if ((focus.state == 1 ? comm.comm.equals("") : comm.comm.equals(cmd)) && comm.minState <= focus.state && comm.maxState >= focus.state) {
							comm.run(focus, letters, args);
							r = true;
							break;
						}
					}
					if (!r) {
						focus.writeLine(focus, letters, "BAD Command not recognized");
					}
				}
			}catch (IOException e) {
				if (!(e instanceof SocketException)) e.printStackTrace();
			}
			if (focus != null && !focus.s.isClosed()) workQueue.add(focus);
		}
	}
}
