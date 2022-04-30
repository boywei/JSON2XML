package xodr.map;

import lombok.Data;
import xodr.map.entity.*;

import java.util.List;

@Data
public class MapDataContainer {

    private List<Road> roads;
    private List<Junction> junctions;
    private List<LaneSection> laneSections;
    private List<Lane> lanes;
    private List<Connection> connections;
    private List<LaneLink> laneLinks;

    public MapDataContainer(List<Road> roads, List<Junction> junctions, List<LaneSection> laneSections, List<Lane> lanes, List<Connection> connections, List<LaneLink> laneLinks) {
        this.roads = roads;
        this.junctions = junctions;
        this.laneSections = laneSections;
        this.lanes = lanes;
        this.connections = connections;
        this.laneLinks = laneLinks;
    }
}
