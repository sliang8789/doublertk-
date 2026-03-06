package com.example.doublertk.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

/**
 * Room 数据库
 * 数据库名称：rtkroom
 * 版本4：添加DockingLog表
 */
@Database(
    entities = {
        CoordinateSystem.class,
        KnownPoint.class,
        Job.class,
        ShipInfo.class,
        PositionHistory.class,
        Leg.class,
        DockingLog.class
    },
    version = 4,
    exportSchema = false
)
@TypeConverters({})
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "rtkroom";

    public abstract CoordinateSystemDao coordinateSystemDao();
    public abstract KnownPointDao knownPointDao();
    public abstract JobDao jobDao();
    public abstract ShipInfoDao shipInfoDao();
    public abstract PositionHistoryDao positionHistoryDao();
    public abstract LegDao legDao();
    public abstract DockingLogDao dockingLogDao();

    private static volatile AppDatabase INSTANCE;

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
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 获取数据库实例（兼容旧代码）
     */
    public static AppDatabase getInstance(Context context) {
        return getDatabase(context);
    }

    /**
     * 销毁数据库实例（主要用于测试）
     */
    public static void destroyInstance() {
        INSTANCE = null;
    }
}

