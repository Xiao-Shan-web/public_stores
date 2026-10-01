-- ============================================================
-- Fitness 会员卡系统 - 初始化数据库（单一整合版 V1）
-- 包含全部表结构与种子数据：用户/管理员/会员卡/订单/核销/门店/多卡种/
-- 分享（含媒体扩展）/优惠券/限时活动/消息群发/短信通知/平台治理
-- 原 V4–V8 迁移内容已按版本顺序整合至本文件，其余迁移文件已删除
-- 注意：修改结构请勿直接改本文件（已上线的库会校验和失配），应新增 V2+ 迁移
-- ============================================================

CREATE TABLE IF NOT EXISTS `users` (
    `id`          BIGINT       NOT NULL COMMENT '用户ID（雪花算法生成）',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号（登录凭证）',
    `app_id`      VARCHAR(64)  DEFAULT NULL COMMENT '第三方应用ID（微信小程序openid等）',
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '账号状态：1-正常，0-禁用',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_phone` (`phone`),
    UNIQUE KEY `uk_users_app_id` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 用户资料表（每用户最多一条，与 users 一对一，存头像/昵称/实名/性别/生日/简介）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user_profiles` (
    `user_id`    BIGINT       NOT NULL COMMENT '用户ID（关联 users.id）',
    `avatar`     VARCHAR(500) DEFAULT NULL COMMENT '头像地址',
    `nickname`   VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    `real_name`  VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    `gender`     VARCHAR(10)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '性别：MALE-男，FEMALE-女，UNKNOWN-保密',
    `birthday`   DATE         DEFAULT NULL COMMENT '生日',
    `bio`        VARCHAR(200) DEFAULT NULL COMMENT '个人简介',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料表';

-- ----------------------------
-- 管理员表（账号密码登录，BCrypt 加密）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `admins` (
    `id`          BIGINT       NOT NULL COMMENT '管理员ID（雪花算法生成）',
    `username`    VARCHAR(64)  NOT NULL COMMENT '管理员账号',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码（BCrypt 加密）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admins_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- ----------------------------
-- 会员卡表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `memberships` (
    `id`                BIGINT        NOT NULL COMMENT '会员卡ID（雪花算法生成）',
    `user_id`           BIGINT        NOT NULL COMMENT '所属用户ID',
    `card_no`           VARCHAR(64)  NOT NULL COMMENT '会员卡号',
    `card_type`         VARCHAR(32)  NOT NULL COMMENT '卡类型（历史枚举：TIMES/MONTHLY/YEARLY；新购卡写入卡类型名称如 月卡/季卡）',
    `card_type_id`      BIGINT        DEFAULT NULL COMMENT '卡类型ID（关联 card_types.id，新购卡写入，软关联历史枚举）',
    `total_times`       INT          DEFAULT NULL COMMENT '总次数（次卡适用）',
    `remaining_times`   INT          DEFAULT NULL COMMENT '剩余次数（次卡适用）',
    `start_time`        DATETIME     DEFAULT NULL COMMENT '生效时间',
    `end_time`          DATETIME     DEFAULT NULL COMMENT '到期时间',
    `status`            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：UNACTIVATED-未激活（新购未首次到店），ACTIVE-有效，EXPIRED-已过期，DISABLED-已停用',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_memberships_card_no` (`card_no`),
    KEY `idx_memberships_user_id` (`user_id`),
    KEY `idx_memberships_card_type_id` (`card_type_id`),
    KEY `idx_memberships_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员卡表';

-- ----------------------------
-- 健身卡类型表（管理端维护，支持软删除）
-- category：NORMAL-普通会员卡（月卡/季卡/半年卡/年卡），PT-私教会员卡（后续可加）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `card_types` (
    `id`             BIGINT        NOT NULL COMMENT '卡类型ID',
    `name`           VARCHAR(100)  NOT NULL COMMENT '卡名称（如：月卡、季卡、年卡）',
    `category`       VARCHAR(32)   NOT NULL DEFAULT 'NORMAL' COMMENT '分类：NORMAL-普通会员卡，PT-私教会员卡',
    `duration_days`  INT           NOT NULL COMMENT '有效天数',
    `price`          DECIMAL(10,2) NOT NULL COMMENT '价格',
    `description`    VARCHAR(500)  DEFAULT NULL COMMENT '卡描述',
    `is_active`      TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
    `is_deleted`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_card_types_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='健身卡类型表';

-- ----------------------------
-- 人脸特征表（用于刷脸核销，每用户最多一条）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `face_features` (
    `id`           BIGINT       NOT NULL COMMENT 'ID（雪花算法生成）',
    `user_id`      BIGINT       NOT NULL COMMENT '用户ID',
    `face_token`   VARCHAR(255) NOT NULL COMMENT '人脸特征令牌（百度AI face_token 或 Mock 唯一值）',
    `image_hash`   BIGINT       DEFAULT NULL COMMENT '图片平均哈希（Mock 比对用；百度AI实现为空）',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_face_features_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人脸特征表';

-- ----------------------------
-- 购买订单表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `card_orders` (
    `id`             BIGINT        NOT NULL COMMENT '订单ID（雪花算法生成）',
    `order_no`       VARCHAR(64)  NOT NULL COMMENT '订单号',
    `trade_no`       VARCHAR(64)  DEFAULT NULL COMMENT '第三方交易号（支付宝 trade_no）',
    `user_id`        BIGINT        NOT NULL COMMENT '下单用户ID',
    `membership_id`  BIGINT        DEFAULT NULL COMMENT '关联会员卡ID（支付成功后生成）',
    `card_type`      VARCHAR(32)  NOT NULL COMMENT '购买的卡类型（冗余名称，便于展示）',
    `card_type_id`   BIGINT        DEFAULT NULL COMMENT '购买的卡类型ID（关联 card_types.id）',
    `amount`         DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '订单状态：PENDING-待支付，PAID-已支付，CANCELLED-已取消',
    `pay_time`       DATETIME     DEFAULT NULL COMMENT '支付时间',
    `paid_at`        DATETIME     DEFAULT NULL COMMENT '实际支付时间（以第三方回调为准）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_card_orders_order_no` (`order_no`),
    KEY `idx_card_orders_user_id` (`user_id`),
    KEY `idx_card_orders_trade_no` (`trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购买订单表';

-- ----------------------------
-- 核销记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `entry_records` (
    `id`             BIGINT       NOT NULL COMMENT '核销记录ID（雪花算法生成）',
    `user_id`        BIGINT       DEFAULT NULL COMMENT '会员用户ID（未识别到人脸时为空）',
    `membership_id`  BIGINT       DEFAULT NULL COMMENT '会员卡ID（未匹配到会员卡时为空）',
    `check_type`     VARCHAR(16)  NOT NULL DEFAULT 'FACE' COMMENT '核销方式：FACE-刷脸核销',
    `result`         VARCHAR(16)  NOT NULL COMMENT '核销结果：SUCCESS-入场成功，FAILED-入场失败',
    `fail_reason`    VARCHAR(128) DEFAULT NULL COMMENT '失败原因（result=FAILED 时填写，如 会员卡已过期）',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核销时间',
    PRIMARY KEY (`id`),
    KEY `idx_entry_records_membership_id` (`membership_id`),
    KEY `idx_entry_records_user_id` (`user_id`),
    KEY `idx_entry_records_result` (`result`),
    KEY `idx_entry_records_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='核销记录表';

-- ----------------------------
-- 会员分享表（仅 ACTIVE 会员卡用户可发布）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `shares` (
    `id`            BIGINT       NOT NULL COMMENT '分享ID（雪花算法生成）',
    `user_id`       BIGINT       NOT NULL COMMENT '发布用户ID',
    `title`         VARCHAR(128) NOT NULL COMMENT '标题',
    `content`       TEXT         DEFAULT NULL COMMENT '正文内容',
    `video_url`     VARCHAR(512) DEFAULT NULL COMMENT '视频URL（CDN/对象存储）',
    `cover_url`     VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',
    `like_count`    INT          NOT NULL DEFAULT 0 COMMENT '点赞数（冗余字段，便于排序）',
    `comment_count` INT          NOT NULL DEFAULT 0 COMMENT '评论数（冗余字段）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '状态：NORMAL-正常，HIDDEN-隐藏',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_shares_user_id` (`user_id`),
    KEY `idx_shares_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员分享表';

-- ----------------------------
-- 分享点赞表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `share_likes` (
    `id`          BIGINT   NOT NULL COMMENT '点赞ID（雪花算法生成）',
    `share_id`    BIGINT   NOT NULL COMMENT '分享ID',
    `user_id`     BIGINT   NOT NULL COMMENT '点赞用户ID',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_likes_share_user` (`share_id`, `user_id`),
    KEY `idx_share_likes_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享点赞表';

-- ----------------------------
-- 分享评论表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `share_comments` (
    `id`          BIGINT   NOT NULL COMMENT '评论ID（雪花算法生成）',
    `share_id`    BIGINT   NOT NULL COMMENT '分享ID',
    `user_id`     BIGINT   NOT NULL COMMENT '评论用户ID',
    `content`     VARCHAR(500) NOT NULL COMMENT '评论内容',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_share_comments_share_id` (`share_id`),
    KEY `idx_share_comments_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享评论表';

-- ----------------------------
-- 消息通知表
-- type 取值：
--   VERIFY       核销成功通知
--   EXPIRE       会员卡到期提醒（提前3天）
--   SYSTEM       系统公告
--   ACTIVITY     活动通知
--   INTERACT     分享互动通知（点赞/评论）
--   AI_PLAN      AI 计划生成完成通知
-- ----------------------------
CREATE TABLE IF NOT EXISTS `messages` (
    `id`          BIGINT       NOT NULL COMMENT '消息ID（雪花算法生成）',
    `user_id`     BIGINT       NOT NULL COMMENT '接收用户ID',
    `sender_id`   BIGINT       DEFAULT NULL COMMENT '发送者管理员ID（系统自动消息为NULL）',
    `batch_id`    BIGINT       DEFAULT NULL COMMENT '批次ID（同一次管理员批量发送共享，用于列表聚合/整批删除）',
    `scope`       VARCHAR(16)  DEFAULT NULL COMMENT '发送范围：ALL-全部用户，SPECIFIED-指定用户（管理员发送）',
    `type`        VARCHAR(16)  NOT NULL COMMENT '类型：VERIFY/EXPIRE/SYSTEM/ACTIVITY/INTERACT/AI_PLAN',
    `title`       VARCHAR(128) NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(500) NOT NULL COMMENT '消息内容',
    `ref_id`      BIGINT       DEFAULT NULL COMMENT '关联业务ID（如核销记录ID/分享ID/计划ID等）',
    `is_read`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读：1-已读，0-未读',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_messages_user_id` (`user_id`),
    KEY `idx_messages_is_read` (`user_id`, `is_read`),
    KEY `idx_messages_type` (`type`),
    KEY `idx_messages_batch` (`batch_id`),
    KEY `idx_messages_sender` (`sender_id`, `batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- ----------------------------
-- 客服消息表（用户与客服的实时聊天记录）
-- is_read 语义：接收方是否已读
--   - sender_type=USER 的记录：is_read 表示客服是否已读
--   - sender_type=ADMIN 的记录：is_read 表示用户是否已读
-- ----------------------------
CREATE TABLE IF NOT EXISTS `customer_service_messages` (
    `id`          BIGINT       NOT NULL COMMENT 'ID（雪花算法生成）',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID（会话归属）',
    `sender_type` VARCHAR(16)  NOT NULL COMMENT '发送方类型：USER-用户，ADMIN-客服',
    `content`     VARCHAR(500) NOT NULL COMMENT '消息内容（纯文本，最长500字）',
    `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '接收方是否已读：0-未读，1-已读',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_csm_user_created` (`user_id`, `created_at`),
    KEY `idx_csm_sender_read` (`sender_type`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服消息表';

-- ----------------------------
-- AI 饮食计划表（保存用户历史生成的计划）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_plans` (
    `id`              BIGINT       NOT NULL COMMENT '计划ID（雪花算法生成）',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `height`          INT          NOT NULL COMMENT '身高(cm)',
    `weight`          INT          NOT NULL COMMENT '体重(kg)',
    `age`             INT          NOT NULL COMMENT '年龄',
    `gender`          VARCHAR(8)   NOT NULL COMMENT '性别：MALE/FEMALE',
    `goal`            VARCHAR(16)  NOT NULL COMMENT '目标：MUSCLE_GAIN/FAT_LOSS/MAINTAIN',
    `daily_calories`  INT          NOT NULL COMMENT '每日热量(kcal)',
    `protein`         INT          NOT NULL COMMENT '蛋白质(g)',
    `carbs`           INT          NOT NULL COMMENT '碳水(g)',
    `fat`             INT          NOT NULL COMMENT '脂肪(g)',
    `meals_json`      TEXT         NOT NULL COMMENT '三餐建议(JSON字符串)',
    `provider`        VARCHAR(32)  NOT NULL DEFAULT 'LOCAL' COMMENT '生成源：LOCAL-本地规则/OpenAI/QWEN',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_plans_user_id` (`user_id`),
    KEY `idx_ai_plans_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI饮食计划表';

-- ----------------------------
-- 初始化管理员账号（密码 123456，BCrypt 加密）
-- ----------------------------
INSERT INTO `admins` (`id`, `username`, `password`) VALUES
    (1, 'admin', '$2a$10$JZy.gfUFVSnk077wdg0E..lRMApDzW2g8/8SOwW9ucdEJxpzj/s0G');

-- ----------------------------
-- 初始化卡类型预设数据（月卡/季卡/半年卡/年卡，均为普通会员卡 NORMAL）
-- ----------------------------
INSERT INTO `card_types` (`id`, `name`, `category`, `duration_days`, `price`, `description`, `is_active`, `is_deleted`) VALUES
    (1, '月卡',   'NORMAL', 30,  199.00,  '30天有效，灵活短周期',          1, 0),
    (2, '季卡',   'NORMAL', 90,  499.00,  '90天有效，性价比之选',          1, 0),
    (3, '半年卡', 'NORMAL', 180, 899.00,  '180天有效，长期训练优选',       1, 0),
    (4, '年卡',   'NORMAL', 365, 1599.00, '365天有效，全年畅享',           1, 0);

-- ============================================================
-- 以下内容整合自 V4__multi_store_card_system.sql
-- ============================================================

-- ============================================
-- V4: 多门店 + 多卡种 + 私教课卡 体系升级
-- 1) stores 门店表
-- 2) card_types 增加 scope / store_id / total_times
-- 3) memberships 增加 store_id（购卡门店快照）
-- 4) 预置 1 个示例门店与 3 张私教课卡（10/20/30 节，全店通用）
-- ============================================

-- ----------------------------
-- 门店表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `stores` (
    `id`          BIGINT       NOT NULL COMMENT '门店ID（雪花算法生成，种子数据使用固定ID）',
    `name`        VARCHAR(100) NOT NULL COMMENT '门店名称',
    `address`     VARCHAR(255) DEFAULT NULL COMMENT '门店地址',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店表';

-- ----------------------------
-- card_types 扩展：适用范围 / 绑定门店 / 私教总节数
-- ----------------------------
ALTER TABLE `card_types`
    ADD COLUMN `scope`       VARCHAR(16) DEFAULT 'ALL_STORE' COMMENT '适用范围：ALL_STORE-全店通用，SINGLE_STORE-指定单店' AFTER `category`,
    ADD COLUMN `store_id`    BIGINT      DEFAULT NULL COMMENT '绑定门店ID（scope=SINGLE_STORE 时必填，关联 stores.id）' AFTER `scope`,
    ADD COLUMN `total_times` INT         DEFAULT NULL COMMENT '私教课总节数（category=PT 时必填，NORMAL 为空）' AFTER `duration_days`,
    ADD KEY `idx_card_types_store_id` (`store_id`);

-- 存量普通卡默认全店通用（scope 默认值已为 ALL_STORE，显式回填保证一致）
UPDATE `card_types` SET `scope` = 'ALL_STORE', `store_id` = NULL, `total_times` = NULL
WHERE `category` = 'NORMAL';

-- ----------------------------
-- memberships 扩展：购卡门店快照
-- ----------------------------
ALTER TABLE `memberships`
    ADD COLUMN `store_id` BIGINT DEFAULT NULL COMMENT '购卡时绑定的门店快照（ALL_STORE 卡为 NULL）' AFTER `card_type_id`,
    ADD KEY `idx_memberships_store_id` (`store_id`);

-- ----------------------------
-- 预置门店与私教课卡
-- ----------------------------
INSERT INTO `stores` (`id`, `name`, `address`, `is_deleted`) VALUES
    (1001, '南山旗舰店', '深圳市南山区科技园示范路1号', 0);

INSERT INTO `card_types`
    (`id`, `name`, `category`, `scope`, `store_id`, `duration_days`, `total_times`, `price`, `description`, `is_active`, `is_deleted`)
VALUES
    (5, '私教10次卡', 'PT', 'ALL_STORE', NULL, 180, 10, 1999.00, '10节一对一私教课，180天有效', 1, 0),
    (6, '私教20次卡', 'PT', 'ALL_STORE', NULL, 365, 20, 3699.00, '20节一对一私教课，365天有效', 1, 0),
    (7, '私教30次卡', 'PT', 'ALL_STORE', NULL, 365, 30, 4999.00, '30节一对一私教课，365天有效', 1, 0);

-- ============================================================
-- 以下内容整合自 V5__share_media.sql
-- ============================================================

-- ============================================================
-- V5：分享模块媒体扩展
-- 1. shares 增加 images（图片URL列表，JSON数组字符串）
-- 2. shares 增加 view_count（浏览数）
-- ============================================================

ALTER TABLE `shares`
    ADD COLUMN `images` TEXT DEFAULT NULL COMMENT '图片URL列表（JSON数组字符串，最多9张）' AFTER `cover_url`;

ALTER TABLE `shares`
    ADD COLUMN `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览数' AFTER `comment_count`;

-- ============================================================
-- 以下内容整合自 V6__operation_coupon_activity.sql
-- ============================================================

-- ============================================
-- V6: 运营能力（优惠券 + 限时活动）
-- 1) coupons        优惠券模板（管理端创建）
-- 2) user_coupons   用户持有的券（领取 → 锁定 → 使用/释放）
-- 3) activities     限时活动（针对卡类型做活动价）
-- 4) card_orders    增加券抵扣与活动价快照字段
-- ============================================

-- ----------------------------
-- 优惠券模板
-- ----------------------------
CREATE TABLE IF NOT EXISTS `coupons` (
    `id`             BIGINT       NOT NULL COMMENT '优惠券ID（雪花算法）',
    `name`           VARCHAR(100) NOT NULL COMMENT '券名称',
    `type`           VARCHAR(32)  NOT NULL DEFAULT 'FULL_REDUCE' COMMENT '类型：FULL_REDUCE-满减，DIRECT-直减',
    `threshold`      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛（订单金额需>=该值；0 表示无门槛）',
    `amount`         DECIMAL(10,2) NOT NULL COMMENT '抵扣面额',
    `total_count`    INT          NOT NULL DEFAULT 0 COMMENT '发行总量（0 表示不限量）',
    `issued_count`   INT          NOT NULL DEFAULT 0 COMMENT '已领取数量',
    `per_user_limit` INT          NOT NULL DEFAULT 1 COMMENT '每人限领张数',
    `valid_type`     VARCHAR(32)  NOT NULL COMMENT '有效期类型：FIXED-固定区间，DAYS_AFTER_RECEIVE-领取后N天',
    `start_time`     DATETIME     DEFAULT NULL COMMENT '固定有效期开始（valid_type=FIXED）',
    `end_time`       DATETIME     DEFAULT NULL COMMENT '固定有效期结束（valid_type=FIXED）',
    `valid_days`     INT          DEFAULT NULL COMMENT '领取后有效天数（valid_type=DAYS_AFTER_RECEIVE）',
    `status`         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1-上架可领，0-下架',
    `is_deleted`     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_coupons_status` (`status`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

-- ----------------------------
-- 用户优惠券
-- 状态机：UNUSED（可用）→ LOCKED（下单占用）→ USED（已核销）
--          LOCKED → UNUSED（订单取消/超时释放）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `user_coupons` (
    `id`         BIGINT   NOT NULL COMMENT '用户券ID（雪花算法）',
    `coupon_id`  BIGINT   NOT NULL COMMENT '券模板ID',
    `user_id`    BIGINT   NOT NULL COMMENT '持有人ID',
    `status`     VARCHAR(32) NOT NULL DEFAULT 'UNUSED' COMMENT '状态：UNUSED-可用，LOCKED-已锁定，USED-已使用，EXPIRED-已过期',
    `order_id`   BIGINT   DEFAULT NULL COMMENT '关联订单ID（锁定/核销时写入）',
    `start_time` DATETIME NOT NULL COMMENT '生效时间',
    `end_time`   DATETIME NOT NULL COMMENT '失效时间',
    `used_at`    DATETIME DEFAULT NULL COMMENT '核销时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_coupons_user` (`user_id`, `status`),
    KEY `idx_user_coupons_coupon` (`coupon_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户持有的优惠券';

-- ----------------------------
-- 限时活动（活动价：对指定卡类型打折）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `activities` (
    `id`          BIGINT       NOT NULL COMMENT '活动ID（雪花算法）',
    `title`       VARCHAR(100) NOT NULL COMMENT '活动标题',
    `subtitle`    VARCHAR(200) DEFAULT NULL COMMENT '活动副标题',
    `cover_url`   VARCHAR(500) DEFAULT NULL COMMENT '活动封面图',
    `content`     TEXT         DEFAULT NULL COMMENT '活动详情',
    `card_type_id` BIGINT      NOT NULL COMMENT '适用卡类型ID',
    `discount`    DECIMAL(4,2) NOT NULL COMMENT '折扣率（0.90 表示 9 折）',
    `quota_total` INT          DEFAULT NULL COMMENT '活动名额（NULL 不限）',
    `quota_used`  INT          NOT NULL DEFAULT 0 COMMENT '已售名额',
    `start_time`  DATETIME     NOT NULL COMMENT '活动开始时间',
    `end_time`    DATETIME     NOT NULL COMMENT '活动结束时间',
    `status`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '状态：1-上架，0-下架',
    `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_activities_time` (`status`, `start_time`, `end_time`),
    KEY `idx_activities_card` (`card_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限时活动';

-- ----------------------------
-- card_orders 扩展：券抵扣 + 活动价快照
-- original_amount 原价，amount 实付金额（券抵扣后）
-- ----------------------------
ALTER TABLE `card_orders`
    ADD COLUMN `original_amount`   DECIMAL(10,2) DEFAULT NULL COMMENT '原价（活动价前）' AFTER `amount`,
    ADD COLUMN `activity_id`       BIGINT        DEFAULT NULL COMMENT '参与的活动ID' AFTER `card_type_id`,
    ADD COLUMN `user_coupon_id`    BIGINT        DEFAULT NULL COMMENT '使用的用户券ID' AFTER `activity_id`,
    ADD COLUMN `discount_amount`   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '券抵扣金额' AFTER `user_coupon_id`,
    ADD KEY `idx_card_orders_coupon` (`user_coupon_id`),
    ADD KEY `idx_card_orders_activity` (`activity_id`);

-- ----------------------------
-- messages 扩展：分人群群发审计
-- 记录该批次使用的筛选条件，便于管理端回溯「这批通知发给了谁」
-- ----------------------------
ALTER TABLE `messages`
    ADD COLUMN `card_type_id` BIGINT DEFAULT NULL COMMENT '分人群群发：卡类型ID' AFTER `scope`,
    ADD COLUMN `store_id`     BIGINT DEFAULT NULL COMMENT '分人群群发：门店ID' AFTER `card_type_id`;

-- ----------------------------
-- 预置：新手满减券（可直接演示领券流程）
-- ----------------------------
INSERT INTO `coupons`
    (`id`, `name`, `type`, `threshold`, `amount`, `total_count`, `issued_count`, `per_user_limit`,
     `valid_type`, `valid_days`, `status`, `is_deleted`)
VALUES
    (2001, '新人满100减20', 'FULL_REDUCE', 100.00, 20.00, 1000, 0, 1, 'DAYS_AFTER_RECEIVE', 30, 1, 0),
    (2002, '满500减50', 'FULL_REDUCE', 500.00, 50.00, 500, 0, 1, 'DAYS_AFTER_RECEIVE', 30, 1, 0),
    (2003, '无门槛直减10元', 'DIRECT', 0.00, 10.00, 200, 0, 1, 'DAYS_AFTER_RECEIVE', 15, 1, 0);

-- ============================================================
-- 以下内容整合自 V7__sms_notify.sql
-- ============================================================

-- ============================================
-- V7: 关键通知短信
-- sms_records：短信发送留痕（无论 log 演示通道还是真实云通道均落库，便于审计与对账）
-- ============================================

CREATE TABLE IF NOT EXISTS `sms_records` (
    `id`            BIGINT       NOT NULL COMMENT '记录ID（雪花算法）',
    `user_id`       BIGINT       DEFAULT NULL COMMENT '接收用户ID（系统级短信可为空）',
    `phone`         VARCHAR(20)  NOT NULL COMMENT '接收手机号（脱敏存储，仅保留前3后4）',
    `template_type` VARCHAR(48)  NOT NULL COMMENT '模板类型：PAY_SUCCESS-支付成功，ENTRY_SUCCESS-核销成功，ACTIVATE_SUCCESS-激活成功',
    `channel`       VARCHAR(16)  NOT NULL DEFAULT 'LOG' COMMENT '发送通道：LOG-日志通道(开发/降级)，ALIYUN-阿里云短信',
    `content`       VARCHAR(500) NOT NULL COMMENT '渲染后的短信内容',
    `status`        VARCHAR(16)  NOT NULL COMMENT '发送状态：SUCCESS-成功，FAILED-失败，SKIPPED-跳过(限频/无手机号/开关关闭)',
    `fail_reason`   VARCHAR(255) DEFAULT NULL COMMENT '失败原因（status=FAILED 时）',
    `biz_id`        VARCHAR(64)  DEFAULT NULL COMMENT '关联业务ID（订单号/核销记录ID/会员卡ID），便于追溯',
    `cost_ms`       INT          NOT NULL DEFAULT 0 COMMENT '发送耗时（毫秒）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_sms_user` (`user_id`, `created_at`),
    KEY `idx_sms_template` (`template_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信发送记录';

-- ============================================================
-- 以下内容整合自 V8__governance.sql
-- ============================================================

-- ============================================
-- V8: 平台治理（投诉 / 仲裁 / 风控 / 管理员审计）
-- ============================================

-- ----------------------------
-- 用户投诉
-- 状态机：PENDING 待受理 → PROCESSING 处理中 → RESOLVED 已解决 → CLOSED 已关闭
--         处理中可升级 ARBITRATING（进入平台仲裁，arbitrations 表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `complaints` (
    `id`               BIGINT       NOT NULL COMMENT '投诉ID（雪花算法）',
    `user_id`          BIGINT       NOT NULL COMMENT '投诉用户ID',
    `type`             VARCHAR(32)  NOT NULL DEFAULT 'OTHER' COMMENT '类型：SERVICE-服务态度，ORDER-订单支付，ENTRY-到店核销，CARD-会员卡，OTHER-其他',
    `title`            VARCHAR(100) NOT NULL COMMENT '标题',
    `content`          VARCHAR(1000) NOT NULL COMMENT '投诉详情',
    `images_json`      VARCHAR(2000) DEFAULT NULL COMMENT '凭证图片URL JSON 数组',
    `biz_type`         VARCHAR(32)  DEFAULT NULL COMMENT '关联业务类型：ORDER/MEMBERSHIP/ENTRY/STORE',
    `biz_id`           VARCHAR(64)  DEFAULT NULL COMMENT '关联业务ID',
    `store_id`         BIGINT       DEFAULT NULL COMMENT '关联门店ID',
    `contact_phone`    VARCHAR(20)  DEFAULT NULL COMMENT '联系电话（脱敏展示）',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/ARBITRATING/RESOLVED/CLOSED',
    `admin_reply`      VARCHAR(1000) DEFAULT NULL COMMENT '平台处理回复',
    `handler_admin_id` BIGINT       DEFAULT NULL COMMENT '受理管理员ID',
    `handled_at`       DATETIME     DEFAULT NULL COMMENT '最近处理时间',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_complaints_user` (`user_id`, `created_at`),
    KEY `idx_complaints_status` (`status`, `created_at`),
    KEY `idx_complaints_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户投诉';

-- ----------------------------
-- 平台仲裁（投诉升级后生成，一次投诉至多一条）
-- 状态机：INVESTIGATING 调查中 → RULING 裁决中 → DONE 已完成
-- ----------------------------
CREATE TABLE IF NOT EXISTS `arbitrations` (
    `id`                   BIGINT       NOT NULL COMMENT '仲裁ID',
    `complaint_id`         BIGINT       NOT NULL COMMENT '来源投诉ID',
    `user_id`              BIGINT       NOT NULL COMMENT '投诉用户ID',
    `reason`               VARCHAR(500) NOT NULL COMMENT '升级仲裁原因',
    `status`               VARCHAR(16)  NOT NULL DEFAULT 'INVESTIGATING' COMMENT 'INVESTIGATING/RULING/DONE',
    `result`               VARCHAR(32)  DEFAULT NULL COMMENT '裁决结果：SUPPORT_USER-支持用户，SUPPORT_PLATFORM-支持平台，PARTIAL-各担其责',
    `decision`             VARCHAR(1000) DEFAULT NULL COMMENT '平台裁决说明',
    `compensation_amount`  DECIMAL(10,2) DEFAULT NULL COMMENT '补偿金额（无补偿为NULL/0）',
    `arbitrator_admin_id`  BIGINT       DEFAULT NULL COMMENT '仲裁管理员ID',
    `handled_at`           DATETIME     DEFAULT NULL COMMENT '裁决完成时间',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_arbitrations_complaint` (`complaint_id`),
    KEY `idx_arbitrations_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台仲裁';

-- ----------------------------
-- 风控事件（异常登录 / 异常核销自动检测产生）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `risk_events` (
    `id`            BIGINT       NOT NULL COMMENT '事件ID',
    `event_type`    VARCHAR(48)  NOT NULL COMMENT 'LOGIN_FAIL_BURST-登录失败爆发，LOGIN_UNUSUAL_TIME-异常时段登录，ENTRY_FREQ-高频核销，ENTRY_NIGHT-夜间核销',
    `risk_level`    VARCHAR(8)   NOT NULL DEFAULT 'LOW' COMMENT 'LOW/MEDIUM/HIGH',
    `subject_type`  VARCHAR(8)   NOT NULL COMMENT 'USER-用户，ADMIN-管理员',
    `subject_id`    BIGINT       DEFAULT NULL COMMENT '主体ID（无法定位时为NULL，如用户名不存在的爆破）',
    `subject_name`  VARCHAR(64)  DEFAULT NULL COMMENT '主体标识（用户名/手机号，脱敏）',
    `detail_json`   VARCHAR(1000) DEFAULT NULL COMMENT '事件详情JSON（次数/窗口/IP等）',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN-待处理，IGNORED-已忽略，HANDLED-已处理',
    `handler_id`    BIGINT       DEFAULT NULL COMMENT '处理管理员ID',
    `handle_remark` VARCHAR(255) DEFAULT NULL COMMENT '处理备注',
    `handled_at`    DATETIME     DEFAULT NULL COMMENT '处理时间',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_risk_status` (`status`, `created_at`),
    KEY `idx_risk_subject` (`subject_type`, `subject_id`),
    KEY `idx_risk_type` (`event_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风控事件';

-- ----------------------------
-- 管理员操作审计日志（拦截器自动记录写操作）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `admin_audit_logs` (
    `id`            BIGINT       NOT NULL COMMENT '日志ID',
    `admin_id`      BIGINT       DEFAULT NULL COMMENT '管理员ID（未登录操作如登录失败为NULL）',
    `username`      VARCHAR(64)  DEFAULT NULL COMMENT '管理员用户名',
    `module`        VARCHAR(32)  NOT NULL COMMENT '业务模块（URI 第一段推导，如 members/coupons）',
    `action`        VARCHAR(16)  NOT NULL COMMENT 'POST/PUT/DELETE/PATCH',
    `method`        VARCHAR(8)   NOT NULL COMMENT 'HTTP 方法',
    `uri`           VARCHAR(255) NOT NULL COMMENT '请求URI（不含query）',
    `param_summary` VARCHAR(500) DEFAULT NULL COMMENT '参数摘要（已脱敏：密码/手机号截断）',
    `ip`            VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
    `result`        VARCHAR(8)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL',
    `cost_ms`       INT          NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_audit_admin` (`admin_id`, `created_at`),
    KEY `idx_audit_module` (`module`, `created_at`),
    KEY `idx_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作审计日志';
