package com.rice.mapper;

import com.rice.entity.AIChat;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AIChatMapper {

    @Insert("INSERT INTO ai_chat(user_id, question, answer) VALUES(#{userId}, #{question}, #{answer})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AIChat chat);

    @Select("SELECT * FROM ai_chat WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{limit}")
    List<AIChat> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM ai_chat WHERE id = #{id} LIMIT 1")
    AIChat findById(@Param("id") Long id);

    @Select("SELECT * FROM ai_chat ORDER BY create_time DESC LIMIT #{limit}")
    List<AIChat> findRecent(@Param("limit") int limit);

    @Update("UPDATE ai_chat SET question = #{question} WHERE id = #{id}")
    int updateQuestion(@Param("id") Long id, @Param("question") String question);

    @Update("UPDATE ai_chat SET answer = #{answer} WHERE id = #{id}")
    int updateAnswer(@Param("id") Long id, @Param("answer") String answer);

    @Select("SELECT COUNT(*) FROM ai_chat")
    Long countAll();

    @Select("SELECT COUNT(*) FROM ai_chat WHERE create_time >= #{from}")
    Long countSince(@Param("from") LocalDateTime from);

    @Select("SELECT DISTINCT user_id FROM ai_chat WHERE create_time >= #{from}")
    List<Long> findUserIdsSince(@Param("from") LocalDateTime from);
}
