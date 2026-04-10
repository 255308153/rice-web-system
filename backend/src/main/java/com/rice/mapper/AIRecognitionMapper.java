package com.rice.mapper;

import com.rice.entity.AIRecognition;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AIRecognitionMapper {

    @Insert("INSERT INTO ai_recognition(user_id, image_url, result, confidence) VALUES(#{userId}, #{imageUrl}, #{result}, #{confidence})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AIRecognition recognition);

    @Select("SELECT * FROM ai_recognition WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{limit}")
    List<AIRecognition> findByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM ai_recognition WHERE id = #{id} LIMIT 1")
    AIRecognition findById(@Param("id") Long id);

    @Select("SELECT * FROM ai_recognition ORDER BY create_time DESC LIMIT #{limit}")
    List<AIRecognition> findRecent(@Param("limit") int limit);

    @Update("UPDATE ai_recognition SET result = #{result} WHERE id = #{id}")
    int updateResult(@Param("id") Long id, @Param("result") String result);

    @Select("SELECT COUNT(*) FROM ai_recognition")
    Long countAll();

    @Select("SELECT COUNT(*) FROM ai_recognition WHERE create_time >= #{from}")
    Long countSince(@Param("from") LocalDateTime from);

    @Select("SELECT DISTINCT user_id FROM ai_recognition WHERE create_time >= #{from}")
    List<Long> findUserIdsSince(@Param("from") LocalDateTime from);
}
