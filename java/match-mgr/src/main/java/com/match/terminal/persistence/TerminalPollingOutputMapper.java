package com.match.terminal.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface TerminalPollingOutputMapper {
    @Insert("INSERT INTO terminal_polling_output(session_id,output_cursor,output_data,created_at) VALUES(#{sessionId},#{outputCursor},#{outputData},#{createdAt})")
    int insert(TerminalPollingOutputRecord record);

    @Select("SELECT session_id,output_cursor,output_data,created_at FROM terminal_polling_output WHERE session_id=#{sessionId} AND output_cursor>#{cursor} ORDER BY output_cursor LIMIT 100")
    List<TerminalPollingOutputRecord> selectAfter(@Param("sessionId") String sessionId, @Param("cursor") long cursor);
}
