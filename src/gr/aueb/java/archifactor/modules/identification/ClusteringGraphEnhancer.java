package gr.aueb.java.archifactor.modules.identification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import gr.uom.java.ast.ClassObject;

public class ClusteringGraphEnhancer<T> {

    public void enhanceGraph(ClusteringGraph<T> graph, List<CallGraph> callGraphs) {
        applyOwnershipFromCreationRecords(graph, callGraphs);

        Map<T, Integer> totalByEntity = new HashMap<T, Integer>();
        Map<EntityPair<T>, Integer> coOccurrenceByPair = new HashMap<EntityPair<T>, Integer>();
        collectCoOccurrenceStatistics(callGraphs, totalByEntity, coOccurrenceByPair);

        for (T entityA : graph.getVertices()) {
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(entityA)) {
                T entityB = edge.getTarget();
                double score = computeCouplingScore(entityA, entityB, totalByEntity, coOccurrenceByPair);
                if (score <= edge.getWeight()) {
                    continue;
                }
                edge.setWeight(score);
                graph.setEdge(entityB, entityA, edge);
            }
        }
    }

    private void applyOwnershipFromCreationRecords(ClusteringGraph<T> graph, List<CallGraph> callGraphs) {
        HashSet<CreationRecord> creationRecords = getAllCreationRecords(callGraphs);
        for (T parent : graph.getVertices()) {
            for (ClusteringGraph.Edge<T> edge : graph.getNeighbors(parent)) {
                for (CreationRecord record : creationRecords) {
                    if (record.getCreatedBy() != parent || record.getCreated() != edge.getTarget()) {
                        continue;
                    }
                    if (EdgeType.OWNERSHIP.getBaseline() <= edge.getType().getBaseline()) {
                        continue;
                    }
                    edge.setType(EdgeType.OWNERSHIP);
                    edge.setWeight(EdgeType.OWNERSHIP.getBaseline());
                    graph.setEdge(edge.getTarget(), parent, edge);
                }
            }
        }
    }

    private static HashSet<CreationRecord> getAllCreationRecords(List<CallGraph> callGraphs) {
        HashSet<CreationRecord> allCreationRecords = new HashSet<CreationRecord>();
        for (CallGraph callGraph : callGraphs) {
            allCreationRecords.addAll(callGraph.getRoot().creationRecords);
        }
        return allCreationRecords;
    }

    @SuppressWarnings("unchecked")
    private void collectCoOccurrenceStatistics(
		List<CallGraph> callGraphs,
	    Map<T, Integer> totalByEntity,
	    Map<EntityPair<T>, Integer> coOccurrenceByPair
    ) {
        for (CallGraph callGraph : callGraphs) {
            CallGraphNode root = callGraph.getRoot();
            Set<ClassObject> writtenEntitiesSet = new HashSet<ClassObject>();
            writtenEntitiesSet.addAll(root.createdEntitiesObjects);
            writtenEntitiesSet.addAll(root.definedEntitiesObjects);
            writtenEntitiesSet.addAll(root.deletedEntitiesObjects);
            List<ClassObject> writtenEntities = new ArrayList<ClassObject>(writtenEntitiesSet);

            for (ClassObject entity : writtenEntities) {
                Integer currentTotal = totalByEntity.get((T) entity);
                totalByEntity.put((T) entity, currentTotal == null ? 1 : currentTotal + 1);
            }

            for (int i = 0; i < writtenEntities.size(); i++) {
                for (int j = i + 1; j < writtenEntities.size(); j++) {
                    EntityPair<T> pair = new EntityPair<T>((T) writtenEntities.get(i), (T) writtenEntities.get(j));
                    Integer currentCoOcc = coOccurrenceByPair.get(pair);
                    coOccurrenceByPair.put(pair, currentCoOcc == null ? 1 : currentCoOcc + 1);
                }
            }
        }
    }

    private double computeCouplingScore(
		T entityA,
		T entityB,
	    Map<T, Integer> totalByEntity,
	    Map<EntityPair<T>, Integer> coOccurrenceByPair
    ) {
        Integer totalA = totalByEntity.get(entityA);
        Integer totalB = totalByEntity.get(entityB);
        if (totalA == null || totalB == null || totalA == 0 || totalB == 0) {
            return 0.0;
        }
        Integer coOccurrence = coOccurrenceByPair.get(new EntityPair<T>(entityA, entityB));
        if (coOccurrence == null || coOccurrence == 0) {
            return 0.0;
        }
        double ratioA = (double) coOccurrence / (double) totalA;
        double ratioB = (double) coOccurrence / (double) totalB;
        return Math.max(ratioA, ratioB);
    }

    private static final class EntityPair<T> {
        private final T first;
        private final T second;

        EntityPair(T first, T second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EntityPair)) {
                return false;
            }
            EntityPair<?> that = (EntityPair<?>) other;
            return (this.first == that.first && this.second == that.second)
                || (this.first == that.second && this.second == that.first);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(first) + System.identityHashCode(second);
        }
    }
}
