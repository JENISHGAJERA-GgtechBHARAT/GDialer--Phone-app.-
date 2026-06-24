package com.gg_tech_bharat.gdialer;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {ContactModel.class, RecentModel.class, BlockedNumber.class, QuickReplyModel.class, RecentSearch.class, VoicemailEntity.class}, version = 8, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ContactDao contactDao();
    public abstract RecentDao recentDao();
    public abstract BlockedNumberDao blockedNumberDao();
    public abstract QuickReplyDao quickReplyDao();
    public abstract RecentSearchDao recentSearchDao();
    public abstract VoicemailDao voicemailDao();

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setPriority(Thread.MAX_PRIORITY - 1);
                return t;
            });

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "gdialer_database")
                            .addCallback(sRoomDatabaseCallback)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            seedQuickReplies();
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            seedQuickReplies();
        }

        private void seedQuickReplies() {
            databaseWriteExecutor.execute(() -> {
                try {
                    QuickReplyDao dao = INSTANCE.quickReplyDao();
                    java.util.List<QuickReplyModel> existing = dao.getAllQuickRepliesSync();
                    if (existing == null || existing.isEmpty()) {
                        dao.insert(new QuickReplyModel("I will call you later."));
                        dao.insert(new QuickReplyModel("Can't talk right now."));
                        dao.insert(new QuickReplyModel("I'm in a meeting."));
                        dao.insert(new QuickReplyModel("Please text me."));
                        dao.insert(new QuickReplyModel("I'll call you back."));
                        dao.insert(new QuickReplyModel("Call me back later."));
                    }
                } catch (Exception e) {
                    android.util.Log.e("AppDatabase", "Error seeding quick replies", e);
                }
            });
        }
    };
}
