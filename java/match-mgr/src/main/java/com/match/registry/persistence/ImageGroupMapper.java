package com.match.registry.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ImageGroupMapper extends BaseMapper<ImageGroupRecord> {
    @Select("SELECT * FROM image_group ORDER BY updated_at DESC, group_id DESC LIMIT #{limit}")
    List<ImageGroupRecord> selectVisible(@Param("limit") int limit);
}
