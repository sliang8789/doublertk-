package com.example.doublertk.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 船舶信息数据访问对象
 */
@Dao
public interface ShipInfoDao {
    
    @Insert
    long insert(ShipInfo shipInfo);
    
    @Update
    void update(ShipInfo shipInfo);
    
    @Delete
    void delete(ShipInfo shipInfo);
    
    @Query("SELECT * FROM ship_info ORDER BY createTime DESC")
    List<ShipInfo> getAll();
    
    @Query("SELECT * FROM ship_info WHERE id = :id")
    ShipInfo getById(long id);
    
    @Query("SELECT * FROM ship_info WHERE isDefault = 1 LIMIT 1")
    ShipInfo getDefault();
    
    @Query("UPDATE ship_info SET isDefault = 0")
    void clearAllDefault();
    
    @Query("UPDATE ship_info SET isDefault = 1 WHERE id = :id")
    void setDefault(long id);
    
    @Query("SELECT COUNT(*) FROM ship_info")
    int getCount();
    
    @Query("DELETE FROM ship_info WHERE id = :id")
    void deleteById(long id);
}
