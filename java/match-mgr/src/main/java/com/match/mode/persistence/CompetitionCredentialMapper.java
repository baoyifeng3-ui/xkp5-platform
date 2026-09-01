package com.match.mode.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CompetitionCredentialMapper extends BaseMapper<CompetitionCredentialRecord> {
    @Select("SELECT * FROM competition_credential WHERE mode_generation=#{generation} ORDER BY user_id")
    List<CompetitionCredentialRecord> selectByGeneration(@Param("generation") Long generation);
    @Select("SELECT * FROM competition_credential WHERE mode_generation=#{generation} AND user_id=#{userId}")
    CompetitionCredentialRecord selectOne(@Param("generation") Long generation, @Param("userId") Integer userId);
    @Delete("DELETE FROM competition_credential WHERE mode_generation=#{generation}")
    int deleteGeneration(@Param("generation") Long generation);
}
