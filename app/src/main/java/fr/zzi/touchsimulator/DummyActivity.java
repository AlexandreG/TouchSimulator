package fr.zzi.touchsimulator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Empty activy. Its only purpose is to launch the service.
 */
public class DummyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startService(new Intent(this, MainService.class));

        finish();
    }

}
