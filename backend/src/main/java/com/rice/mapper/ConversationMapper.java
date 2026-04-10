package com.rice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rice.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
