package ru.ramprox.netty;

import io.netty.channel.Channel;

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
    private Channel channel;

    private static int userCount = 1;

    public User(Channel channel) throws IOException {
        this.channel = channel;
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

    public Channel getChannel() {
        return channel;
    }
}
