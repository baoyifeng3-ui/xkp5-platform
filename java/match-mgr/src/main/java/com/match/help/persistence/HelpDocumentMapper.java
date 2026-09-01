package com.match.help.persistence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;import org.apache.ibatis.annotations.*;import java.util.List;
public interface HelpDocumentMapper extends BaseMapper<HelpDocumentRecord>{@Select("SELECT * FROM help_document ORDER BY updated_at DESC")List<HelpDocumentRecord> selectAllOrdered();@Select("SELECT * FROM help_document WHERE published=1 AND audience IN (${audiences}) ORDER BY updated_at DESC")List<HelpDocumentRecord> selectPublished(@Param("audiences")String audiences);}
