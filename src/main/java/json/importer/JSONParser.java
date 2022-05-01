package json.importer;

import com.alibaba.fastjson.JSONObject;
import json.tree.TreeConstant;
import json.tree.TreeDataContainer;
import json.tree.entity.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static util.ADSMLUtil.*;

@Slf4j
public class JSONParser {

    private static List<Behavior> behaviorList;
    private static List<CommonTransition> commonTransitionList;
    private static List<ProbabilityTransition> probabilityTransitionList;
    private static List<BranchPoint> branchPointList;

    // 映射：name -> car
    private static Map<String, Car> nameCarMap;

    // 从locationParams初始化car的道路信息
    private static void initLocationParams(Car car) {
        Map<String, String> locationParams = car.getLocationParams();
        String type = car.getLocationType();

        double x = Double.parseDouble(locationParams.getOrDefault("x", "0.0"));
        double y = Double.parseDouble(locationParams.getOrDefault("y", "0.0"));
        double minOffset = Double.parseDouble(locationParams.getOrDefault("minLongitudinalOffset", "0.0"));
        double maxOffset = Double.parseDouble(locationParams.getOrDefault("maxLongitudinalOffset", "0.0"));
        double minLateralOffset = Double.parseDouble(locationParams.getOrDefault("minLateralOffset", "0.0"));
        double maxLateralOffset = Double.parseDouble(locationParams.getOrDefault("maxLateralOffset", "0.0"));
        int roadId = Integer.parseInt(locationParams.getOrDefault("roadId", "0"));
        int laneId = Integer.parseInt(locationParams.getOrDefault("laneId", "0"));
        String actorRefName = locationParams.getOrDefault("actorRef", "");

        car.setMinOffset(minOffset);
        car.setMaxOffset(maxOffset);
        car.setMinLateralOffset(minLateralOffset);
        car.setMaxLateralOffset(maxLateralOffset);
        car.setRoadId(roadId);
        car.setLaneId(laneId);
        car.setActorRef(actorRefName);

        // TODO: Global Position定位位置信息和索引
//        if (type.equals(TreeConstant.LANE_POSITION)) {
//
//        } else if (type.equals(TreeConstant.ROAD_POSITION)) {
//
//        } else if (type.equals(TreeConstant.RELATED_POSITION)) {
//
//        } else if (type.equals(TreeConstant.GLOBAL_POSITION)) {
//
//        } else {
//            log.error("car的定位类型错误！");
//        }
    }

    private static void initFromRelatedCar(Car car) {
        if(car.getLocationType().equals(TreeConstant.RELATED_POSITION)) {
            log.info("{}的关联车辆为{}", car.getName(), car.getActorRef());
            Car relatedCar = nameCarMap.get(car.getActorRef());
            car.setRoadId(relatedCar.getRoadId());
            car.setLaneId(relatedCar.getLaneId());
            car.setMinOffset(relatedCar.getMinOffset() + car.getMinOffset());
            car.setMaxOffset(relatedCar.getMaxOffset() + car.getMaxOffset());
            car.setMinLateralOffset(relatedCar.getMinLateralOffset() + car.getMinLateralOffset());
            car.setMaxLateralOffset(relatedCar.getMaxLateralOffset() + car.getMaxLateralOffset());
        }
        log.info("{}车的定位方式为：{}, 道路初步信息（需再计算）：road id: {}, lane id: {}, min offset: {}, max offset: {}",
                car.getName(), car.getLocationType(), car.getRoadId(), car.getLaneId(), car.getMinOffset(), car.getMaxOffset());
    }

