package gem.kevin.widget;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public interface MutualSourceProcessor {
    public List<? extends Map<String, ?>> buildMutualAdapterData(
            HashSet<Object> rawSource);
}
