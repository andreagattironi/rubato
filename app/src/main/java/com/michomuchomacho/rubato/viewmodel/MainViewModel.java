package com.michomuchomacho.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.michomuchomacho.rubato.github.models.LatestRelease;
import com.michomuchomacho.rubato.repository.QueueRepository;
import com.michomuchomacho.rubato.repository.SystemRepository;
import com.michomuchomacho.rubato.subsonic.models.OpenSubsonicExtension;
import com.michomuchomacho.rubato.subsonic.models.SubsonicResponse;
import com.michomuchomacho.rubato.util.Preferences;

import java.util.List;

public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "SearchViewModel";

    private final SystemRepository systemRepository;

    private final MutableLiveData<String> activeMusicFolderId =
            new MutableLiveData<>(Preferences.getActiveMusicFolderId());

    public MainViewModel(@NonNull Application application) {
        super(application);

        systemRepository = new SystemRepository();
    }

    /**
     * Signals that the active library changed. Preferences is a plain SharedPreferences read with
     * no observable, so without this a screen already on display has no way to learn it is showing
     * the wrong library.
     *
     * Observers should treat this as "look again" and read Preferences for the value. The emitted
     * id can be stale after a server switch, because the stored choice is keyed per server while
     * this lives as long as the activity.
     */
    public LiveData<String> getActiveMusicFolderId() {
        return activeMusicFolderId;
    }

    public void setActiveMusicFolderId(String musicFolderId) {
        Preferences.setActiveMusicFolderId(musicFolderId);
        // Read back instead of posting the argument, so the sentinel that callers pass for every
        // library arrives here as the null that getActiveMusicFolderId reports for it.
        activeMusicFolderId.setValue(Preferences.getActiveMusicFolderId());
    }

    public boolean isQueueLoaded() {
        QueueRepository queueRepository = new QueueRepository();
        return queueRepository.count() != 0;
    }

    public LiveData<SubsonicResponse> ping() {
        return systemRepository.ping();
    }

    public LiveData<List<OpenSubsonicExtension>> getOpenSubsonicExtensions() {
        return systemRepository.getOpenSubsonicExtensions();
    }

    public LiveData<LatestRelease> checkTempusUpdate() {
        return systemRepository.checkTempusUpdate();
    }
}
