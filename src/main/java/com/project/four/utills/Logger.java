package com.project.four.utills;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * The Logger class provides a simple logging mechanism for the project.
 * It supports logging to a specified file and console, with options for enabling/disabling console logs.
 */
public class Logger {
	private String fileName = "";
	private File logFile = null;
	private PrintWriter logWriter = null;
	private boolean isLogs = false;

	/**
	 * Default constructor for Logger, initializes with a default log file.
	 *
	 * @throws IOException if an I/O error occurs during file creation.
	 */
	public Logger () throws IOException {
		this.initialize(null);
	}

	/**
	 * Constructor for Logger with a specified log file name.
	 *
	 * @param fileName The name of the log file.
	 * @throws IOException if an I/O error occurs during file creation.
	 */
	public Logger (String fileName) throws IOException {
		this.initialize(fileName);
	}

	/**
	 * Initializes the logger with the specified log file name or a default name.
	 *
	 * @param fileName The name of the log file.
	 * @throws IOException if an I/O error occurs during file creation.
	 */
	private void initialize (String fileName) throws IOException {
		String logDir = "./logs";
		File logFDir = new File (logDir);
		if (!logFDir.exists()) {
			logFDir.mkdirs();
		}
		this.fileName = (fileName == null || fileName == "") ? "Server_log_" + System.currentTimeMillis() + ".txt" : fileName;
		this.fileName = logDir + "/" + this.fileName;
		this.logFile = new File(this.fileName);
		if (this.logFile.exists()) {
			this.logFile.delete();
		}
		this.logFile.createNewFile();
		this.logWriter = new PrintWriter(this.logFile);
	}
	
	public void close () {
		this.logWriter.close();
	}
	
	public void loggerEx (Exception e) {
		e.printStackTrace(this.logWriter);
	}

	/**
	 * Logs a message to the file and optionally to the console if console logging is enabled.
	 *
	 * @param msg The message to be logged.
	 */
	public void logger (String msg) {
		try {
			this.logWriter.println(msg);
			this.logWriter.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (this.isLogs) {
			System.out.println(msg + " - " + System.currentTimeMillis());
		}
	}

	public boolean isLogs() {
		return isLogs;
	}

	public void setLogs(boolean isLogs) {
		this.isLogs = isLogs;
	}
	
	
}
