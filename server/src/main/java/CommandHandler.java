import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;

@Slf4j
public class CommandHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.info("Получена команда: " + msg.toString().substring(0, msg.toString().indexOf("@")));
        if (msg instanceof UpdateMessage) {
            UpdateMessage updateMessage = (UpdateMessage) msg;
            String receivedLogin = updateMessage.getLogin();
            ctx.writeAndFlush(new UpdateMessage(getContentsOfCloudStorage(receivedLogin)));
        } else if (msg instanceof DeletionMessage) {
            DeletionMessage deletionMessage = (DeletionMessage) msg;
            for (int i = 0; i < deletionMessage.getFilesToDelete().size(); i++) {
                File fileToDelete = new File(deletionMessage.getFilesToDelete().get(i).getAbsolutePath());
                if (fileToDelete.isDirectory()) {
                    CommandHandler.deleteRecursively(fileToDelete);
                } else {
                    fileToDelete.delete();
                }
            }
            deletionMessage.getFilesToDelete().clear();
            if (deletionMessage.getFilesToDelete().isEmpty()) {
                ctx.writeAndFlush(new UpdateMessage(getContentsOfCloudStorage(deletionMessage.getLogin())));
            } else {
                ctx.writeAndFlush("DeletionFailure");
            }
        } else if (msg instanceof FileRequest) {
            FileRequest fileRequest = (FileRequest) msg;
            for (int i = 0; i < fileRequest.getFilesToRequest().size(); i++) {
                File file = new File(fileRequest.getFilesToRequest().get(i).getAbsolutePath());
                Path fileToRequest = Paths.get(fileRequest.getFilesToRequest().get(i).getAbsolutePath());
                try {
                    if (file.isDirectory()) {
                        if (file.listFiles().length == 0) {
                            ctx.writeAndFlush(new FileMessage(file.getName(), true, true));
                        } else {
                            ctx.writeAndFlush(new FileMessage(file.getName(), true, false));
                        }
                    } else {
                        try {
                            ctx.writeAndFlush(new FileMessage(fileToRequest));
                        } catch (AccessDeniedException e) {
                            log.error("Error: ", e);
                        }
                    }
                } catch (IOException e) {
                    log.error("Error: ", e);
                }
            }
        } else if (msg instanceof FileMessage) {
            FileMessage fileMessage = (FileMessage) msg;
            Path pathToNewFile = Paths.get("server/serverDirectory/" + fileMessage.getLogin() + File.separator + fileMessage.getFileName());
            if (fileMessage.isDirectory() && fileMessage.isEmpty()) {
                if (Files.exists(pathToNewFile)) {
                    log.info("Директория с именем " + pathToNewFile.getFileName() + " уже существует");
                } else {
                    Files.createDirectory(pathToNewFile);
                }
            } else {
                if (Files.exists(pathToNewFile)) {
                    log.info("Файл с именем " + pathToNewFile.getFileName() + " уже существует");
                } else {
                    Files.write(Paths.get("server/serverDirectory/" + fileMessage.getLogin() + File.separator + fileMessage.getFileName()), fileMessage.getData(), StandardOpenOption.CREATE);
                }
            }
            ctx.writeAndFlush(new UpdateMessage(getContentsOfCloudStorage(fileMessage.getLogin())));
        } else if (msg instanceof AuthMessage) {
            AuthMessage authMessage = (AuthMessage) msg;
            DBaseHandler.getConnectionWithDB();
            if (DBaseHandler.checkUserExists(authMessage.getLogin())) {
                if (DBaseHandler.checkPassword(authMessage.getLogin(), authMessage.getPassword())) {
                    ctx.writeAndFlush("userIsValid/" + authMessage.getLogin());
                } else {
                    ctx.writeAndFlush("wrongPassword");
                }
            } else {
                ctx.writeAndFlush("userDoesNotExist");
            }
            DBaseHandler.disconnectDB();
        } else if (msg instanceof RegistrationMessage) {
            RegistrationMessage registrationMessage = (RegistrationMessage) msg;
            DBaseHandler.getConnectionWithDB();
            if (DBaseHandler.checkUserExists(registrationMessage.getLogin())) {
                ctx.writeAndFlush("userAlreadyExists");
            } else {
                if (DBaseHandler.register(registrationMessage.getLogin(), registrationMessage.getPassword())) {
                    File newDirectory = new File("server/serverDirectory/" + registrationMessage.getLogin());
                    newDirectory.mkdir();
                    ctx.writeAndFlush("registrationIsSuccessful");
                }
            }
            DBaseHandler.disconnectDB();
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        DBaseHandler.disconnectDB();
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        DBaseHandler.disconnectDB();
        ctx.close();
    }

    public static void deleteRecursively(File f) throws Exception {
        try {
            if (f.isDirectory()) {
                for (File c : f.listFiles()) {
                    deleteRecursively(c);
                }
            }
            if (!f.delete()) {
                throw new Exception("Delete command returned false for file: " + f);
            }
        } catch (Exception e) {
            throw new Exception("Failed to delete the folder: " + f, e);
        }
    }

    public static HashMap<Integer, LinkedList<File>> getContentsOfCloudStorage(String login) {
        HashMap<Integer, LinkedList<File>> cloudStorageContents;
        File path = new File("server/serverDirectory/" + login);
        File[] files = path.listFiles();
        cloudStorageContents = new HashMap<>();
        if (files.length == 0) {
        } else {
            LinkedList<File> listCloudStorageFiles = new LinkedList<>(Arrays.asList(files));
            cloudStorageContents.put(0, listCloudStorageFiles);
        }
        return cloudStorageContents;
    }
}