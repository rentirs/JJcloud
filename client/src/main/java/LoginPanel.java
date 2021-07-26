import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class LoginPanel implements Initializable {

    @FXML
    TextField loginField;
    @FXML
    PasswordField passwordField;
    @FXML
    Button registrationButton;
    @FXML
    VBox registrationBlock;
    @FXML
    Button finalRegistrationButton;
    @FXML
    Button cancelRegistrationButton;
    @FXML
    TextField registrationLoginForm;
    @FXML
    TextField registrationPassForm;
    @FXML
    TextField repeatPassForm;
    @FXML
    Button entryButton;
    @FXML
    Label messageToUser;
    @FXML
    Label registrationMessage;
    @FXML
    Label registrationSuccessNotification;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ConnectionServer.startConnection();
        OpeningPanelServerListener.setDaemon(true);
        OpeningPanelServerListener.start();
    }

    public void showRegistrationForms(ActionEvent actionEvent) {
        registrationBlock.setVisible(true);
        finalRegistrationButton.setVisible(true);
        registrationButton.setVisible(false);
        cancelRegistrationButton.setVisible(true);
        registrationSuccessNotification.setVisible(false);
    }

    public void sendAuthMessage(ActionEvent actionEvent) {
        if (!loginField.getText().isEmpty() && !passwordField.getText().isEmpty()) {
            ConnectionServer.sendAuthMessageToServer(loginField.getText(), passwordField.getText());
            loginField.clear();
            passwordField.clear();
        }
    }

    public void cancelRegistration(ActionEvent actionEvent) {
        registrationButton.setVisible(true);
        cancelRegistrationButton.setVisible(false);
        registrationLoginForm.clear();
        registrationPassForm.clear();
        repeatPassForm.clear();
        registrationBlock.setVisible(false);
        finalRegistrationButton.setVisible(false);
        registrationMessage.setText("");
        registrationSuccessNotification.setVisible(false);
    }

    public void sendRegMessageToServer(ActionEvent actionEvent) {
        if (!registrationLoginForm.getText().isEmpty() && !registrationPassForm.getText().isEmpty() && !repeatPassForm.getText().isEmpty()) {
            if (registrationPassForm.getText().equals(repeatPassForm.getText())) {
                ConnectionServer.sendRegMessageToServer(registrationLoginForm.getText(), repeatPassForm.getText());
            } else {
                registrationMessage.setText("Пароли отличаются. Попробуйте ещё.");
                registrationPassForm.clear();
                repeatPassForm.clear();
            }
        }
    }

    public void switchScenes(String login) throws IOException {
        Stage mainStage;
        Parent root = FXMLLoader.load(getClass().getResource("./MainPanel.fxml"));
        mainStage = (Stage) entryButton.getScene().getWindow();
        mainStage.setScene(new Scene(root));
        mainStage.setResizable(false);
        mainStage.setTitle("Облако пользователя: " + login);
        mainStage.show();
    }

    public void authorizeAndSwitchToMainPanel(String login) {
        Platform.runLater(() -> {
            try {
                switchScenes(login);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    Thread OpeningPanelServerListener = new Thread(() -> {
        for (; ; ) {
            Object serverMessage = null;
            try {
                serverMessage = ConnectionServer.readIncomingObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (serverMessage.toString().startsWith("userIsValid/")) {
                String[] receivedWords = serverMessage.toString().split("/");
                String login = receivedWords[1];
                CurrentLogin.setCurrentLogin(login);
                authorizeAndSwitchToMainPanel(login);
            } else if (serverMessage.toString().startsWith("wrongPassword")) {
                Platform.runLater(() -> messageToUser.setText("Неверный пароль"));
            } else if (serverMessage.toString().startsWith("userDoesNotExist")) {
                Platform.runLater(() -> messageToUser.setText("Пользователь не найден"));
            } else if (serverMessage.toString().equals("userAlreadyExists")) {
                Platform.runLater(() -> {
                    registrationMessage.setText("Пользователь уже существует");
                    registrationLoginForm.clear();
                });

            } else if (serverMessage.toString().equals("registrationIsSuccessful")) {
                Platform.runLater(() -> {
                    registrationBlock.setVisible(false);
                    registrationButton.setVisible(true);
                    cancelRegistrationButton.setVisible(false);
                    registrationSuccessNotification.setVisible(true);
                });
            }
        }
    });
}