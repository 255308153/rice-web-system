-- 助农大米平台数据库表结构

-- 用户表
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码(加密)',
  `phone` VARCHAR(20) UNIQUE COMMENT '手机号',
  `avatar` VARCHAR(255) COMMENT '头像URL',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色:USER/MERCHANT/EXPERT/ADMIN',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0禁用/1正常',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_phone (`phone`),
  INDEX idx_role (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 收货地址表
CREATE TABLE `address` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(50) NOT NULL COMMENT '收货人',
  `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
  `province` VARCHAR(50) NOT NULL COMMENT '省',
  `city` VARCHAR(50) NOT NULL COMMENT '市',
  `district` VARCHAR(50) NOT NULL COMMENT '区',
  `detail` VARCHAR(255) NOT NULL COMMENT '详细地址',
  `is_default` TINYINT DEFAULT 0 COMMENT '是否默认:0否/1是',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- 认证申请表
CREATE TABLE `certification` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role` VARCHAR(20) NOT NULL COMMENT '申请角色:MERCHANT/EXPERT',
  `credentials` TEXT COMMENT '资质证明(JSON)',
  `status` TINYINT DEFAULT 0 COMMENT '状态:0待审核/1通过/2拒绝',
  `audit_time` DATETIME COMMENT '审核时间',
  `audit_remark` VARCHAR(255) COMMENT '审核备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`),
  INDEX idx_status (`status`),
  INDEX idx_role_status_time (`role`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证申请表';

-- AI识别记录表
CREATE TABLE `ai_recognition` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `image_url` VARCHAR(255) NOT NULL,
  `result` TEXT COMMENT '识别结果(JSON)',
  `confidence` DECIMAL(5,4) COMMENT '置信度',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI识别记录表';

-- AI对话记录表
CREATE TABLE `ai_chat` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `question` TEXT NOT NULL,
  `answer` TEXT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话记录表';

-- 店铺表
CREATE TABLE `shop` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '商户用户ID',
  `name` VARCHAR(100) NOT NULL COMMENT '店铺名称',
  `description` TEXT COMMENT '店铺简介',
  `license` VARCHAR(255) COMMENT '营业执照',
  `contact` VARCHAR(50) COMMENT '联系方式',
  `avatar` VARCHAR(255) COMMENT '店铺头像',
  `cover` VARCHAR(255) COMMENT '店铺封面',
  `rating` DECIMAL(3,2) DEFAULT 5.0 COMMENT '评分',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0禁用/1正常',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`),
  INDEX idx_status_rating (`status`, `rating`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商品表
CREATE TABLE `product` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `shop_id` BIGINT NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `category` VARCHAR(50) COMMENT '分类',
  `price` DECIMAL(10,2) NOT NULL,
  `stock` INT DEFAULT 0,
  `images` TEXT COMMENT '图片URL(JSON数组)',
  `description` TEXT,
  `specs` TEXT COMMENT '规格参数(JSON)',
  `status` TINYINT DEFAULT 1 COMMENT '0下架/1上架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_shop_id (`shop_id`),
  INDEX idx_status_shop_time (`status`, `shop_id`, `create_time`),
  INDEX idx_status_name (`status`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单表
CREATE TABLE `order` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_no` VARCHAR(50) UNIQUE NOT NULL,
  `user_id` BIGINT NOT NULL,
  `shop_id` BIGINT NOT NULL,
  `total_price` DECIMAL(10,2) NOT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0待付款/1待发货/2待收货/3已完成/4已取消',
  `address_id` BIGINT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`),
  INDEX idx_shop_id (`shop_id`),
  INDEX idx_user_status_time (`user_id`, `status`, `create_time`),
  INDEX idx_shop_status_time (`shop_id`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订单详情表
CREATE TABLE `order_item` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `quantity` INT NOT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  INDEX idx_order_id (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评价表
CREATE TABLE `review` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `rating` TINYINT NOT NULL COMMENT '1-5星',
  `content` TEXT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_product_id (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 帖子表
CREATE TABLE `post` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT NOT NULL,
  `category` VARCHAR(50) DEFAULT '综合交流' COMMENT '论坛分类',
  `images` TEXT COMMENT '图片(JSON数组)',
  `tags` VARCHAR(200) COMMENT '标签',
  `views` INT DEFAULT 0,
  `likes` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '状态:0异常/1正常/2下架',
  `audit_remark` VARCHAR(255) COMMENT '审核备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_id (`user_id`),
  INDEX idx_status (`status`),
  INDEX idx_status_category_time (`status`, `category`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评论表
CREATE TABLE `comment` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `content` TEXT NOT NULL,
  `parent_id` BIGINT COMMENT '父评论ID',
  `status` TINYINT DEFAULT 1 COMMENT '状态:0下架/1正常',
  `audit_remark` VARCHAR(255) COMMENT '审核备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_post_id (`post_id`),
  INDEX idx_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 点赞记录表
CREATE TABLE `like_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `target_id` BIGINT NOT NULL COMMENT '目标ID',
  `target_type` VARCHAR(20) NOT NULL COMMENT 'POST/COMMENT',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_target (`user_id`, `target_id`, `target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收藏表
CREATE TABLE `favorite` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `post_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_post (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 会话表
CREATE TABLE `conversation` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user1_id` BIGINT NOT NULL,
  `user2_id` BIGINT NOT NULL,
  `last_message` TEXT,
  `last_time` DATETIME,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users (`user1_id`, `user2_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
CREATE TABLE `message` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `conversation_id` BIGINT NOT NULL,
  `sender_id` BIGINT NOT NULL,
  `content` TEXT NOT NULL,
  `type` VARCHAR(20) DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE',
  `is_read` TINYINT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_conversation_id (`conversation_id`),
  INDEX idx_conversation_read_time (`conversation_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 购物车表
CREATE TABLE `cart` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `quantity` INT DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_product (`user_id`, `product_id`),
  INDEX idx_user_id (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';


-- 物流表
CREATE TABLE `logistics` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `company` VARCHAR(50) COMMENT '物流公司',
  `tracking_number` VARCHAR(100) COMMENT '物流单号',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_order_id (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流表';

-- 退款申请表
CREATE TABLE `refund_request` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `shop_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `reason` VARCHAR(500) COMMENT '退款原因',
  `amount` DECIMAL(10,2) NOT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0待处理/1同意/2拒绝',
  `merchant_remark` VARCHAR(500) COMMENT '商户处理说明',
  `audit_time` DATETIME COMMENT '处理时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_order_id (`order_id`),
  INDEX idx_shop_id (`shop_id`),
  INDEX idx_user_id (`user_id`),
  INDEX idx_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款申请表';

-- 系统通知表
CREATE TABLE `system_notification` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT COMMENT '发布人ID',
  `role` VARCHAR(20) DEFAULT 'ALL' COMMENT '接收角色:ALL/USER/MERCHANT/EXPERT/ADMIN',
  `title` VARCHAR(200) NOT NULL COMMENT '标题',
  `content` TEXT NOT NULL COMMENT '内容',
  `type` VARCHAR(20) DEFAULT 'NOTICE' COMMENT '类型',
  `is_read` TINYINT DEFAULT 0 COMMENT '是否已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_role (`role`),
  INDEX idx_type (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知表';


-- 系统配置表
CREATE TABLE `system_config` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `config_key` VARCHAR(100) NOT NULL UNIQUE,
  `config_value` TEXT,
  `description` VARCHAR(255),
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 操作日志表
CREATE TABLE `admin_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `admin_id` BIGINT NOT NULL,
  `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
  `target` VARCHAR(100) COMMENT '操作对象',
  `detail` TEXT COMMENT '操作详情',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_admin_id (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';
