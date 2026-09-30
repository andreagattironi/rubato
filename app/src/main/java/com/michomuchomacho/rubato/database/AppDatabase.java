package com.michomuchomacho.rubato.database;

import androidx.media3.common.util.UnstableApi;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.michomuchomacho.rubato.App;
import com.michomuchomacho.rubato.database.converter.DateConverters;
import com.michomuchomacho.rubato.database.converter.StringListConverter;
import com.michomuchomacho.rubato.database.dao.ChronologyDao;
import com.michomuchomacho.rubato.database.dao.DownloadDao;
import com.michomuchomacho.rubato.database.dao.FavoriteDao;
import com.michomuchomacho.rubato.database.dao.InternetRadioStationDao;
import com.michomuchomacho.rubato.database.dao.LyricsDao;
import com.michomuchomacho.rubato.database.dao.PinnedPlaylistDao;
import com.michomuchomacho.rubato.database.dao.PlaylistDao;
import com.michomuchomacho.rubato.database.dao.PlaylistSongDao;
import com.michomuchomacho.rubato.database.dao.QueueDao;
import com.michomuchomacho.rubato.database.dao.RecentSearchDao;
import com.michomuchomacho.rubato.database.dao.ServerDao;
import com.michomuchomacho.rubato.database.dao.SessionMediaItemDao;
import com.michomuchomacho.rubato.model.Chronology;
import com.michomuchomacho.rubato.model.Download;
import com.michomuchomacho.rubato.model.Favorite;
import com.michomuchomacho.rubato.model.InternetRadioStationCache;
import com.michomuchomacho.rubato.model.LyricsCache;
import com.michomuchomacho.rubato.model.PinnedPlaylist;
import com.michomuchomacho.rubato.model.PlaylistSong;
import com.michomuchomacho.rubato.model.Queue;
import com.michomuchomacho.rubato.model.RecentSearch;
import com.michomuchomacho.rubato.model.Server;
import com.michomuchomacho.rubato.model.SessionMediaItem;
import com.michomuchomacho.rubato.subsonic.models.Playlist;

@UnstableApi
@Database(
        version = 22,
        entities = {
            Queue.class,
            Server.class,
            RecentSearch.class,
            Download.class,
            Chronology.class,
            Favorite.class,
            SessionMediaItem.class,
            Playlist.class,
            PinnedPlaylist.class,
            LyricsCache.class,
            InternetRadioStationCache.class,
            PlaylistSong.class,
        },
        autoMigrations = {
                @AutoMigration(from = 10, to = 11),
                @AutoMigration(from = 11, to = 12),
                @AutoMigration(from = 12, to = 13),
                @AutoMigration(from = 13, to = 14),
                @AutoMigration(from = 14, to = 15),
                @AutoMigration(from = 15, to = 16),
                @AutoMigration(from = 16, to = 17),
                @AutoMigration(from = 17, to = 18),
                @AutoMigration(from = 18, to = 19),
                @AutoMigration(from = 19, to = 20),
                @AutoMigration(from = 20, to = 21),
                @AutoMigration(from = 21, to = 22),
        }
)
@TypeConverters({DateConverters.class, StringListConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private final static String DB_NAME = "tempo_db";
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance() {
        if (instance == null) {
            instance = Room.databaseBuilder(App.getContext(), AppDatabase.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }

        return instance;
    }

    public abstract QueueDao queueDao();

    public abstract ServerDao serverDao();

    public abstract RecentSearchDao recentSearchDao();

    public abstract DownloadDao downloadDao();

    public abstract ChronologyDao chronologyDao();

    public abstract FavoriteDao favoriteDao();

    public abstract SessionMediaItemDao sessionMediaItemDao();

    public abstract PlaylistDao playlistDao();

    public abstract PinnedPlaylistDao pinnedPlaylistDao();

    public abstract PlaylistSongDao playlistSongDao();

    public abstract LyricsDao lyricsDao();

    public abstract InternetRadioStationDao internetRadioStationDao();
}
