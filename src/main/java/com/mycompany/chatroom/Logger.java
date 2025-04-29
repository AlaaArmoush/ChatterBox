package com.mycompany.chatroom;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static Logger instance;
    private PrintWriter writer;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Logger() {
        try {
            writer = new PrintWriter(new FileWriter("app.log", true), true);
        } catch (IOException e) {
            throw new RuntimeException("Cannot open log file", e);
        }
    }

    public static synchronized Logger getInstance(){
        if(instance == null)
            instance = new Logger();
        return instance;
    }

    public synchronized void log(String level, String msg){
        String timeStamp = LocalDateTime.now().format(formatter);
        writer.printf("%s [%s] %s%n", timeStamp, level, msg);
    }

    public void info(String msg)  { log("INFO",  msg); }
    public void warn(String msg)  { log("WARN",  msg); }
    public void error(String msg) { log("ERROR", msg); }

}
