package com.michomuchomacho.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.michomuchomacho.rubato.repository.SongRepository;
import com.michomuchomacho.rubato.subsonic.models.Child;

import java.util.List;

public class StarredSyncViewModel extends AndroidViewModel {
    private final SongRepository songRepository;

    private final MutableLiveData<List<Child>> starredTracks = new MutableLiveData<>(null);

    public StarredSyncViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
    }

    public LiveData<List<Child>> getStarredTracks(LifecycleOwner owner) {
        songRepository.getStarredSongs(false, -1).observe(owner, starredTracks::postValue);
        return starredTracks;
    }
}
