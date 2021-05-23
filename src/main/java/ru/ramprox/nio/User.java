package ru.ramprox.nio;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class User {
    private String login;
    private String nick;
    private Path homeDir;
    private Path currentDir;
    private SocketAddress address;

    private static int userCount = 1;

    public User(SocketAddress address) throws IOException {
        this.address = address;
        login = "user" + userCount++;
        nick = login;
        homeDir = Paths.get(login);
        if(!Files.exists(homeDir)) {
            Files.createDirectories(homeDir);
        }
        currentDir = homeDir;
    }

    public String getPrompt() {
        String result = nick + " ~";
        if(!currentDir.equals(homeDir)) {
            result += File.separator + homeDir.relativize(currentDir).toString();
        }
        result += " > ";
        return result;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public void setCurrentDir(Path currentDir) {
        this.currentDir = currentDir;
    }

    public Path getCurrentDir() {
        return currentDir;
    }

    public Path getHomeDir() {
        return homeDir;
    }

    public SocketAddress getAddress() {
        return address;
    }
}