    // 将所有id去重，并重新分配
    private static void modifyId(Car car) {
        int id = 1;
        int locationId = 1;
        Map<Integer, Integer> ids = new HashMap<>(); // 原id -> 去重后id
        Map<String, Integer> locationIds = new HashMap<>(); // 行为（Location）对应的id

        for(Behavior behavior : car.getMTree().getBehaviors()) {
            if(!locationIds.containsKey(behavior.getName())) {
                locationIds.put(behavior.getName(), locationId);
                ids.put(behavior.getId(), locationId);
                behavior.setId(locationId);
                locationId++;
                id++;
            } else {
                int nowId = locationIds.get(behavior.getName());
                ids.put(behavior.getId(), nowId);
                behavior.setId(nowId);
            }
        }
        
        for(BranchPoint branchPoint : car.getMTree().getBranchPoints()) {
            ids.put(branchPoint.getId(), id);
            branchPoint.setId(id++);
        }

        for(CommonTransition commonTransition : car.getMTree().getCommonTransitions()) {
            ids.put(commonTransition.getId(), id);
            commonTransition.setId(id++);
            commonTransition.setSourceId(ids.get(commonTransition.getSourceId()));
            commonTransition.setTargetId(ids.get(commonTransition.getTargetId()));
        }

        for(ProbabilityTransition probabilityTransition : car.getMTree().getProbabilityTransitions()) {
            ids.put(probabilityTransition.getId(), id);
            probabilityTransition.setId(id++);
            probabilityTransition.setSourceId(ids.get(probabilityTransition.getSourceId()));
            probabilityTransition.setTargetId(ids.get(probabilityTransition.getTargetId()));
        }

    }

    private static void initEdge2(Car car) {
        MTree mTree = car.getMTree();
        List<Behavior> behaviors = mTree.getBehaviors();
        List<CommonTransition> commonTransitions = mTree.getCommonTransitions();
        List<ProbabilityTransition> probabilityTransitions = mTree.getProbabilityTransitions();
        List<BranchPoint> branchPoints = mTree.getBranchPoints();

        behaviorList = new ArrayList<>();
        commonTransitionList = new ArrayList<>();
        probabilityTransitionList = new ArrayList<>();
        branchPointList = new ArrayList<>();

        // 0. init idMap
        // id -> Behavior
        Map<Integer, Behavior> idBehaviorMap = new HashMap<>();
        Map<Integer, BranchPoint> idBranchPointMap = new HashMap<>();
        for(Behavior behavior : behaviors) {
            idBehaviorMap.put(behavior.getId(), behavior);
        }
        for(BranchPoint branchPoint : branchPoints) {
            idBranchPointMap.put(branchPoint.getId(), branchPoint);
        }

        // 1. init next[]
        for(Behavior behavior : behaviors) {
            behavior.setNextBehaviors(new ArrayList<>());
            behavior.setNextTransitions(new ArrayList<>());
            behavior.setNextBranchPoints(new ArrayList<>());
            // 以该behavior id为source的边
            for(CommonTransition commonTransition : commonTransitions) {
                if(behavior.getId() == commonTransition.getSourceId()) {
                    // next commonTransition
                    commonTransition.setSourceBehavior(behavior);
                    if(idBehaviorMap.containsKey(commonTransition.getTargetId())) { // next behavior
                        Behavior behavior1 = idBehaviorMap.get(commonTransition.getTargetId());
                        commonTransition.setTargetBehavior(behavior1);
                        List<Behavior> behaviorList = behavior.getNextBehaviors();
                        behaviorList.add(behavior1);
                        behavior.setNextBehaviors(behaviorList);
                    } else { // next branchPoint
                        BranchPoint branchPoint = idBranchPointMap.get(commonTransition.getTargetId());
                        commonTransition.setTargetBranchPoint(branchPoint);
                        List<BranchPoint> branchPointList = behavior.getNextBranchPoints();
                        branchPointList.add(branchPoint);
                        behavior.setNextBranchPoints(branchPointList);
                    }
                    List<CommonTransition> commonTransitionList = behavior.getNextTransitions();
                    commonTransitionList.add(commonTransition);
                    behavior.setNextTransitions(commonTransitionList);
                }
            }
        }

        for(BranchPoint branchPoint : branchPoints) {
            branchPoint.setNextBehaviors(new ArrayList<>());
            branchPoint.setNextTransitions(new ArrayList<>());
            for(ProbabilityTransition probabilityTransition : probabilityTransitions) {
                if(branchPoint.getId() == probabilityTransition.getSourceId()) {
                    // next probabilityTransition
                    probabilityTransition.setSourceBranchPoint(branchPoint);
                    if(idBehaviorMap.containsKey(probabilityTransition.getTargetId())) {
                        Behavior behavior = idBehaviorMap.get(probabilityTransition.getTargetId());
                        probabilityTransition.setTargetBehavior(behavior);
                        List<Behavior> behaviorList = branchPoint.getNextBehaviors();
                        behaviorList.add(behavior);
                        branchPoint.setNextBehaviors(behaviorList);
                    }
                    List<ProbabilityTransition> probabilityTransitionList = branchPoint.getNextTransitions();
                    probabilityTransitionList.add(probabilityTransition);
                    branchPoint.setNextTransitions(probabilityTransitionList);
                }
            }
        }

        // 2. build
        // root
        for(Behavior behavior : behaviors) {
            if(behavior.getId() == mTree.getRootId()) {
                behavior.setLevel(1);
                behavior.setGroup(1);
                behavior.setNumber(0);
                behaviorList.add(behavior);
                initBehavior(behavior);
                break;
            }
        }

        // 3. 加入到全局变量中
        mTree.setProbabilityTransitions(probabilityTransitionList);
        mTree.setBranchPoints(branchPointList);
        mTree.setCommonTransitions(commonTransitionList);
        mTree.setBehaviors(behaviorList);
    }

