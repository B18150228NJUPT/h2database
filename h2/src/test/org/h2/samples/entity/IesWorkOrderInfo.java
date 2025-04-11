package org.h2.samples.entity;


import java.io.Serializable;
import java.util.Date;

/**
 * @ClassName IesWorkOrderInfo
 * @Description IES工单同步信息
 * @Date 2024-05-29 11:33:44
 * @Author kai.song@youhualin.com
 * @Copyright 悠桦林信息科技（上海）有限公司
 * @Version 1.0
 */

public class IesWorkOrderInfo implements Serializable {

    private static final long serialVersionUID = -93480874308145482L;

    /**
     * 消息事件戳
     */
    @ApiModelProperty(value = "消息事件戳")
    private String transactionId;

    /**
     * 操作类型
     */
    @ApiModelProperty(value = "操作类型")
    private String operationType;

    /**
     * 工单单号
     */
    @ApiModelProperty(value = "工单单号")
    private String productRequestName;

    /**
     * 产品编号
     */
    @ApiModelProperty(value = "产品编号")
    private String productSpecName;

    /**
     * 工单类型
     */
    @ApiModelProperty(value = "工单类型")
    private String productRequestType;

    /**
     * 产品编码描述
     */
    @ApiModelProperty(value = "产品编码描述")
    private String productDescription;

    /**
     * 生产模式
     */
    @ApiModelProperty(value = "生产模式")
    private String productionMode;

    /**
     * 补片的源工单
     */
    @ApiModelProperty(value = "补片的源工单")
    private String sourceProductRequestName;


    /**
     * 计划开工日期（首工序开始)
     */
    @ApiModelProperty(value = "计划开工日期（首工序开始)")
    private String planReleasedTime;

    /**
     * 计划完工日期（末工序结束）
     */
    @ApiModelProperty(value = "计划完工日期（末工序结束）")
    private String planEndTime;

    /**
     * 客户单号
     */
    @ApiModelProperty(value = "客户单号")
    private String orderNo;

    /**
     * 任务单号
     */
    @ApiModelProperty(value = "任务单号")
    private String taskNo;

    /**
     * 箱号
     */
    @ApiModelProperty(value = "箱号")
    private String boxNo;

    /**
     * 计划数量
     */
    @ApiModelProperty(value = "计划数量")
    private Integer planQuantity;

    /**
     * 层标
     */
    @ApiModelProperty(value = "层标")
    private String slotNo;

    @ApiModelProperty(value = "镀膜批次")
    private String coatingBatch;

    /**
     * 厚度
     */
    @ApiModelProperty(value = "厚度")
    private String thickness;

    /**
     * 优化包编码
     */
    @ApiModelProperty(value = "优化包编码")
    private String optimizationNo;

    /**
     * 优化包路径
     */
    @ApiModelProperty(value = "优化包路径")
    private String optimizationPath;

    /**
     * 合箱虚拟LOT
     */
    @ApiModelProperty(value = "合箱虚拟LOT")
    private String mergeBoxLot;

    /**
     * 包装类型
     */
    @ApiModelProperty(value = "包装类型")
    private String packingType;

    /**
     * 合同头d
     */
    @ApiModelProperty(value = "合同头d")
    private String orderHeaderId;

    /**
     * 计划箱号ID
     */
    @ApiModelProperty(value = "计划箱号ID")
    private String assembleBoxId;

    /**
     * 需求id
     */
    @ApiModelProperty(value = "需求id")
    private String needId;

    /**
     * 工程名称
     */
    @ApiModelProperty(value = "工程名称")
    private String projectName;

    /**
     * 客户名称
     */
    @ApiModelProperty(value = "客户名称")
    private String customerName;

    /**
     * 订单类型
     */
    @ApiModelProperty(value = "订单类型")
    private String orderType;

    /**
     * 客订批次
     */
    @ApiModelProperty(value = "客订批次")
    private String orderLot;

    /**
     * 规格信息
     */
    @ApiModelProperty(value = "规格信息")
    private String specInfo;

    /**
     * 工序信息
     */
    @ApiModelProperty(value = "工序信息")
    private String processFlowInfo;

    /**
     * 物料信息
     */
    @ApiModelProperty(value = "物料信息")
    private String productRequestBom;

    /**
     * 补片信息
     */
    @ApiModelProperty(value = "补片信息")
    private String lot;

    /**
     * 同步结果
     */
    @ApiModelProperty(value = "同步结果")
    private String syncResult;

    /**
     * 异常信息
     */
    @ApiModelProperty(value = "异常信息")
    private String exceptionInfo;

    /**
     * 重传次数
     */
    @ApiModelProperty(value = "重传次数")
    private Integer syncCount;

    /**
     * 重传次数
     */
    @ApiModelProperty(value = "重传时间")
    private Date syncTime;


    //非数据库字段
    private String customerOrderId;

    /**
     * 子工单中的再用工单（多个用,隔开）
     */
    private String zyWorkOrderIds;

    /**
     * 工单状态
     */
    @ApiModelProperty(value = "工单状态")
    private String workOrderStatus;

    @ApiModelProperty(value = "补片数量")
    private int patchQuantity;

    public IesWorkOrderInfo() {
        // 所有字段模拟随机值 加上随机值
        this.transactionId = "transactionId" + System.currentTimeMillis();
        this.operationType = "operationType" + System.currentTimeMillis();
        this.productRequestName = "productRequestName" + System.currentTimeMillis();
        this.productSpecName = "productSpecName" + System.currentTimeMillis();
        this.productRequestType = "productRequestType" + System.currentTimeMillis();
        this.productDescription = "productDescription" + System.currentTimeMillis();
        this.productionMode = "productionMode" + System.currentTimeMillis();
        this.sourceProductRequestName = "sourceProductRequestName" + System.currentTimeMillis();
        this.planReleasedTime = "planReleasedTime" + System.currentTimeMillis();
        this.planEndTime = "planEndTime" + System.currentTimeMillis();
        this.orderNo = "orderNo";
        this.taskNo = "taskNo";
        this.boxNo = "boxNo";
        this.planQuantity = 1;
        this.slotNo = "slotNo";
        this.coatingBatch = "coatingBatch";
        this.thickness = "thickness";
        this.optimizationNo = "optimizationNo";
        this.optimizationPath = "optimizationPath";
        this.mergeBoxLot = "mergeBoxLot";
        this.packingType = "packingType";
        this.orderHeaderId = "orderHeaderId";
        this.assembleBoxId = "assembleBoxId";
        this.needId = "needId";
        this.projectName = "projectName";
        this.customerName = "customerName";
        this.orderType = "orderType";
        this.orderLot = "orderLot";
        this.specInfo = "specInfo";
        this.processFlowInfo = "processFlowInfo";
        this.productRequestBom = "productRequestBom";
        this.lot = "lot";
        this.syncResult = "syncResult";
        this.exceptionInfo = "exceptionInfo";
        this.syncCount = 1;
        this.syncTime = new Date();
        this.customerOrderId = "customerOrderId";
        this.zyWorkOrderIds = "zyWorkOrderIds";
        this.workOrderStatus = "workOrderStatus";
        this.patchQuantity = 1;
    }
}
