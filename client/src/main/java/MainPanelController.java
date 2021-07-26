import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.input.*;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

@Slf4j
public class MainPanelController implements Initializable {
    private int localStorageFolderLevelCounter = 0;
    private int cloudStorageFolderLevelCounter = 0;
    private HashMap<Integer, LinkedList<File>> folderCloudStorageListViews;
    private LinkedList<File> pathsToCloudStorageFiles;
    private String watchableDirectory = "client" + File.separator + "localDirectory";
    private String currentDirectoryName = "";
    AudioClip soundOfFolderOpening = new AudioClip(this.getClass().getResource("foldersound.mp3").toExternalForm());

    @FXML
    ListView<StorageItem> listOfLocalElements;
    @FXML
    Button localStorageUpdate;
    @FXML
    VBox firstBlockMainPanel;
    @FXML
    ListView<StorageItem> listOfCloudStorageElements;
    @FXML
    ChoiceBox menu;
    @FXML
    Button cloudStorageUpdate;
    @FXML
    Button goToPreviousFolderInLocalStorageButton;
    @FXML
    Button goToPreviousFolderInCloudStorageButton;
    @FXML
    Button localStorageDelete;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ConnectionServer.startConnection();
        initializeListOfLocalStorageItems();
        mainPanelServerListener.setDaemon(true);
        mainPanelServerListener.start();
        deleteWithDeleteKey();
        updateCloudStoragePanel();
    }

    Thread mainPanelServerListener = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    Object object = null;
                    object = ConnectionServer.readIncomingObject();
                    if (object instanceof UpdateMessage) {
                        UpdateMessage message = (UpdateMessage) object;
                        folderCloudStorageListViews = new HashMap<>();
                        folderCloudStorageListViews.putAll(message.getCloudStorageContents());
                        Platform.runLater(() -> initializeListOfCloudStorageItems(folderCloudStorageListViews));
                    } else if (object.toString().equals("DeletionSuccess")) {
                        Platform.runLater(() -> initializeListOfCloudStorageItems(folderCloudStorageListViews));
                    } else if (object instanceof FileMessage) {
                        FileMessage fileMessage = (FileMessage) object;
                        if (fileMessage.isDirectory() && fileMessage.isEmpty()) {
                            Path pathToNewEmptyDirectory = Paths.get("client" + File.separator + "localDirectory" + File.separator + "" + fileMessage.getFileName());
                            if (Files.exists(pathToNewEmptyDirectory)) {
                                Alert alert = new Alert(Alert.AlertType.ERROR, "Директория уже существует!", ButtonType.OK);
                                alert.showAndWait();
                            } else {
                                Platform.runLater(() -> {
                                    try {
                                        Files.createDirectory(pathToNewEmptyDirectory);
                                    } catch (IOException e) {
                                        log.error("Error: ", e);
                                    }
                                });
                            }
                        } else {
                            try {
                                Files.write(Paths.get("client" + File.separator + "localDirectory" + File.separator + "" + fileMessage.getFileName()), fileMessage.getData(), StandardOpenOption.CREATE);
                            } catch (NullPointerException e) {
                                log.error("Error: ", e);
                            }
                        }
                        Platform.runLater(() -> initializeListOfLocalStorageItems());
                    } else if (object.toString().equals("succes")) {
                        log.info("Успешно");
                    }
                }
            } catch (Exception e) {
                log.error("Error: ", e);
            }
        }
    });

    public void initializeListOfLocalStorageItems() {
        ObservableList<StorageItem> listOfLocalItems = FXCollections.observableArrayList();
        File pathToLocalStorage = new File(watchableDirectory);
        File[] listOfLocalStorageFiles = pathToLocalStorage.listFiles();
        if (listOfLocalStorageFiles.length == 0 && localStorageFolderLevelCounter == 0) {
            listOfLocalElements.setItems(listOfLocalItems);
            listOfLocalElements.setCellFactory(param -> new StorageListViewItem());
        } else if (listOfLocalStorageFiles.length > 0) {
            for (int i = 0; i < listOfLocalStorageFiles.length; i++) {
                long initialSizeOfLocalFileOrDirectory = 0;
                String nameOfLocalFileOrDirectory = listOfLocalStorageFiles[i].getName();
                if (listOfLocalStorageFiles[i].isDirectory()) {
                    try {
                        initialSizeOfLocalFileOrDirectory = getActualSizeOfFolder(listOfLocalStorageFiles[i]);
                    } catch (Exception e) {
                        log.error("Error: ", e);
                    }
                } else {
                    initialSizeOfLocalFileOrDirectory = listOfLocalStorageFiles[i].length();
                }
                String dateOfLastModification = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(listOfLocalStorageFiles[i].lastModified()));
                File pathToFileInLocalStorage = new File(listOfLocalStorageFiles[i].getAbsolutePath());
                listOfLocalItems.addAll(new StorageItem(nameOfLocalFileOrDirectory, initialSizeOfLocalFileOrDirectory, false, dateOfLastModification, pathToFileInLocalStorage));
            }
            listOfLocalElements.setItems(listOfLocalItems);
            listOfLocalElements.setCellFactory(param -> new StorageListViewItem());
        } else {
            listOfLocalElements.setItems(listOfLocalItems);
            listOfLocalElements.setCellFactory(param -> new StorageListViewItem());
        }
    }

    public void initializeListOfCloudStorageItems(HashMap<Integer, LinkedList<File>> listOfCloudStorageFiles) {
        if (cloudStorageFolderLevelCounter > 0) {
            cloudStorageFolderLevelCounter = 0;
            goToPreviousFolderInCloudStorageButton.setVisible(false);
        }
        try {
            ObservableList<StorageItem> listOfCloudItems = FXCollections.observableArrayList();
            if (!listOfCloudStorageFiles.isEmpty()) {
                for (int i = 0; i < listOfCloudStorageFiles.get(0).size(); i++) {
                    long initialSizeOfCloudFileOrDir = 0;
                    String nameOfCloudFileOrDir = listOfCloudStorageFiles.get(0).get(i).getName();
                    if (listOfCloudStorageFiles.get(0).get(i).isDirectory()) {
                        try {
                            initialSizeOfCloudFileOrDir = getActualSizeOfFolder(listOfCloudStorageFiles.get(0).get(i));
                        } catch (Exception e) {
                            log.error("Error: ", e);
                        }
                    } else {
                        initialSizeOfCloudFileOrDir = listOfCloudStorageFiles.get(0).get(i).length();
                    }
                    String dateOfLastModification = new java.text.SimpleDateFormat("dd.MM.yyy HH:mm:ss").format(new Date(listOfCloudStorageFiles.get(0)
                            .get(i).lastModified()));
                    File pathOfFileInCloudStorage = new File(listOfCloudStorageFiles.get(0).get(i).getAbsolutePath());
                    listOfCloudItems.addAll(new StorageItem(nameOfCloudFileOrDir, initialSizeOfCloudFileOrDir, false, dateOfLastModification, pathOfFileInCloudStorage));
                }
                listOfCloudStorageElements.setItems(listOfCloudItems);
                listOfCloudStorageElements.setCellFactory(param -> new StorageListViewItem());
            } else {
                listOfCloudStorageElements.setItems(listOfCloudItems);
                listOfCloudStorageElements.setCellFactory(param -> new StorageListViewItem());
            }
        } catch (NullPointerException e) {
            log.error("Error: ", e);
        }
    }

    public void openDirectoryOrFile(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 1 && mouseEvent.getButton() == MouseButton.PRIMARY) {
            listOfLocalElements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        } else if (mouseEvent.getClickCount() == 2 && mouseEvent.getButton() == MouseButton.PRIMARY) {
            listOfLocalElements.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            if (listOfLocalElements.getSelectionModel().getSelectedItems().size() == 1) {
                File pathToClickedFile;
                pathToClickedFile = listOfLocalElements.getSelectionModel().getSelectedItem().getPathToFile();
                if (pathToClickedFile.isDirectory()) {
                    File[] nextDirectory = pathToClickedFile.listFiles();
                    if (nextDirectory.length == 0) {
                    } else if (nextDirectory.length != 0) {
                        playSoundOfFolderOpening();
                        localStorageFolderLevelCounter++;
                        if (localStorageFolderLevelCounter > 0) {
                            goToPreviousFolderInLocalStorageButton.setVisible(true);
                        }
                        if (localStorageFolderLevelCounter > 0 && nextDirectory.length != 0) {
                            watchableDirectory += File.separator + pathToClickedFile.getName();
                            currentDirectoryName = pathToClickedFile.getName();
                        } else {
                            currentDirectoryName = "client/localDirectory";
                        }
                        ObservableList<StorageItem> listOfLocalItems = FXCollections.observableArrayList();
                        for (int i = 0; i < nextDirectory.length; i++) {
                            String nameOfLocalFileOrDirectory = nextDirectory[i].getName();
                            long initialSizeOfLocalFileOrDirectory = 0;
                            try {
                                if (nextDirectory[i].isDirectory()) {
                                    initialSizeOfLocalFileOrDirectory = getActualSizeOfFolder(nextDirectory[i]);
                                } else {
                                    initialSizeOfLocalFileOrDirectory = nextDirectory[i].length();
                                }
                            } catch (Exception e) {
                                log.error("Error: ", e);
                            }
                            String dateOfLastModification = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                                    .format(new Date(nextDirectory[i].lastModified()));
                            File pathOfFileInLocalStorage = new File(nextDirectory[i].getAbsolutePath());
                            listOfLocalItems.addAll(new StorageItem(nameOfLocalFileOrDirectory, initialSizeOfLocalFileOrDirectory, false, dateOfLastModification, pathOfFileInLocalStorage));
                            listOfLocalElements.setItems(listOfLocalItems);
                            listOfLocalElements.setCellFactory(param -> new StorageListViewItem());
                        }

                    }
                } else {
                    Desktop desktop = null;
                    if (desktop.isDesktopSupported()) {
                        desktop = desktop.getDesktop();
                        try {
                            desktop.open(pathToClickedFile);
                        } catch (IOException e) {
                            log.error("Error: ", e);
                            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Ошибка обмена", ButtonType.OK);
                            alert.showAndWait();
                        }
                    }
                }
            }
        }
    }

    public void goToPreviousDirectoryInLocalStorage() {
        playSoundOfFolderOpening();
        ObservableList<StorageItem> listOfLocalItems = FXCollections.observableArrayList();
        LinkedList<File> files = new LinkedList<>();
        File file = new File(watchableDirectory);
        File previousDirectory = new File(file.getParent());
        File[] contentsOfPreviousDirectory = previousDirectory.listFiles();
        for (int i = 0; i < contentsOfPreviousDirectory.length; i++) {
            files.add((contentsOfPreviousDirectory[i]));
        }
        for (int i = 0; i < files.size(); i++) {
            String nameOfLocalFileOrDirectory = files.get(i).getName();
            long initialSizeOfLocalFileOrDirectory = 0;
            try {
                if (files.get(i).isDirectory()) {
                    initialSizeOfLocalFileOrDirectory = getActualSizeOfFolder(files.get(i));
                } else {
                    initialSizeOfLocalFileOrDirectory = files.get(i).length();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            String dateOfLastModification = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(new Date(files.get(i).lastModified()));
            File pathOfFileInLocalStorage = files.get(i).getAbsoluteFile();
            listOfLocalItems.addAll(new StorageItem(nameOfLocalFileOrDirectory, initialSizeOfLocalFileOrDirectory, false, dateOfLastModification, pathOfFileInLocalStorage
            ));
        }
        listOfLocalElements.setItems(listOfLocalItems);
        listOfLocalElements.setCellFactory(param -> new StorageListViewItem());
        localStorageFolderLevelCounter--;
        if (localStorageFolderLevelCounter <= 0) {
            goToPreviousFolderInLocalStorageButton.setVisible(false);
            watchableDirectory = "client" + File.separator + "localDirectory";
            currentDirectoryName = "localDirectory";
        } else {
            watchableDirectory = previousDirectory.toString();
            currentDirectoryName = previousDirectory.getName();
        }
    }

    public void goToNextDirectoryInCloudStorageOnDoubleClick(MouseEvent mouseEvent) {
        pathsToCloudStorageFiles = new LinkedList<>();
        if (mouseEvent.getClickCount() == 1) {
            listOfCloudStorageElements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        } else if (mouseEvent.getClickCount() == 2 && mouseEvent.getButton() == MouseButton.PRIMARY) {
            listOfCloudStorageElements.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            if (listOfCloudStorageElements.getSelectionModel().getSelectedItems().size() == 1) {
                File pathToClickedFile = new File("");
                for (int i = 0; i < folderCloudStorageListViews.get(cloudStorageFolderLevelCounter).size(); i++) {
                    File file = folderCloudStorageListViews.get(cloudStorageFolderLevelCounter).get(i);
                    if (listOfCloudStorageElements.getSelectionModel().getSelectedItem().getName().equals(file.getName())) {
                        pathToClickedFile = folderCloudStorageListViews.get(cloudStorageFolderLevelCounter).get(i);
                    }
                }
                if (pathToClickedFile.isDirectory()) {
                    File[] nextDirectory = pathToClickedFile.listFiles();
                    if (nextDirectory.length != 0) {
                        for (int i = 0; i < nextDirectory.length; i++) {
                            try {
                                pathsToCloudStorageFiles.add(nextDirectory[i]);
                            } catch (IndexOutOfBoundsException e) {
                                log.error("Error: ", e);
                            }
                        }
                        playSoundOfFolderOpening();
                        cloudStorageFolderLevelCounter++;
                        folderCloudStorageListViews.put(cloudStorageFolderLevelCounter, pathsToCloudStorageFiles);
                        ObservableList<StorageItem> listOfCloudItems = FXCollections.observableArrayList();
                        for (int i = 0; i < nextDirectory.length; i++) {
                            String nameOfCloudStorageFileOrDirectory = nextDirectory[i].getName();
                            long initialSizeOfLocalStorageFileOrDirectory = nextDirectory[i].length();
                            String dateOfLastModification = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                                    .format(new Date(nextDirectory[i].lastModified()));
                            File pathToFileInLocalStorage = new File(nextDirectory[i].getAbsolutePath());
                            listOfCloudItems.addAll(new StorageItem(nameOfCloudStorageFileOrDirectory, initialSizeOfLocalStorageFileOrDirectory, false, dateOfLastModification, pathToFileInLocalStorage));
                            listOfCloudStorageElements.setItems(listOfCloudItems);
                            listOfCloudStorageElements.setCellFactory(param -> new StorageListViewItem());
                        }
                    }
                    if (cloudStorageFolderLevelCounter > 0) {
                        goToPreviousFolderInCloudStorageButton.setVisible(true);
                    }
                }
            }
        }
    }

    public void goToPreviousDirectoryInCloudStorage(ActionEvent event) {
        ObservableList<StorageItem> listOfCloudItems = FXCollections.observableArrayList();
        LinkedList<File> files = new LinkedList<>();
        for (int i = 0; i < folderCloudStorageListViews.get(cloudStorageFolderLevelCounter - 1).size(); i++) {
            files.add((folderCloudStorageListViews.get(cloudStorageFolderLevelCounter - 1).get(i)));
        }
        for (int i = 0; i < files.size(); i++) {
            String nameOfLocalFileOrDirectory = files.get(i).getName();
            long initialSizeOfLocalFileOrDirectory = files.get(i).length();
            String dateOfLastModification = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                    .format(new Date(files.get(i).lastModified()));
            File pathToFileInCloudStorage = new File(files.get(i).getAbsolutePath());
            listOfCloudItems.addAll(new StorageItem(nameOfLocalFileOrDirectory, initialSizeOfLocalFileOrDirectory, false, dateOfLastModification, pathToFileInCloudStorage));
        }
        listOfCloudStorageElements.setItems(listOfCloudItems);
        listOfCloudStorageElements.setCellFactory(param -> new StorageListViewItem());
        folderCloudStorageListViews.remove(cloudStorageFolderLevelCounter);
        cloudStorageFolderLevelCounter--;
        if (cloudStorageFolderLevelCounter <= 0) {
            goToPreviousFolderInCloudStorageButton.setVisible(false);
        }
        playSoundOfFolderOpening();
    }

    public LinkedList getPathsOfSelectedFilesInLocalStorage() {
        try {
            listOfLocalElements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            LinkedList<File> listOfSelectedElementsInLocalStorage = new LinkedList<>();
            if (listOfLocalElements.getSelectionModel().getSelectedItems().size() != 0) {
                for (int i = 0; i < listOfLocalElements.getSelectionModel().getSelectedItems().size(); i++) {
                    listOfSelectedElementsInLocalStorage.add(listOfLocalElements.getSelectionModel().getSelectedItems().get(i).getPathToFile());
                }
                return listOfSelectedElementsInLocalStorage;
            }
        } catch (NullPointerException e) {
            log.error("Error: ", e);
        }
        return null;
    }

    public LinkedList getPathsOfSelectedFilesInCloudStorage() {
        listOfCloudStorageElements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        LinkedList<File> listOfSelectedElementsInCloudStorage = new LinkedList<File>();
        if (listOfCloudStorageElements.getSelectionModel().getSelectedItems().size() != 0) {
            for (int i = 0; i < listOfCloudStorageElements.getSelectionModel().getSelectedItems().size(); i++) {
                listOfSelectedElementsInCloudStorage.add(listOfCloudStorageElements.getSelectionModel().getSelectedItems().get(i).getPathToFile());
            }
        }
        return listOfSelectedElementsInCloudStorage;
    }

    public void sendDeletionMessageToServer() {
        ConnectionServer.sendDeletionMessage(CurrentLogin.getCurrentLogin(), getPathsOfSelectedFilesInCloudStorage());
    }

    public void deleteFileInLocalDirectory() {
        listOfCloudStorageElements.getSelectionModel().clearSelection();
        for (int i = 0; i < getPathsOfSelectedFilesInLocalStorage().size(); i++) {
            String absolutePath = getPathsOfSelectedFilesInLocalStorage().get(i).toString();
            Path path = Paths.get(absolutePath);
            File file = new File(getPathsOfSelectedFilesInLocalStorage().get(i).toString());
            try {
                if (file.isDirectory()) {
                    deleteContentsOfFolderRecursively(file);
                } else {
                    Files.delete(path);
                }
            } catch (Exception e) {
                log.error("Error: ", e);
            }
        }
        initializeListOfLocalStorageItems();
    }

    public void deleteWithDeleteKey() {
        listOfLocalElements.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                deleteFileInLocalDirectory();
            }
        });
        listOfCloudStorageElements.setOnKeyPressed(event -> {
            listOfLocalElements.getSelectionModel().clearSelection();
            if (event.getCode() == KeyCode.DELETE) {
                sendDeletionMessageToServer();
            }
        });
    }

    public void selectAllFilesFromCloudStorage() {
        if (listOfCloudStorageElements.getItems().size() == listOfCloudStorageElements.getSelectionModel().getSelectedItems().size()) {
            listOfCloudStorageElements.getSelectionModel().clearSelection();
        } else {
            listOfCloudStorageElements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listOfCloudStorageElements.getSelectionModel().selectAll();
        }
    }

    public void selectAllFilesFromLocalStorage() {
        if (listOfLocalElements.getItems().size() == listOfLocalElements.getSelectionModel().getSelectedItems().size()) {
            listOfLocalElements.getSelectionModel().clearSelection();
        } else {
            listOfLocalElements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listOfLocalElements.getSelectionModel().selectAll();
        }
    }

    public void updateCloudStoragePanel() {
        ConnectionServer.sendUpdateMessageToServer(CurrentLogin.getCurrentLogin());
    }

    public static void deleteContentsOfFolderRecursively(File file) {
        try {
            if (file.isDirectory()) {
                for (File c : file.listFiles()) {
                    deleteContentsOfFolderRecursively(c);
                }
            }
            if (!file.delete()) {
                log.error("Delete command returned false for file: " + file);
            }
        } catch (Exception e) {
            log.error("Failed to delete the folder: " + file, e);
        }
    }

    public static long getActualSizeOfFolder(File file) {
        long actualSizeOfFolder = 0;
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                if (f.isFile()) {
                    actualSizeOfFolder += f.length();
                } else if (f.isDirectory()) {
                    actualSizeOfFolder += getActualSizeOfFolder(f);
                }
            }
        }
        return actualSizeOfFolder;
    }

    public void downloadFilesIntoLocalStorage() {
        ConnectionServer.sendFileRequest(getPathsOfSelectedFilesInCloudStorage());
    }

    public void transferFilesToCloudStorage() {
        ConnectionServer.transferFilesToCloudStorage(CurrentLogin.getCurrentLogin(), getPathsOfSelectedFilesInLocalStorage());
    }

    public void goToOpeningPanelToChangeProfileOrLeaveApp() {
        if (menu.getSelectionModel().getSelectedItem().toString().equals("Сменить пользователя")) {
            try {
                Stage stage;
                Parent root;
                stage = (Stage) menu.getScene().getWindow();
                root = FXMLLoader.load(getClass().getResource("/LoginPanel.fxml"));
                Scene scene = new Scene(root, 400, 400);
                stage.setScene(scene);
                stage.setResizable(false);
                stage.setTitle("Облако JJ");
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (menu.getSelectionModel().getSelectedItem().toString().equals("Выход")) {
            Stage stage;
            stage = (Stage) menu.getScene().getWindow();
            stage.close();
        }
        menu.setValue(null);
    }

    public void playSoundOfFolderOpening() {
        soundOfFolderOpening.setVolume(0.2);
        soundOfFolderOpening.play();
    }
}