package com.heima.sobdianping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_ticket")
public class Ticket {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private Long orderItemId;
    private String ticketCode;
    private Integer ticketStatus;
    private LocalDateTime issuedTime;
    private LocalDateTime usedTime;
    private LocalDateTime voidTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
