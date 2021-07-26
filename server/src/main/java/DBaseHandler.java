import lombok.extern.slf4j.Slf4j;
import java.sql.*;

@Slf4j
public class DBaseHandler {
    private static Connection connection;
    private static Statement statement;

    public static void getConnectionWithDB() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:./server/jjcloud.sqlite");
            statement = connection.createStatement();
        } catch (SQLException e) {
            log.error("SQLException");
            e.printStackTrace();
        }
    }


    public static void disconnectDB() {
        try {
            statement.close();
            connection.close();
        } catch (SQLException e) {
            log.error("SQLException");
        }
    }

    public static boolean checkUserExists(String login) {
        String dbQuery = "SELECT login FROM users";
        try {
            ResultSet resultSet = statement.executeQuery(dbQuery);
            while (resultSet.next()) {
                if (resultSet.getString("login").equals(login)) {
                    return true;
                }
            }
        } catch (SQLException e) {
            log.error("SQLException");
        }
        return false;
    }

    public static boolean checkPassword(String login, String userPassword) {
        String dbQuery = "SELECT password FROM users WHERE login='" + login + "'";
        try {
            ResultSet resultSet = statement.executeQuery(dbQuery);
            String password = resultSet.getString("password");
            if (password.equals(userPassword)) {
                return true;
            }
        } catch (SQLException e) {
            log.error("SQLException");
        }
        return false;
    }

    public static boolean register(String login, String password) {
        String dbQuery = "INSERT INTO users(login,password) VALUES ('" + login + "','" + password + "')";
        try {
            int rows = statement.executeUpdate(dbQuery);
            if (rows > 0) {
                return true;
            }
        } catch (SQLException e) {
            log.error("SQLException");
        }
        return false;
    }
}