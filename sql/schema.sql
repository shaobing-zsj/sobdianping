CREATE TABLE tb_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(32) NOT NULL COMMENT '用户名',
    gender TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '性别：0未知，1男，2女',
    phone VARCHAR(11) NOT NULL COMMENT '手机号',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE tb_artist (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '艺人ID',
    name VARCHAR(100) NOT NULL COMMENT '艺人名称',
    name_pinyin VARCHAR(255) NOT NULL DEFAULT '' COMMENT '艺人名称拼音，用于搜索',
    genre VARCHAR(32) NOT NULL DEFAULT '' COMMENT '音乐流派',
    avatar_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '头像地址',
    cover_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '封面地址',
    introduction TEXT COMMENT '艺人简介',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0停用，1正常',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='艺人表';

CREATE TABLE tb_city (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '城市ID',
    name VARCHAR(64) NOT NULL COMMENT '城市名称',
    province_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '省份/直辖市名称',
    city_code VARCHAR(20) NOT NULL COMMENT '城市编码',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0停用，1正常',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_city_code (city_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='城市表';

CREATE TABLE tb_venue (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '场地ID',
    city_id BIGINT UNSIGNED NOT NULL COMMENT '所属城市ID',
    name VARCHAR(128) NOT NULL COMMENT '场地名称',
    address VARCHAR(255) NOT NULL DEFAULT '' COMMENT '详细地址',
    capacity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '场地最大容纳人数',
    cover_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '场地封面地址',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0停用，1正常',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_city_id_name (city_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出场地表';

CREATE TABLE tb_concert (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '演出ID',
    artist_id BIGINT UNSIGNED NOT NULL COMMENT '艺人ID',
    title VARCHAR(200) NOT NULL COMMENT '演出标题',
    poster_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '演出海报地址',
    description TEXT COMMENT '演出详情',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1已发布，2已下架',
    publish_time DATETIME DEFAULT NULL COMMENT '发布时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_artist_id_status (artist_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐会表';

CREATE TABLE tb_concert_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '场次ID',
    concert_id BIGINT UNSIGNED NOT NULL COMMENT '演出ID',
    venue_id BIGINT UNSIGNED NOT NULL COMMENT '场地ID',
    start_time DATETIME NOT NULL COMMENT '演出开始时间',
    end_time DATETIME NOT NULL COMMENT '演出结束时间',
    entry_start_time DATETIME NOT NULL COMMENT '开始入场时间',
    sale_start_time DATETIME NOT NULL COMMENT '开售时间',
    sale_end_time DATETIME NOT NULL COMMENT '停售时间',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0未开售，1售卖中，2已售罄，3已取消，4已结束',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_concert_id_status_start_time (concert_id, status, start_time),
    KEY idx_venue_id_start_time (venue_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音乐会场次表';

CREATE TABLE tb_session_ticket_tier (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '场次票档ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '场次ID',
    name VARCHAR(64) NOT NULL COMMENT '票档名称',
    description VARCHAR(255) NOT NULL DEFAULT '' COMMENT '票档说明',
    price_cent INT UNSIGNED NOT NULL COMMENT '票面价格，单位分',
    total_stock INT UNSIGNED NOT NULL COMMENT '总库存',
    available_stock INT UNSIGNED NOT NULL COMMENT '可售库存',
    locked_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '待支付锁定库存',
    sold_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已售库存',
    max_per_order TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '单笔订单限购数量',
    sale_start_time DATETIME NOT NULL COMMENT '票档开售时间',
    sale_end_time DATETIME NOT NULL COMMENT '票档停售时间',
    sort_no INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示排序值',
    status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0未开售，1售卖中，2已售罄，3已下架',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_session_id_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场次票档与库存表';

CREATE TABLE tb_ticket_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    order_no VARCHAR(32) NOT NULL COMMENT '订单号',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '购票用户ID',
    contact_name VARCHAR(64) NOT NULL COMMENT '联系人姓名',
    contact_phone VARCHAR(11) NOT NULL COMMENT '联系人手机号',
    order_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0待支付，1已支付，2已关闭，3已取消',
    total_amount_cent INT UNSIGNED NOT NULL COMMENT '订单总金额，单位分',
    pay_amount_cent INT UNSIGNED NOT NULL COMMENT '应付金额，单位分',
    expire_time DATETIME NOT NULL COMMENT '待支付过期时间',
    paid_time DATETIME DEFAULT NULL COMMENT '支付完成时间',
    closed_time DATETIME DEFAULT NULL COMMENT '订单关闭时间',
    cancel_reason VARCHAR(255) DEFAULT NULL COMMENT '取消原因',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id_order_status_create_time (user_id, order_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门票订单表';

CREATE TABLE tb_ticket_order_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单明细ID',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '场次ID',
    ticket_tier_id BIGINT UNSIGNED NOT NULL COMMENT '场次票档ID',
    concert_title VARCHAR(200) NOT NULL COMMENT '演出标题快照',
    artist_name VARCHAR(100) NOT NULL COMMENT '艺人名称快照',
    venue_name VARCHAR(128) NOT NULL COMMENT '场地名称快照',
    session_start_time DATETIME NOT NULL COMMENT '演出开始时间快照',
    ticket_tier_name VARCHAR(64) NOT NULL COMMENT '票档名称快照',
    unit_price_cent INT UNSIGNED NOT NULL COMMENT '单价快照，单位分',
    quantity TINYINT UNSIGNED NOT NULL COMMENT '购买数量',
    subtotal_amount_cent INT UNSIGNED NOT NULL COMMENT '小计金额，单位分',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门票订单明细表';

CREATE TABLE tb_payment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '支付记录ID',
    payment_no VARCHAR(32) NOT NULL COMMENT '支付单号',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    payment_channel VARCHAR(32) NOT NULL COMMENT '支付渠道',
    transaction_no VARCHAR(128) DEFAULT NULL COMMENT '第三方支付流水号',
    amount_cent INT UNSIGNED NOT NULL COMMENT '支付金额，单位分',
    payment_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0发起，1成功，2失败，3关闭',
    initiated_time DATETIME NOT NULL COMMENT '发起支付时间',
    paid_time DATETIME DEFAULT NULL COMMENT '支付成功时间',
    fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order_id_payment_status (order_id, payment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

CREATE TABLE tb_ticket (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '电子票ID',
    ticket_no VARCHAR(32) NOT NULL COMMENT '电子票号',
    order_item_id BIGINT UNSIGNED NOT NULL COMMENT '订单明细ID',
    ticket_code VARCHAR(128) NOT NULL COMMENT '验票码',
    ticket_status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态：0未使用，1已验票，2已作废',
    issued_time DATETIME NOT NULL COMMENT '发放时间',
    used_time DATETIME DEFAULT NULL COMMENT '验票时间',
    void_time DATETIME DEFAULT NULL COMMENT '作废时间',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_no (ticket_no),
    UNIQUE KEY uk_ticket_code (ticket_code),
    KEY idx_order_item_id (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子票表';
