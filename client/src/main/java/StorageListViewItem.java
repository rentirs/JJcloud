import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;

import java.awt.*;
import java.io.IOException;

public class StorageListViewItem extends ListCell<StorageItem> {

    FXMLLoader localStorageItemLoader;
    public Label localItemName;
    public Label localItemSize;
    public Label localItemModified;
    public VBox localStorageItemCell;

    @Override
    protected void updateItem(StorageItem item, boolean empty) {
        super.updateItem(item, empty);


        try {
            if (empty || item == null) {
                localItemName.setText("");
                localItemSize.setText("");
                localItemModified.setText("");
            } else {
                if (localStorageItemLoader == null) {
                    localStorageItemLoader = new FXMLLoader(getClass().getResource("/ItemCellView.fxml"));
                    localStorageItemLoader.setController(this);
                    try {
                        localStorageItemLoader.load();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                localItemName.setText(item.getName());

                if (item.getSize() / 1073741824 > 0) {
                    localItemSize.setText(item.getSize() / 1073741824 + " GB");
                } else if (item.getSize() / 1048576 > 0) {
                    localItemSize.setText((item.getSize() / 1048576) + " MB");
                } else if (item.getSize() / 1024 > 0) {
                    localItemSize.setText(item.getSize() / 1024 + " KB");
                } else if (item.getSize() / 1024 <= 0) {
                    localItemSize.setText(item.getSize() + " bytes");
                }
                localItemModified.setText("" + item.getLastModificationDate());
            }
            setGraphic(localStorageItemCell);
        }catch (NullPointerException e){
        }
    }
}