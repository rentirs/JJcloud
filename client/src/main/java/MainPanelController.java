import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.media.AudioClip;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class MainPanelController implements Initializable {
    private int cloudStorageFolderLevelCounter = 0;
    private HashMap<Integer, LinkedList<File>> folderCloudStorageListViews;

    AudioClip soundOfFolderOpening = new AudioClip(Objects.requireNonNull(this.getClass().getResource("foldersound.mp3")).toExternalForm());

    @FXML
    Button localStorageUpdate;
    @FXML
    VBox firstBlockMainPanel;
    @FXML
    ListView<StorageItem> listOfCloudStorageElements;
    @FXML
    Button cloudStorageUpdate;
    @FXML
    Button upButtonLocal;
    @FXML
    Button upButtonServer;
    @FXML
    ComboBox<String> disksBox;
    @FXML
    TableView<FileInfo> localFilesTable;
    @FXML
    TextField pathFieldLocal;
    @FXML
    VBox main;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ConnectionServer.startConnection();
        localPanel();
        mainPanelServerListener.setDaemon(true);
        mainPanelServerListener.start();
        isDeleteKey();
        updateServer();
    }

    private void localPanel() {

        TableColumn<FileInfo, String> fileTypeColumn = new TableColumn<>();
        fileTypeColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getType().getName()));
        fileTypeColumn.setPrefWidth(20);

        TableColumn<FileInfo, String> filenameColumn = new TableColumn<>("Имя");
        filenameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFileName()));
        filenameColumn.setPrefWidth(286);
        TableColumn<FileInfo, Long> fileSizeColumn = new TableColumn<>("Размер");
        fileSizeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
        fileSizeColumn.setCellFactory(column -> new TableCell<FileInfo, Long>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    String text = item.toString();
                    if (item / 1073741824 > 0) {
                        text = String.format("%.2f", item / 1073741824D) + " GB";
                    } else if (item / 1048576 > 0) {
                        text = String.format("%.2f", item / 1048576D) + " MB";
                    } else if (item / 1024 > 0) {
                        text = String.format("%.2f", item / 1024D) + " KB";
                    } else if (item / 1024 <= 0) {
                        text = item + " bytes";
                    }
                    if (item == -1L) {
                        text = "[DIR]";
                    }
                    setText(text);
                }
            }
        });
        fileSizeColumn.setPrefWidth(60);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        TableColumn<FileInfo, String> fileDateColumn = new TableColumn<>("Дата изменения");
        fileDateColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getLastModified().format(dtf)));
        fileDateColumn.setPrefWidth(120);

        localFilesTable.getColumns().addAll(fileTypeColumn, filenameColumn, fileSizeColumn, fileDateColumn);
        localFilesTable.getSortOrder().add(fileTypeColumn);

        disksBox.getItems().clear();
        for (Path p : FileSystems.getDefault().getRootDirectories()) {
            disksBox.getItems().add(p.toString());
        }
        disksBox.getSelectionModel().select(0);

        localFilesTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                localFilesTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
                openLocalFileOrDirectory();
            }
            if (event.getClickCount() == 1) {
                localFilesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            }
        });

        localFilesTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                openLocalFileOrDirectory();
            }
        });

        updateLocal(Paths.get(disksBox.getSelectionModel().getSelectedItem()));
    }

    private void updateLocal(Path path) {
        try {
            pathFieldLocal.setText(path.normalize().toAbsolutePath().toString());
            localFilesTable.getItems().clear();
            localFilesTable.getItems().addAll(Files.list(path).map(FileInfo::new).collect(Collectors.toList()));
            localFilesTable.sort();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось обновить список файлов", ButtonType.OK);
            alert.showAndWait();
        }
    }

    public void reloadLocal() {
        updateLocal(Paths.get(pathFieldLocal.getText()));
    }

    public void upLocal() {
        playSoundOfFolderOpening();
        Path upperPath = Paths.get(pathFieldLocal.getText()).getParent();
        if (upperPath != null) {
            updateLocal(upperPath);
        }
    }

    private void openLocalFileOrDirectory() {
        playSoundOfFolderOpening();
        Path path = Paths.get(pathFieldLocal.getText()).resolve(localFilesTable.getSelectionModel().getSelectedItem().getFileName());
        if (Files.isDirectory(path)) {
            updateLocal(path);
        } else {
            Desktop desktop = Desktop.getDesktop();
            try {
                desktop.open(path.toFile());
            } catch (IOException e) {
                e.printStackTrace();
                Alert alert = new Alert(Alert.AlertType.ERROR, "IO error", ButtonType.OK);
                alert.showAndWait();
            }
        }
    }

    public void deleteLocalFile() {
        for (int i = 0; i < getPathsSelectedLocal().size(); i++) {
            String absolutePath = getPathsSelectedLocal().get(i).toString();
            Path path = Paths.get(absolutePath);
            File file = new File(getPathsSelectedLocal().get(i).toString());
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
        reloadLocal();
    }

    public static void deleteContentsOfFolderRecursively(File file) {
        try {
            if (file.isDirectory()) {
                for (File c : Objects.requireNonNull(file.listFiles())) {
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

    public void selectAllLocal() {
        localFilesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        localFilesTable.getSelectionModel().selectAll();
        localFilesTable.requestFocus();
    }

    public LinkedList<File> getPathsSelectedLocal() {
        try {
            localFilesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            LinkedList<File> pathsSelectedLocal = new LinkedList<>();
            if (localFilesTable.getSelectionModel().getSelectedItems().size() != 0) {
                for (int i = 0; i < localFilesTable.getSelectionModel().getSelectedItems().size(); i++) {
                    pathsSelectedLocal.add(localFilesTable.getSelectionModel().getSelectedItems().get(i).getPathToFile());
                }
                return pathsSelectedLocal;
            }
        } catch (NullPointerException e) {
            log.error("Error: ", e);
        }
        return null;
    }

    public void selectDisk(ActionEvent actionEvent) {
        ComboBox<String> element = (ComboBox<String>) actionEvent.getSource();
        updateLocal(Paths.get(element.getSelectionModel().getSelectedItem()));
    }

    Thread mainPanelServerListener = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while (true) {
                    Object object;
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
                            Path pathToNewEmptyDirectory = Paths.get(Paths.get(pathFieldLocal.getText()) + File.separator + "" + fileMessage.getFileName());
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
                                Files.write(Paths.get(Paths.get(pathFieldLocal.getText()) + File.separator + "" + fileMessage.getFileName()), fileMessage.getData(), StandardOpenOption.CREATE);
                            } catch (NullPointerException e) {
                                log.error("Error: ", e);
                            }
                        }
                        Platform.runLater(() -> reloadLocal());
                    } else if (object.toString().equals("succes")) {
                        log.info("Успешно");
                    }
                }
            } catch (Exception e) {
                log.error("Error: ", e);
                Platform.runLater(Alerts::networkError);
            }
        }
    });

    public void initializeListOfCloudStorageItems(HashMap<Integer, LinkedList<File>> listOfCloudStorageFiles) {
        if (cloudStorageFolderLevelCounter > 0) {
            cloudStorageFolderLevelCounter = 0;
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
                            e.printStackTrace();
                        }
                    } else {
                        initialSizeOfCloudFileOrDir = listOfCloudStorageFiles.get(0).get(i).length();
                    }
                    String dateOfLastModification = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(listOfCloudStorageFiles.get(0)
                            .get(i).lastModified()));
                    File pathOfFileInCloudStorage = new File(listOfCloudStorageFiles.get(0).get(i).getAbsolutePath());
                    listOfCloudItems.addAll(new StorageItem(nameOfCloudFileOrDir, initialSizeOfCloudFileOrDir, dateOfLastModification, pathOfFileInCloudStorage));
                }
            }
            listOfCloudStorageElements.setItems(listOfCloudItems);
            listOfCloudStorageElements.setCellFactory(param -> new StorageListViewItem());
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public static long getActualSizeOfFolder(File file) {
        long actualSizeOfFolder = 0;
        if (file.isDirectory()) {
            for (File f : Objects.requireNonNull(file.listFiles())) {
                if (f.isFile()) {
                    actualSizeOfFolder += f.length();
                } else if (f.isDirectory()) {
                    actualSizeOfFolder += getActualSizeOfFolder(f);
                }
            }
        }
        return actualSizeOfFolder;
    }

    public void openServerDir(MouseEvent mouseEvent) {
        LinkedList<File> pathsToCloudStorageFiles = new LinkedList<>();
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
                        for (File value : nextDirectory) {
                            try {
                                pathsToCloudStorageFiles.add(value);
                            } catch (IndexOutOfBoundsException e) {
                                log.error("Error: ", e);
                            }
                        }
                        playSoundOfFolderOpening();
                        cloudStorageFolderLevelCounter++;
                        folderCloudStorageListViews.put(cloudStorageFolderLevelCounter, pathsToCloudStorageFiles);
                        ObservableList<StorageItem> listOfCloudItems = FXCollections.observableArrayList();
                        for (File file : nextDirectory) {
                            listElement(listOfCloudItems, file);
                            listOfCloudStorageElements.setItems(listOfCloudItems);
                            listOfCloudStorageElements.setCellFactory(param -> new StorageListViewItem());
                        }
                    }
                    if (cloudStorageFolderLevelCounter > 0) {
                        upButtonServer.setVisible(true);
                    }
                }
            }
        }
    }

    private void listElement(ObservableList<StorageItem> listOfCloudItems, File file) {
        String nameOfCloudStorageFileOrDirectory = file.getName();
        long initialSizeOfLocalStorageFileOrDirectory = file.length();
        String dateOfLastModification = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                .format(new Date(file.lastModified()));
        File pathToFileInLocalStorage = new File(file.getAbsolutePath());
        listOfCloudItems.addAll(new StorageItem(nameOfCloudStorageFileOrDirectory, initialSizeOfLocalStorageFileOrDirectory, dateOfLastModification, pathToFileInLocalStorage));
    }

    public void upServer() {
        ObservableList<StorageItem> listOfCloudItems = FXCollections.observableArrayList();
        if (cloudStorageFolderLevelCounter > 0) {
            LinkedList<File> files = new LinkedList<>(folderCloudStorageListViews.get(cloudStorageFolderLevelCounter - 1));
            for (File file : files) {
                listElement(listOfCloudItems, file);
            }
            listOfCloudStorageElements.setItems(listOfCloudItems);
            listOfCloudStorageElements.setCellFactory(param -> new StorageListViewItem());
            folderCloudStorageListViews.remove(cloudStorageFolderLevelCounter);
            cloudStorageFolderLevelCounter--;
            if (cloudStorageFolderLevelCounter <= 0) {
                upButtonServer.setVisible(false);
            }
            playSoundOfFolderOpening();
        }
    }

    public LinkedList<File> getPathsOfSelectedFilesInCloudStorage() {
        listOfCloudStorageElements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        LinkedList<File> listOfSelectedElementsInCloudStorage = new LinkedList<>();
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

    public void isDeleteKey() {
        localFilesTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                deleteLocalFile();
            }
        });
        listOfCloudStorageElements.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                sendDeletionMessageToServer();
            }
        });
    }

    public void selectAllServerFiles() {
        if (listOfCloudStorageElements.getItems().size() == listOfCloudStorageElements.getSelectionModel().getSelectedItems().size()) {
            listOfCloudStorageElements.getSelectionModel().clearSelection();
        } else {
            listOfCloudStorageElements.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listOfCloudStorageElements.getSelectionModel().selectAll();
            listOfCloudStorageElements.requestFocus();
        }
    }

    public void updateServer() {
        ConnectionServer.sendUpdateMessageToServer(CurrentLogin.getCurrentLogin());
    }

    public void downloadFilesIntoLocalStorage() {
        ConnectionServer.sendFileRequest(getPathsOfSelectedFilesInCloudStorage());
    }

    public void sendToCloud() {
        ConnectionServer.transferFilesToCloudStorage(CurrentLogin.getCurrentLogin(), getPathsSelectedLocal());
    }

    public void changeUser() {
        try {
            Stage stage;
            Parent root;
            stage = (Stage) main.getScene().getWindow();
            root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/LoginPanel.fxml")));
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setTitle("Облако JJ");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void menuExit() {
        Platform.exit();
    }

    public void playSoundOfFolderOpening() {
        soundOfFolderOpening.setVolume(0.2);
        soundOfFolderOpening.play();
    }
}