    // 递归初始化
    private static void initBehavior(Behavior sourceBehavior) {
        int number = 1;
        // 初始化 以该behavior为source的边
        for(CommonTransition commonTransition : sourceBehavior.getNextTransitions()) {
            // 更改对应边的三元组
            commonTransition.setLevel(sourceBehavior.getLevel());
            commonTransition.setGroup(sourceBehavior.getGroup());
            commonTransition.setNumber(number);
            commonTransitionList.add(commonTransition);
            number ++;
            initCommonTransition(commonTransition);
        }

    }

    private static void initCommonTransition(CommonTransition commonTransition) {
        if(commonTransition.getTargetBehavior() != null) {
            Behavior behavior = commonTransition.getTargetBehavior();
            // 更改对应behavior的三元组
            behavior.setLevel(commonTransition.getLevel() + 1);
            behavior.setGroup((commonTransition.getGroup() - 1) * N + commonTransition.getNumber());
            behavior.setNumber(0);
            behaviorList.add(behavior);
            initBehavior(behavior);
        } else if(commonTransition.getTargetBranchPoint() != null) {
            BranchPoint branchPoint = commonTransition.getTargetBranchPoint();
            // 更改对应branchPoint的三元组
            branchPoint.setLevel(commonTransition.getLevel() + 1);
            branchPoint.setGroup((commonTransition.getGroup() - 1) * N + commonTransition.getNumber());
            branchPoint.setNumber(0);
            branchPointList.add(branchPoint);
            initBranchPoint(branchPoint);
        }
    }

    private static void initBranchPoint(BranchPoint branchPoint) {
        int number = 1;
        for(ProbabilityTransition probabilityTransition : branchPoint.getNextTransitions()) {
            // 更改对应边的三元组
            probabilityTransition.setLevel(branchPoint.getLevel());
            probabilityTransition.setGroup(branchPoint.getGroup());
            probabilityTransition.setNumber(number);
            probabilityTransitionList.add(probabilityTransition);
            number ++;
            initProbabilityTransition(probabilityTransition);
        }
    }

    private static void initProbabilityTransition(ProbabilityTransition probabilityTransition) {
        if(probabilityTransition.getTargetBehavior() != null) {
            // 更改该behavior对应的三元组
            Behavior behavior = probabilityTransition.getTargetBehavior();
            behavior.setLevel(probabilityTransition.getLevel() + 1);
            behavior.setGroup((probabilityTransition.getGroup() - 1) * N + probabilityTransition.getNumber());
            behavior.setNumber(0);
            behaviorList.add(behavior);
            initBehavior(behavior);
        }
    }

    /**
     * @description 初始化各边和各自环对应的三元组
     */
    @Deprecated
    private static void initEdge(Car car) {
        List<Behavior> behaviors = car.getMTree().getBehaviors();
        List<CommonTransition> commonTransitions = car.getMTree().getCommonTransitions();
        List<ProbabilityTransition> probabilityTransitions = car.getMTree().getProbabilityTransitions();
        List<BranchPoint> branchPoints = car.getMTree().getBranchPoints();

        behaviors.get(0).setLevel(1);
        behaviors.get(0).setGroup(1);
        behaviors.get(0).setNumber(0);
        buildTree(behaviors.get(0), behaviors, commonTransitions, probabilityTransitions, branchPoints);

        MTree mTree = car.getMTree();
        mTree.setBehaviors(behaviors);
        mTree.setCommonTransitions(commonTransitions);
        mTree.setBranchPoints(branchPoints);
    }

