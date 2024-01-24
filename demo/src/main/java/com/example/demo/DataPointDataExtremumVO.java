package com.example.demo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据点数据极值VO
 *
 * @author Karl
 * @since 2022-07-26
 */
@Data
public class DataPointDataExtremumVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 日期
     */
    private LocalDate date;
    /**
     * 最大值发生时间
     */
    private LocalDateTime maxTime;
    /**
     * 最大值
     */
    private Double max;
    /**
     * 最小值发生时间
     */
    private LocalDateTime minTime;
    /**
     * 最小值
     */
    private Double min;
    /**
     * 平均值
     */
    private Double avg;

    // 3
}