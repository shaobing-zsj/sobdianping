package com.heima.sobdianping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_session_ticket_tier")
public class SessionTicketTier {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String name;
    private String description;
    private Integer priceCent;
    private Integer totalStock;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer soldStock;
    private Integer maxPerOrder;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private Integer sortNo;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