    // 递归初始化
    @Deprecated
    private static void buildTree(Behavior sourceBehavior, List<Behavior> behaviors, List<CommonTransition> commonTransitions,
                                  List<ProbabilityTransition> probabilityTransitions, List<BranchPoint> branchPoints) {
        int number = 1;
        // 找出以该behavior为source的边
        for(CommonTransition commonTransition : commonTransitions) {
            if(commonTransition.getSourceId() == sourceBehavior.getId()) {
                // 更改对应边的三元组
                commonTransition.setLevel(sourceBehavior.getLevel());
                commonTransition.setGroup(sourceBehavior.getGroup());
                commonTransition.setNumber(number);
                number ++;
                // 找出该边targetId对应的behavior
                for(Behavior behavior : behaviors) {
                    if(behavior.getId() == commonTransition.getTargetId()) {
                        // 更改对应behavior的三元组
                        behavior.setLevel(commonTransition.getLevel() + 1);
                        behavior.setGroup((commonTransition.getGroup() - 1) * N + commonTransition.getNumber());
                        behavior.setNumber(0);
                        buildTree(behavior, behaviors, commonTransitions, probabilityTransitions, branchPoints);
                    }
                }
                // 找出该边targetId对应的branchPoint
                for(BranchPoint branchPoint: branchPoints) {
                    if(branchPoint.getId() == commonTransition.getTargetId()) {
                        // 更改对应branchPoint的三元组
                        branchPoint.setLevel(commonTransition.getLevel() + 1);
                        branchPoint.setGroup((commonTransition.getGroup() - 1) * N + commonTransition.getNumber());
                        branchPoint.setNumber(0);
                        number ++;
                        // 找出以该branchPoint为source的commonTransition
                        int number2 = 1;
                        for(ProbabilityTransition probabilityTransition : probabilityTransitions) {
                            if(probabilityTransition.getSourceId() == branchPoint.getId()) {
                                // 更改对应边的三元组
                                probabilityTransition.setLevel(branchPoint.getLevel());
                                probabilityTransition.setGroup(branchPoint.getGroup());
                                probabilityTransition.setNumber(number2);
                                number2 ++;
                                // 找出该边的targetId对应的behavior
                                int number3 = 1;
                                for(Behavior behavior : behaviors) {
                                    if(behavior.getId() == probabilityTransition.getTargetId()) {
                                        // 更改该behavior对应的三元组
                                        behavior.setLevel(probabilityTransition.getLevel() + 1);
                                        behavior.setGroup((probabilityTransition.getGroup() - 1) * N + probabilityTransition.getNumber());
                                        behavior.setNumber(number3);
                                        number3 ++;
                                        buildTree(behavior, behaviors, commonTransitions, probabilityTransitions, branchPoints);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static TreeDataContainer parse(String input, String treePathPrefix) {
        log.info("开始解析各车辆...");

        nameCarMap = new HashMap<>();
        TreeDataContainer container = JSONObject.parseObject(input, TreeDataContainer.class);
        container.setMap(treePathPrefix + container.getMap());

        List<Car> cars = new ArrayList<>();
        for(Car car : container.getCars()) {
            // 从路径中获取行为树
            String treeStr = JSONInputReader.readFromFile(treePathPrefix + car.getTreePath());
            MTree tree = JSONObject.parseObject(treeStr, MTree.class);
            car.setMTree(tree);
            // 初始化location
            initLocationParams(car);
            // 初始化（level, group, number）
            initEdge2(car);
            // 需要对id进行去重并更改，将同名节点归为同一id，否则会有重名节点
            modifyId(car);
            // 建立名称到车辆的映射
            nameCarMap.put(car.getName(), car);

            cars.add(car);
        }

        // 如果是related，则需要重新定位
        for (Car car : container.getCars()) {
            initFromRelatedCar(car);
        }

        container.setCars(cars);

        log.info("车辆解析完成!");
        return container;
    }

}
