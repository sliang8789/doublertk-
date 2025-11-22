package com.example.doublertk.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room 数据库
 * 数据库名称：rtkroom
 */
@Database(
    entities = {CoordinateSystem.class, KnownPoint.class, Job.class},
    version = 2,
    exportSchema = false
)
@TypeConverters({})
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "rtkroom";

    public abstract CoordinateSystemDao coordinateSystemDao();
    public abstract KnownPointDao knownPointDao();
    public abstract JobDao jobDao();

    private static volatile AppDatabase INSTANCE;

    /**
     * 数据库迁移：从版本1升级到版本2
     * 添加jobs表
     */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 创建jobs表
            database.execSQL("CREATE TABLE IF NOT EXISTS `jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `description` TEXT)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_jobs_name` ON `jobs` (`name`)");
        }
    };

    /**
     * 获取数据库实例（单例模式）
     */
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 销毁数据库实例（主要用于测试）
     */
    public static void destroyInstance() {
        INSTANCE = null;
    }
}

