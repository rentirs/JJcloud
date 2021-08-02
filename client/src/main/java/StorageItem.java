import lombok.Data;
import java.io.File;

@Data
public class StorageItem {

    private String name;
    private long size;
    private String lastModificationDate;
    private File pathToFile;

    public StorageItem(String name, long size, String lastModificationDate, File pathToFile) {
        this.name = name;
        this.size = size;
        this.lastModificationDate = lastModificationDate;
        this.pathToFile = pathToFile;
    }
}