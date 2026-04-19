package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gr.aueb.java.archifactor.jpa.util.JpaAnnotationExtractorUtils;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.TypeObject;
import gr.uom.java.ast.association.Association;
import gr.uom.java.ast.association.AssociationDetection;

public class ClusteringGraphBuilder {
    private final List<CallGraph> callGraphs;
    private final SystemObject systemObject;

    public ClusteringGraphBuilder(List<CallGraph> callGraphs, SystemObject systemObject) {
        this.callGraphs = callGraphs;
        this.systemObject = systemObject;
    }

    public ClusteringGraph<ClassObject> buildClusteringGraph() {
        ClusteringGraph<ClassObject> graph = new ClusteringGraph<ClassObject>();
        addVertices(graph);
        addAssociationEdges(graph);
        addInheritanceEdges(graph);
        return graph;
    }

    private void addVertices(ClusteringGraph<ClassObject> graph) {
        for (CallGraph callGraph : callGraphs) {
            for (ClassObject entity : callGraph.getRoot().getAllEntitiesObjects()) {
                graph.addVertex(entity);
            }
        }
    }

    private void addAssociationEdges(ClusteringGraph<ClassObject> graph) {
        AssociationDetection associationsDetector = new AssociationDetection(systemObject);
        Set<ClassObject> sourceVertices = new HashSet<ClassObject>(graph.getVertices());
        for (ClassObject sourceVertex : sourceVertices) {
            List<Association> associations = associationsDetector.getAssociationsOfClass(sourceVertex);
            for (Association association : associations) {
                ClassObject targetVertex = systemObject.getClassObject(association.getTo());
                if (targetVertex == null || sourceVertex.equals(targetVertex) || graph.hasEdge(sourceVertex, targetVertex)) {
                    continue;
                }
                EdgeType edgeType = resolveEdgeType(association, targetVertex);
                graph.addEdge(sourceVertex, targetVertex, edgeType.getBaseline(), edgeType);
            }
        }
    }

    private EdgeType resolveEdgeType(Association association, ClassObject targetVertex) {
        FieldObject field = association.getFieldObject();

        if (JpaAnnotationExtractorUtils.hasClassAnnotation(targetVertex, "Embeddable")
        		|| JpaAnnotationExtractorUtils.hasFieldAnnotation(field, "Enumerated")
                || JpaAnnotationExtractorUtils.hasFieldAnnotation(field, "Type")
                || JpaAnnotationExtractorUtils.hasFieldAnnotation(field, "ElementCollection")
                || JpaAnnotationExtractorUtils.hasFieldAnnotation(field, "MapsId")) {
            return EdgeType.OWNERSHIP;
        }

        if (JpaAnnotationExtractorUtils.hasOwnershipCascade(field)) {
            return EdgeType.OWNERSHIP;
        }

        if (JpaAnnotationExtractorUtils.hasOrphanRemoval(field)) {
            return EdgeType.OWNERSHIP;
        }

        return EdgeType.REFERENCE;
    }

    private void addInheritanceEdges(ClusteringGraph<ClassObject> graph) {
        Set<ClassObject> initialVertices = new HashSet<ClassObject>(graph.getVertices());
        for (ClassObject entity : initialVertices) {
            List<ClassObject> ancestors = collectAncestorsInInheritanceHierarchy(entity);
            for (ClassObject ancestor : ancestors) {
                if (!initialVertices.contains(ancestor)) {
                    graph.addVertex(ancestor);
                }
                promoteOrAddInheritanceEdge(graph, entity, ancestor);
            }
        }
    }

    private List<ClassObject> collectAncestorsInInheritanceHierarchy(ClassObject entity) {
        List<ClassObject> ancestorChain = new ArrayList<ClassObject>();
        boolean hierarchyHasInheritanceAnnotation = JpaAnnotationExtractorUtils.hasClassAnnotation(entity, "Inheritance");

        ClassObject current = entity;
        while (true) {
            TypeObject superclassType = current.getSuperclass();
            if (superclassType == null) {
                break;
            }

            ClassObject superclass = systemObject.getClassObject(superclassType.getClassType());
            if (superclass == null) {
                break;
            }

            ancestorChain.add(superclass);
            if (JpaAnnotationExtractorUtils.hasClassAnnotation(superclass, "Inheritance")) {
                hierarchyHasInheritanceAnnotation = true;
            }
            current = superclass;
        }

        if (!hierarchyHasInheritanceAnnotation) {
            return new ArrayList<ClassObject>();
        }

        return ancestorChain;
    }

    private void promoteOrAddInheritanceEdge(ClusteringGraph<ClassObject> graph, ClassObject vertexA, ClassObject vertexB) {
        double inheritanceWeight = EdgeType.INHERITANCE.getBaseline();
        if (!graph.hasEdge(vertexA, vertexB)) {
            graph.addEdge(vertexA, vertexB, inheritanceWeight, EdgeType.INHERITANCE);
            return;
        }

        ClusteringGraph.Edge<ClassObject> existingEdge = graph.getEdge(vertexA, vertexB);
        if (existingEdge == null || existingEdge.getType() == EdgeType.INHERITANCE) {
            return;
        }

        existingEdge.setType(EdgeType.INHERITANCE);
        existingEdge.setWeight(inheritanceWeight);
        graph.setEdge(vertexB, vertexA, existingEdge);
    }
}
