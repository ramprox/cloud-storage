package ru.ramprox.netty.handlers;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import ru.ramprox.netty.User;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChatMessageHandler extends SimpleChannelInboundHandler<String> {

	public static final ConcurrentLinkedQueue<User> users = new ConcurrentLinkedQueue<>();

	public static final String LS_COMMAND = "\tls    view all files and directories\r\n";
	public static final String MKDIR_COMMAND = "\tmkdir    create directory\r\n";
	public static final String CHANGE_NICKNAME = "\tnick    change nickname\r\n";
	public static final String TOUCH_COMMAND = "\ttouch [filename]    create new file\r\n";
	public static final String CD_COMMAND = "\tcd [path]    change directory (.. - parent directory, ~ - home directory)\r\n";
	public static final String RM_COMMAND = "\trm [filename | dirname]    remove file or directory\r\n";
	public static final String COPY_COMMAND = "\tcopy [src] [target]    copy file or directory\r\n";
	public static final String CAT_COMMAND = "\tcat [filename]   browse file\r\n";
	public static final String INCORRECT_COMMAND = "Incorrect command. Use --help to show info.\r\n";

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		System.out.println("Client connected: " + ctx.channel());
		users.add(new User(ctx.channel()));
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, String command) throws Exception {
		SocketChannel channel = (SocketChannel) ctx.channel();
		User user = getUserByAddress(channel);
		command = command.replace("\r", "").replace("\n", "");
		if ("--help".equals(command)) {
			sendMessage(LS_COMMAND, ctx);
			sendMessage(MKDIR_COMMAND, ctx);
			sendMessage(CHANGE_NICKNAME, ctx);
			sendMessage(TOUCH_COMMAND, ctx);
			sendMessage(CD_COMMAND, ctx);
			sendMessage(RM_COMMAND, ctx);
			sendMessage(COPY_COMMAND, ctx);
			sendMessage(CAT_COMMAND, ctx);
		} else if ("ls".equals(command)) {
			sendMessage(getFileList(user).concat("\r\n"), ctx);
		} else if ("exit".equals(command)) {
			System.out.println("Client logged out. IP: " + channel.remoteAddress());
			channel.close();
			users.remove(user);
			return;
		} else if(command.startsWith("mkdir ")) {
			String[] comWithArgs = command.split(" ", 2);
			if(comWithArgs.length == 2) {
				handleMakeDirCommand(comWithArgs[1], ctx, user);
			} else {
				sendMessage(INCORRECT_COMMAND, ctx);
			}
		} else if(command.startsWith("nick ")) {
			String[] comWithArgs = command.split(" ");
			if(comWithArgs.length == 2) {
				user.setNick(comWithArgs[1]);
			} else {
				sendMessage(INCORRECT_COMMAND, ctx);
			}
		} else if(command.startsWith("touch ")) {
			String[] comWithArgs = command.split(" ", 2);
			if(comWithArgs.length == 2) {
				handleTouchFile(comWithArgs[1], ctx, user);
			} else {
				sendMessage(INCORRECT_COMMAND, ctx);
			}
		} else if(command.startsWith("cd ")) {
			String[] comWithArgs = command.split(" ", 2);
			if(comWithArgs.length == 2) {
				handleChangeDirCommand(comWithArgs[1], ctx, user);
			} else {
				sendMessage(INCORRECT_COMMAND, ctx);
			}
		} else if(command.startsWith("rm ")) {
			String[] comWithArgs = command.split(" ", 2);
			if(comWithArgs.length == 2) {
				handleRemoveCommand(comWithArgs[1], ctx, user);
			} else {
				sendMessage(INCORRECT_COMMAND, ctx);
			}
		} else if(command.startsWith("copy ")) {
			String[] comWithArgs = command.split(" ");
			if(comWithArgs.length == 3) {
				handleCopyCommand(comWithArgs[1], comWithArgs[2], ctx, user);
			} else {
				sendMessage(INCORRECT_COMMAND, ctx);
			}
		} else if(command.startsWith("cat ")) {
			String[] comWithArgs = command.split(" ");
			if(comWithArgs.length == 2) {
				handleCatCommand(comWithArgs[1], ctx, user);
			} else {
				sendMessage(INCORRECT_COMMAND, ctx);
			}
		}
		sendMessage(user.getPrompt(), ctx);
	}

	private User getUserByAddress(Channel channel) {
		for(User user : users) {
			if(user.getChannel().equals(channel)) {
				return user;
			}
		}
		return null;
	}

	private void handleMakeDirCommand(String dirName, ChannelHandlerContext ctx, User user) throws IOException {
		Path newDir = user.getCurrentDir().resolve(Paths.get(dirName));
		if(!Files.exists(newDir)) {
			Files.createDirectory(newDir);
		} else {
			sendMessage(dirName + " already exist\r\n", ctx);
		}
	}

	private void handleTouchFile(String fileName, ChannelHandlerContext ctx, User user) throws IOException {
		Path newFile = user.getCurrentDir().resolve(Paths.get(fileName));
		if(!Files.exists(newFile)) {
			Files.createFile(newFile);
		} else {
			sendMessage(fileName + " is already exist\r\n", ctx);
		}
	}

	private void handleChangeDirCommand(String path, ChannelHandlerContext ctx, User user) throws IOException {
		if(path.equals("..")) {
			Path parentDir = user.getCurrentDir().getParent();
			if(!user.getCurrentDir().equals(user.getHomeDir())) {
				user.setCurrentDir(parentDir);
			}
		} else if(path.equals("~")) {
			user.setCurrentDir(user.getHomeDir());
		} else {
			Path newPath = user.getCurrentDir().resolve(Paths.get(path));
			if(Files.isDirectory(newPath)) {
				user.setCurrentDir(newPath);
			} else {
				sendMessage(path + " is not a directory or not exist\r\n", ctx);
			}
		}
	}

	private void handleRemoveCommand(String strPath, ChannelHandlerContext ctx, User user) throws IOException {
		Path filePath = user.getCurrentDir().resolve(Paths.get(strPath));
		if(!Files.exists(filePath)) {
			sendMessage(strPath + " is not exist\r\n", ctx);
			return;
		}
		if(Files.isDirectory(filePath)) {
			Files.walkFileTree(filePath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}
			});
		} else {
			Files.delete(filePath);
		}
	}

	private void handleCopyCommand(String srcStrPath, String targetStrPath, ChannelHandlerContext ctx, User user) throws IOException {
		Path srcPath = user.getCurrentDir().resolve(Paths.get(srcStrPath));
		Path targetPath = user.getCurrentDir().resolve(Paths.get(targetStrPath));
		if(!Files.exists(srcPath)) {
			sendMessage(srcStrPath + " is not exist\r\n", ctx);
			return;
		}
		if(Files.isDirectory(srcPath) && Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
			sendMessage("Can't copy directory to not directory\r\n", ctx);
			return;
		}
		if(Files.isDirectory(srcPath)) {
			if(Files.exists(targetPath)) {
				if(Files.isDirectory(targetPath)) {
					targetPath = targetPath.resolve(srcPath.getParent().relativize(srcPath)); // копируем исходную папку в подпапку targetPath
				}
			}
			copyDir(srcPath, targetPath);
		} else {
			if(Files.isDirectory(targetPath)) {
				targetPath = targetPath.resolve(srcPath.getParent().relativize(srcPath));
			}
			Files.copy(srcPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void copyDir(Path srcPath, Path targetPath) throws IOException {
		Files.walkFileTree(srcPath, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path tempTargetDir = targetPath.resolve(srcPath.relativize(dir));
				if(!Files.exists(tempTargetDir)) {
					Files.createDirectory(tempTargetDir);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path targetFile = targetPath.resolve(srcPath.relativize(file));
				Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void handleCatCommand(String filename, ChannelHandlerContext ctx, User user) throws IOException {
		Path file = user.getCurrentDir().resolve(Paths.get(filename));
		if(!Files.exists(file)) {
			sendMessage(filename + " is not exist", ctx);
		}
		if(!Files.isDirectory(file)) {
			List<String> str = Files.readAllLines(file);
			for(String s : str) {
				ctx.writeAndFlush(s + "\r\n");
			}
		} else {
			sendMessage(filename + " is a directory\r\n", ctx);
		}
	}

	private String getFileList(User user) {
		return String.join(" ", user.getCurrentDir().toFile().list());
	}

	private void sendMessage(String message, ChannelHandlerContext ctx) {
		ctx.writeAndFlush(message);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		System.out.println("Client disconnected: " + ctx.channel());
	}
}
