package com.heima.sobdianping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_concert_session")
public class ConcertSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long concertId;
    private Long venueId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime entryStartTime;
    private LocalDateTime saleStartTime;
    private LocalDateTime saleEndTime;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
