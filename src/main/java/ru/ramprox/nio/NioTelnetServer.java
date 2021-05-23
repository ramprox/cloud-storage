package ru.ramprox.nio;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class NioTelnetServer {
	public static final String LS_COMMAND = "\tls    view all files and directories\r\n";
	public static final String MKDIR_COMMAND = "\tmkdir    create directory\r\n";
	public static final String CHANGE_NICKNAME = "\tnick    change nickname\r\n";
	public static final String TOUCH_COMMAND = "\ttouch [filename]    create new file\r\n";
	public static final String CD_COMMAND = "\tcd [path]    change directory (.. - parent directory, ~ - home directory)\r\n";
	public static final String RM_COMMAND = "\trm [filename | dirname]    remove file or directory\r\n";
	public static final String COPY_COMMAND = "\tcopy [src] [target]    copy file or directory\r\n";
	public static final String CAT_COMMAND = "\tcat [filename]   browse file\r\n";
	public static final String INCORRECT_COMMAND = "Incorrect command. Use --help to show info.\r\n";

	private final ByteBuffer buffer = ByteBuffer.allocate(512);

	private final List<User> users = new LinkedList<>();

	public NioTelnetServer() throws IOException {
		ServerSocketChannel server = ServerSocketChannel.open();
		server.bind(new InetSocketAddress(5678));
		server.configureBlocking(false);
		// OP_ACCEPT, OP_READ, OP_WRITE
		Selector selector = Selector.open();

		server.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Server started");

		while (server.isOpen()) {
			selector.select();

			Set<SelectionKey> selectionKeys = selector.selectedKeys();
			Iterator<SelectionKey> iterator = selectionKeys.iterator();

			while (iterator.hasNext()) {
				SelectionKey key = iterator.next();
				if (key.isAcceptable()) {
					handleAccept(key, selector);
				} else if (key.isReadable()) {
					handleRead(key, selector);
				}
				iterator.remove();
			}
		}
	}

	private void handleRead(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((SocketChannel) key.channel());
		SocketAddress client = channel.getRemoteAddress();
		int readBytes = channel.read(buffer);
		if (readBytes < 0) {
			channel.close();
			return;
		} else if (readBytes == 0) {
			return;
		}

		buffer.flip();

		StringBuilder sb = new StringBuilder();
		while (buffer.hasRemaining()) {
			sb.append((char) buffer.get());
		}

		buffer.clear();
		User user = getUserByAddress(client);

		if (key.isValid()) {
			String command = sb
					.toString()
					.replace("\n", "")
					.replace("\r", "");

			if ("--help".equals(command)) {
				sendMessage(LS_COMMAND, selector, client);
				sendMessage(MKDIR_COMMAND, selector, client);
				sendMessage(CHANGE_NICKNAME, selector, client);
				sendMessage(TOUCH_COMMAND, selector, client);
				sendMessage(CD_COMMAND, selector, client);
				sendMessage(RM_COMMAND, selector, client);
				sendMessage(COPY_COMMAND, selector, client);
				sendMessage(CAT_COMMAND, selector, client);
			} else if ("ls".equals(command)) {
				sendMessage(getFileList(user).concat("\r\n"), selector, client);
			} else if ("exit".equals(command)) {
				System.out.println("Client logged out. IP: " + channel.getRemoteAddress());
				channel.close();
				users.remove(user);
				return;
			} else if(command.startsWith("mkdir ")) {
				String[] comWithArgs = command.split(" ", 2);
				if(comWithArgs.length == 2) {
					handleMakeDirCommand(comWithArgs[1], selector, user);
				} else {
					sendMessage(INCORRECT_COMMAND, selector, client);
				}
			} else if(command.startsWith("nick ")) {
				String[] comWithArgs = command.split(" ");
				if(comWithArgs.length == 2) {
					user.setNick(comWithArgs[1]);
				} else {
					sendMessage(INCORRECT_COMMAND, selector, client);
				}
			} else if(command.startsWith("touch ")) {
				String[] comWithArgs = command.split(" ", 2);
				if(comWithArgs.length == 2) {
					handleTouchFile(comWithArgs[1], selector, user);
				} else {
					sendMessage(INCORRECT_COMMAND, selector, client);
				}
			} else if(command.startsWith("cd ")) {
				String[] comWithArgs = command.split(" ", 2);
				if(comWithArgs.length == 2) {
					handleChangeDirCommand(comWithArgs[1], selector, user);
				} else {
					sendMessage(INCORRECT_COMMAND, selector, client);
				}
			} else if(command.startsWith("rm ")) {
				String[] comWithArgs = command.split(" ", 2);
				if(comWithArgs.length == 2) {
					handleRemoveCommand(comWithArgs[1], selector, user);
				} else {
					sendMessage(INCORRECT_COMMAND, selector, client);
				}
			} else if(command.startsWith("copy ")) {
				String[] comWithArgs = command.split(" ");
				if(comWithArgs.length == 3) {
					handleCopyCommand(comWithArgs[1], comWithArgs[2], selector, user);
				} else {
					sendMessage(INCORRECT_COMMAND, selector, client);
				}
			} else if(command.startsWith("cat ")) {
				String[] comWithArgs = command.split(" ");
				if(comWithArgs.length == 2) {
					handleCatCommand(comWithArgs[1], channel, user);
				} else {
					sendMessage(INCORRECT_COMMAND, selector, client);
				}
			}
			sendMessage(user.getPrompt(), selector, client);
		}
	}

	private User getUserByAddress(SocketAddress address) {
		for(User user : users) {
			if(user.getAddress().equals(address)) {
				return user;
			}
		}
		return null;
	}

	private void handleMakeDirCommand(String dirName, Selector selector, User user) throws IOException {
		Path newDir = user.getCurrentDir().resolve(Paths.get(dirName));
		if(!Files.exists(newDir)) {
			Files.createDirectory(newDir);
		} else {
			sendMessage(dirName + " already exist\r\n", selector, user.getAddress());
		}
	}

	private void handleTouchFile(String fileName, Selector selector, User user) throws IOException {
		Path newFile = user.getCurrentDir().resolve(Paths.get(fileName));
		if(!Files.exists(newFile)) {
			Files.createFile(newFile);
		} else {
			sendMessage(fileName + " is already exist\r\n", selector, user.getAddress());
		}
	}

	private void handleChangeDirCommand(String path, Selector selector, User user) throws IOException {
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
				sendMessage(path + " is not a directory or not exist\r\n", selector, user.getAddress());
			}
		}
	}

	private void handleRemoveCommand(String strPath, Selector selector, User user) throws IOException {
		Path filePath = user.getCurrentDir().resolve(Paths.get(strPath));
		if(!Files.exists(filePath)) {
			sendMessage(strPath + " is not exist\r\n", selector, user.getAddress());
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

	private void handleCopyCommand(String srcStrPath, String targetStrPath, Selector selector, User user) throws IOException {
		Path srcPath = user.getCurrentDir().resolve(Paths.get(srcStrPath));
		Path targetPath = user.getCurrentDir().resolve(Paths.get(targetStrPath));
		if(!Files.exists(srcPath)) {
			sendMessage(srcStrPath + " is not exist\r\n", selector, user.getAddress());
			return;
		}
		if(Files.isDirectory(srcPath) && Files.exists(targetPath) && !Files.isDirectory(targetPath)) {
			sendMessage("Can't copy directory to not directory\r\n", selector, user.getAddress());
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

	private void handleCatCommand(String filename, SocketChannel channel, User user) throws IOException {
		Path file = user.getCurrentDir().resolve(Paths.get(filename));
		if(!Files.exists(file)) {
			channel.write(ByteBuffer.wrap((filename + " is not exist").getBytes(StandardCharsets.UTF_8)));
		}
		if(!Files.isDirectory(file)) {
			FileInputStream fis = new FileInputStream(file.toFile());
			FileChannel fileChannel = fis.getChannel();
			ByteBuffer buf = ByteBuffer.allocate(8 * 1024);
			while (fileChannel.read(buf) != -1) {
				buf.flip();
				channel.write(buf);
				buf.clear();
			}
			fileChannel.close();
			channel.write(ByteBuffer.wrap("\r\n".getBytes(StandardCharsets.UTF_8)));
		} else {
			channel.write(ByteBuffer.wrap((filename + " is a directory\r\n").getBytes(StandardCharsets.UTF_8)));
		}
	}

	private String getFileList(User user) {
		return String.join(" ", user.getCurrentDir().toFile().list());
	}

	private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
		for (SelectionKey key : selector.keys()) {
			if (key.isValid() && key.channel() instanceof SocketChannel) {
				if (((SocketChannel)key.channel()).getRemoteAddress().equals(client)) {
					((SocketChannel)key.channel())
							.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
				}
			}
		}
	}

	private void handleAccept(SelectionKey key, Selector selector) throws IOException {
		SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
		channel.configureBlocking(false);
		System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
		User user = new User(channel.getRemoteAddress());
		users.add(user);
		channel.register(selector, SelectionKey.OP_READ, "some attach");
		channel.write(ByteBuffer.wrap("Hello user!\r\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap("Enter --help for support info\r\n".getBytes(StandardCharsets.UTF_8)));
		channel.write(ByteBuffer.wrap(user.getPrompt().getBytes(StandardCharsets.UTF_8)));
	}

	public static void main(String[] args) throws IOException {
		new NioTelnetServer();
	}
}
