package json.tree.entity;

import lombok.Data;

import java.util.Map;

@Data
public class Car {

    private boolean heading;
    private double initSpeed;
    private double maxSpeed;
    private String model;
    private String name;
    private double roadDeviation;
    private String treePath;
    private String locationType;
    private Map<String, String> locationParams;

    // 通过treePath的对应文件获取
    private MTree mTree;
    // 以下变量不能直接获取，需要初始化后通过Map获取
    private int roadId;
    private int laneId;
    private double minOffset;
    private double maxOffset;
    private double minLateralOffset;
    private double maxLateralOffset;
    private double x;
    private double y;

    // 若通过关联车辆定位
    private String actorRef;

    private double offset;
    private int laneSectionId;
    private int laneIndex;
    private int laneSectionIndex;
    private int roadIndex;
    private double width = 1.5;
    private double length = 2.5;

}
