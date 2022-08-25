package com.example.demo;

import lombok.Data;

/**
 * @author Karl
 * @since 2022-07-13
 */
@Data
public class DataPointDTO {
    /**
     * 设备数据点ID
     */
    private Long pointId;
    /**
     * 设备数据点编号
     */
    private String pointNo;
    /**
     * 设备数据点名称
     */
    private String pointName;
    /**
     * 单位
     */
    private String unit;
    /**
     * 值
     */
    private Float value;
    /**
     * 接收时间
     */
    private Long receivedAt;
}