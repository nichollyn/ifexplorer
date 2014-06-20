package gem.kevin.util.ui;

import android.util.Log;
import android.view.View;

public class ViewUtil {
    public static boolean isViewContained(View view, float rawX, float rawY) {
        Log.i("ViewUtil", "isViewContained");
        if (view == null || view.getWidth() == 0) {
            return false;
        }

        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        Log.i("ViewUtil", "loc of view is: x:" + x + " y:" + y);
        int width = view.getWidth();
        int height = view.getHeight();

        if (rawX < x || rawX > x + width || rawY < y || rawY > y + height) {
            return false;
        } else {
            return true;
        }
    }
}
