package gem.kevin.widget;

public class FileClip {
    private float _clipType;
    private String _filePath;

    public FileClip(float clipType, String filePath) {
        _clipType = clipType;
        _filePath = filePath;
    }

    public float getType() {
        return _clipType;
    }

    public String getFilePath() {
        return _filePath;
    }
}
