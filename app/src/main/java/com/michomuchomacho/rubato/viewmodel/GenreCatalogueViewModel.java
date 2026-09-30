package com.michomuchomacho.rubato.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.michomuchomacho.rubato.repository.GenreRepository;
import com.michomuchomacho.rubato.subsonic.models.Genre;

import java.util.List;

public class GenreCatalogueViewModel extends AndroidViewModel {
    private final GenreRepository genreRepository;
    private LiveData<List<Genre>> genres;

    public GenreCatalogueViewModel(@NonNull Application application) {
        super(application);

        genreRepository = new GenreRepository();
    }

    public void loadGenreList() {
        if (genres == null) {
            genres = genreRepository.getGenres(false, -1);
        }
    }

    public LiveData<List<Genre>> getGenreList() {
        return genres;
    }
}
