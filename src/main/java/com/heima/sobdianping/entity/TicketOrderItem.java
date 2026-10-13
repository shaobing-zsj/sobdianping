package com.heima.sobdianping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_ticket_order_item")
public class TicketOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long sessionId;
    private Long ticketTierId;
    private String concertTitle;
    private String artistName;
    private String venueName;
    private LocalDateTime sessionStartTime;
    private String ticketTierName;
    private Integer unitPriceCent;
    private Integer quantity;
    private Integer subtotalAmountCent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
