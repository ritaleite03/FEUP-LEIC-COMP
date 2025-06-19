package pt.up.fe.comp2024.optimization;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GraphColoring {
    private final Map<String, Set<String>> graph = new HashMap<>();
    private final Map<String, Integer> graphColors = new HashMap<>();

    public void addNode(String node){
        if(graph.containsKey(node)){
            return;
        }
        graph.put(node,new HashSet<>());
    }

    public void addColor(String node){
        Set<Integer> used = graph.get(node).stream().filter(graphColors::containsKey).map(graphColors::get).collect(Collectors.toSet());
        for(int i=0;;i++){
            if(!used.contains(i)) {
                graphColors.put(node, i);
                return;
            }
        }
    }
    /*
    public void removeNode(String node){
        if(!graph.containsKey(node)){
            return;
        }
        for(var neighbor:graph.get(node)) {
            graph.get(neighbor).remove(node);
        }
        graph.remove(node);
    }
    */

    public void addEdge(String a, String b){
        if(!graph.containsKey(a) || !graph.containsKey(b)){
            return;
        }
        graph.get(a).add(b);
        graph.get(b).add(a);
    }

    public Map<String, Integer> getGraphColors(){
        return graphColors;
    }

    public void printGraphColoring(){
        System.out.println(graph);
    }
}
