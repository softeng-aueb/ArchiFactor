package gr.aueb.java.ddd.aggregatesIdentification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
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
        ListIterator<ClassObject> classIterator = systemObject.getClassListIterator();
        while (classIterator.hasNext()) {
            ClassObject classObject = classIterator.next();
            if (JpaAnnotationExtractorUtils.hasClassAnnotation(classObject, "Entity")) {
                graph.addVertex(classObject);
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
                if (targetVertex == null
                        || !JpaAnnotationExtractorUtils.hasClassAnnotation(targetVertex, "Entity")
                        || sourceVertex.equals(targetVertex)) {
                    continue;
                }
                EdgeType edgeType = resolveEdgeType(association, targetVertex);
                addOrUpgradeEdge(graph, sourceVertex, targetVertex, edgeType);
            }
        }
    }

    private EdgeType resolveEdgeType(Association association, ClassObject targetVertex) {
        FieldObject field = association.getFieldObject();

        if (JpaAnnotationExtractorUtils.hasFieldAnnotation(field, "MapsId")) {
            return EdgeType.IDENTITY;
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
                addOrUpgradeEdge(graph, entity, ancestor, EdgeType.INHERITANCE);
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

            if (JpaAnnotationExtractorUtils.hasClassAnnotation(superclass, "Entity")) {
                ancestorChain.add(superclass);
            }
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

    private void addOrUpgradeEdge(ClusteringGraph<ClassObject> graph, ClassObject vertexA, ClassObject vertexB, EdgeType newType) {
        if (!graph.hasEdge(vertexA, vertexB)) {
            graph.addEdge(vertexA, vertexB, newType.getBaseline(), newType);
            return;
        }

        ClusteringGraph.Edge<ClassObject> existingEdge = graph.getEdge(vertexA, vertexB);
        if (existingEdge == null || newType.getBaseline() <= existingEdge.getType().getBaseline()) {
            return;
        }

        existingEdge.setType(newType);
        existingEdge.setWeight(newType.getBaseline());
        graph.setEdge(vertexB, vertexA, existingEdge);
    }
}
