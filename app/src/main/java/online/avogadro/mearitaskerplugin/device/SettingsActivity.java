package online.avogadro.mearitaskerplugin.device;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.Toast;

import online.avogadro.mearitaskerplugin.R;
import online.avogadro.mearitaskerplugin.app.LogDumper;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREF_SHOW_CAMERA_ID = "pref_show_camera_id";
    public static final String PREF_GROUP_BY_FIRST_WORD = "pref_group_by_first_word";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings_title);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        SwitchCompat switchShowId = findViewById(R.id.switchShowCameraId);
        SwitchCompat switchGroup = findViewById(R.id.switchGroupByFirstWord);

        switchShowId.setChecked(prefs.getBoolean(PREF_SHOW_CAMERA_ID, true));
        switchGroup.setChecked(prefs.getBoolean(PREF_GROUP_BY_FIRST_WORD, false));

        switchShowId.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(PREF_SHOW_CAMERA_ID, isChecked).apply());

        switchGroup.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(PREF_GROUP_BY_FIRST_WORD, isChecked).apply());

        Button downloadLogs = findViewById(R.id.buttonDownloadLogs);
        downloadLogs.setOnClickListener(v -> {
            downloadLogs.setEnabled(false);
            new Thread(() -> {
                String location = LogDumper.dumpToDownloads(this);
                runOnUiThread(() -> {
                    downloadLogs.setEnabled(true);
                    if (location != null) {
                        Toast.makeText(this, getString(R.string.logs_saved_to, location), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.logs_save_failed, Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
